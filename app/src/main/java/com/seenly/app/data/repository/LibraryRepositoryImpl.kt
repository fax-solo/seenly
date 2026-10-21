package com.seenly.app.data.repository

import androidx.room.withTransaction
import com.seenly.app.core.common.AppClock
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.core.time.IsoDates.toIso
import com.seenly.app.core.time.IsoDates.toIsoOrNull
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.entity.SeasonEntity
import com.seenly.app.data.local.mapper.EntityMappers
import com.seenly.app.data.local.mapper.toDomain
import com.seenly.app.domain.model.Episode
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.ManualMediaInput
import com.seenly.app.domain.model.Media
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.Progress
import com.seenly.app.domain.model.Rating
import com.seenly.app.domain.model.Season
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LibraryRepositoryImpl(
    private val db: SeenlyDatabase,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
) : LibraryRepository {

    private val mediaDao = db.mediaDao()
    private val seasonDao = db.seasonDao()
    private val episodeDao = db.episodeDao()
    private val progressDao = db.progressDao()

    override fun observeLibrary(): Flow<List<LibraryEntry>> =
        combine(
            mediaDao.observeLibraryRows(),
            mediaDao.observeMediaGenres(),
        ) { rows, genreRows ->
            val genres = genreRows.groupBy({ it.mediaId }, { it.name })
            rows.map { row -> row.toDomain(genres[row.media.id].orEmpty()) }
        }.flowOn(dispatchers.io)

    override fun observeGenres(): Flow<List<String>> =
        mediaDao.observeAllGenreNames().flowOn(dispatchers.io)

    override fun observeMedia(mediaId: Long): Flow<Media?> =
        combine(
            mediaDao.observeById(mediaId),
            mediaDao.observeMediaGenres(),
        ) { entity, genreRows ->
            entity?.toDomain(genreRows.filter { it.mediaId == mediaId }.map { it.name })
        }.flowOn(dispatchers.io)

    override fun observeSeasons(mediaId: Long): Flow<List<Season>> =
        seasonDao.observeForMedia(mediaId)
            .map { seasons -> seasons.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observeEpisodes(mediaId: Long): Flow<List<Episode>> =
        episodeDao.observeForMedia(mediaId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override suspend fun getMedia(mediaId: Long): Media? = withContext(dispatchers.io) {
        val entity = mediaDao.getById(mediaId) ?: return@withContext null
        entity.toDomain(mediaDao.genresForMedia(mediaId))
    }

    override suspend fun addManual(input: ManualMediaInput): Outcome<Long> =
        withContext(dispatchers.io) {
            val title = input.title.trim()
            if (title.isEmpty()) {
                return@withContext Outcome.Failure(AppError.Invalid("empty_title"))
            }
            if (!input.type.hasEpisodes && input.totalEpisodes != null && input.totalEpisodes < 0) {
                return@withContext Outcome.Failure(AppError.Invalid("invalid_episode_count"))
            }
            if ((input.totalEpisodes ?: 0) < 0 || (input.totalSeasons ?: 0) < 0) {
                return@withContext Outcome.Failure(AppError.Invalid("invalid_episode_count"))
            }
            if ((input.totalEpisodes ?: 0) > 20_000 || (input.totalSeasons ?: 0) > 500) {
                return@withContext Outcome.Failure(AppError.Invalid("invalid_episode_count"))
            }
            db.withTransaction {
                val existing = mediaDao.findByTitleAndType(title, input.type.name)
                if (existing != null) {
                    return@withTransaction Outcome.Failure(AppError.Invalid("duplicate_title"))
                }
                val now = clock.nowMillis()
                val entity = EntityMappers.mediaEntity(input.copy(title = title), now)
                val id = mediaDao.insert(entity)
                ProviderImport.applyGenres(db, id, input.genres)
                createManualEpisodeStructure(id, input)
                progressDao.upsert(
                    ProgressEntity(
                        mediaId = id,
                        status = WatchStatus.PLAN_TO_WATCH.name,
                        updatedAt = now,
                    ),
                )
                Outcome.Success(id)
            }
        }

    /**
     * Manual series get real season/episode rows so progress can be tracked. When
     * only a total is supplied, episodes are distributed evenly across the stated
     * seasons; titles stay null rather than being invented.
     */
    private suspend fun createManualEpisodeStructure(mediaId: Long, input: ManualMediaInput) {
        if (!input.type.hasEpisodes) return
        val totalEpisodes = input.totalEpisodes ?: return
        if (totalEpisodes <= 0) return
        val seasonCount = (input.totalSeasons ?: 1).coerceAtLeast(1)
        val base = totalEpisodes / seasonCount
        val remainder = totalEpisodes % seasonCount
        var absoluteNumber = 1
        repeat(seasonCount) { index ->
            val count = base + if (index < remainder) 1 else 0
            val seasonId = seasonDao.insert(
                SeasonEntity(
                    mediaId = mediaId,
                    number = index + 1,
                    episodeCount = count,
                    sortOrder = index,
                ),
            )
            if (count > 0) {
                val episodes = (1..count).map { number ->
                    EpisodeEntity(
                        mediaId = mediaId,
                        seasonId = seasonId,
                        number = number,
                        absoluteNumber = absoluteNumber++,
                        runtimeMinutes = input.runtimeMinutes,
                    )
                }
                episodeDao.insertAll(episodes)
            }
        }
    }

    override suspend fun updatePersonalInfo(
        mediaId: Long,
        rating: Double?,
        review: String?,
        isSpoiler: Boolean,
        notes: String?,
    ): Outcome<Unit> = withContext(dispatchers.io) {
        if (!Rating.isValidOrNull(rating)) {
            return@withContext Outcome.Failure(AppError.Invalid("invalid_rating"))
        }
        val entity = mediaDao.getById(mediaId)
            ?: return@withContext Outcome.Failure(AppError.Invalid("title_not_found"))
        val now = clock.nowMillis()
        val cleanReview = review?.trim()?.takeIf { it.isNotEmpty() }
        val hasReview = cleanReview != null
        mediaDao.update(
            entity.copy(
                personalRating = rating,
                review = cleanReview,
                reviewIsSpoiler = isSpoiler && hasReview,
                reviewCreatedAt = if (hasReview) entity.reviewCreatedAt ?: now else null,
                reviewUpdatedAt = if (hasReview) now else null,
                personalNotes = notes?.trim()?.takeIf { it.isNotEmpty() },
                lastModifiedAt = now,
            ),
        )
        Outcome.Success(Unit)
    }

    override suspend fun updateLocalInfo(
        mediaId: Long,
        title: String,
        type: MediaType,
        totalSeasons: Int?,
        totalEpisodes: Int?,
        runtimeMinutes: Int?,
    ): Outcome<Unit> = withContext(dispatchers.io) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return@withContext Outcome.Failure(AppError.Invalid("empty_title"))
        }
        if ((totalSeasons ?: 0) < 0 || (totalEpisodes ?: 0) < 0 || (runtimeMinutes ?: 0) < 0) {
            return@withContext Outcome.Failure(AppError.Invalid("invalid_value"))
        }
        val entity = mediaDao.getById(mediaId)
            ?: return@withContext Outcome.Failure(AppError.Invalid("title_not_found"))
        val now = clock.nowMillis()
        mediaDao.update(
            entity.copy(
                title = cleanTitle,
                type = type.name,
                totalSeasons = totalSeasons,
                totalEpisodes = totalEpisodes,
                runtimeMinutes = runtimeMinutes,
                locallyEdited = true,
                lastModifiedAt = now,
            ),
        )
        Outcome.Success(Unit)
    }

    override suspend fun deleteMedia(mediaId: Long) = withContext(dispatchers.io) {
        db.withTransaction { mediaDao.deleteById(mediaId) }
    }
}
