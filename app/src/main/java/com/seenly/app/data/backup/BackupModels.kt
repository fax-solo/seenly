package com.seenly.app.data.backup

import com.seenly.app.core.time.IsoDates.toLocalDateOrNull
import com.seenly.app.domain.model.Rating
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.GoalEntity
import com.seenly.app.data.local.entity.GenreEntity
import com.seenly.app.data.local.entity.ListItemEntity
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.MediaGenreCrossRef
import com.seenly.app.data.local.entity.MediaListEntity
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.entity.ProviderIdEntity
import com.seenly.app.data.local.entity.SeasonEntity
import com.seenly.app.data.local.entity.WatchEventEntity
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Versioned, self-contained backup format.
 *
 * [schemaVersion] describes this file format (not the Room version). Import
 * validates the entire file before writing anything, and older app versions
 * refuse newer files instead of silently dropping fields.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int = CURRENT_BACKUP_VERSION,
    val appVersion: String = "",
    val exportedAt: Long = 0,
    val media: List<BackupMedia> = emptyList(),
    val genres: List<BackupGenre> = emptyList(),
    val mediaGenres: List<BackupMediaGenre> = emptyList(),
    val providerIds: List<BackupProviderId> = emptyList(),
    val seasons: List<BackupSeason> = emptyList(),
    val episodes: List<BackupEpisode> = emptyList(),
    val progress: List<BackupProgress> = emptyList(),
    val events: List<BackupEvent> = emptyList(),
    val lists: List<BackupList> = emptyList(),
    val listItems: List<BackupListItem> = emptyList(),
    val goals: List<BackupGoal> = emptyList(),
) {
    companion object {
        const val CURRENT_BACKUP_VERSION = 2
    }
}

@Serializable
data class BackupMedia(
    val id: Long,
    val type: String,
    val title: String,
    val originalTitle: String? = null,
    val alternateTitles: List<String> = emptyList(),
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: String? = null,
    val endDate: String? = null,
    val runtimeMinutes: Int? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null,
    val studio: String? = null,
    val tags: List<String> = emptyList(),
    val directorNames: List<String> = emptyList(),
    val castNames: List<String> = emptyList(),
    val providerScore: Double? = null,
    val imdbId: String? = null,
    val imdbRating: Double? = null,
    val rottenTomatoesRating: Int? = null,
    val malRating: Double? = null,
    val ratingsUpdatedAt: Long? = null,
    val metadataSource: String? = null,
    val metadataUpdatedAt: Long? = null,
    val locallyEdited: Boolean = false,
    val addedAt: Long = 0,
    val lastModifiedAt: Long = 0,
    val personalRating: Double? = null,
    val review: String? = null,
    val reviewIsSpoiler: Boolean = false,
    val reviewCreatedAt: Long? = null,
    val reviewUpdatedAt: Long? = null,
    val personalNotes: String? = null,
)

@Serializable
data class BackupGenre(val id: Long, val name: String)

@Serializable
data class BackupMediaGenre(val mediaId: Long, val genreId: Long)

@Serializable
data class BackupProviderId(
    val id: Long = 0,
    val mediaId: Long,
    val provider: String,
    val externalId: String,
    val linkedAt: Long = 0,
)

@Serializable
data class BackupSeason(
    val id: Long,
    val mediaId: Long,
    val number: Int? = null,
    val title: String? = null,
    val synopsis: String? = null,
    val episodeCount: Int? = null,
    val airDate: String? = null,
    val endDate: String? = null,
    val posterUrl: String? = null,
    val metadataSource: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class BackupEpisode(
    val id: Long,
    val mediaId: Long,
    val seasonId: Long,
    val number: Int? = null,
    val absoluteNumber: Int? = null,
    val title: String? = null,
    val synopsis: String? = null,
    val runtimeMinutes: Int? = null,
    val airDate: String? = null,
    val stillUrl: String? = null,
    val metadataSource: String? = null,
)

@Serializable
data class BackupProgress(
    val mediaId: Long,
    val status: String,
    val startDate: String? = null,
    val completionDate: String? = null,
    val pausedAt: Long? = null,
    val notes: String? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupEvent(
    val id: Long = 0,
    val mediaId: Long,
    val episodeId: Long? = null,
    val eventType: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val localDate: String,
    val notes: String? = null,
)

@Serializable
data class BackupList(
    val id: Long,
    val name: String,
    val description: String? = null,
    val createdAt: Long = 0,
    val sortOrder: Int = 0,
    val isBuiltin: Boolean = false,
    val smartFilter: String? = null,
    val coverUrl: String? = null,
)

@Serializable
data class BackupListItem(
    val listId: Long,
    val mediaId: Long,
    val position: Int = 0,
    val addedAt: Long = 0,
)

@Serializable
data class BackupGoal(
    val id: Long = 0,
    val type: String,
    val metric: String,
    val target: Int,
    val active: Boolean = true,
    val createdAt: Long = 0,
)

// --- Entity mapping ---

internal fun MediaEntity.toBackup(): BackupMedia = BackupMedia(
    id = id,
    type = type,
    title = title,
    originalTitle = originalTitle,
    alternateTitles = alternateTitles,
    synopsis = synopsis,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    releaseDate = releaseDate,
    endDate = endDate,
    runtimeMinutes = runtimeMinutes,
    totalSeasons = totalSeasons,
    totalEpisodes = totalEpisodes,
    studio = studio,
    tags = tags,
    directorNames = directorNames,
    castNames = castNames,
    providerScore = providerScore,
    imdbId = imdbId,
    imdbRating = imdbRating,
    rottenTomatoesRating = rottenTomatoesRating,
    malRating = malRating,
    ratingsUpdatedAt = ratingsUpdatedAt,
    metadataSource = metadataSource,
    metadataUpdatedAt = metadataUpdatedAt,
    locallyEdited = locallyEdited,
    addedAt = addedAt,
    lastModifiedAt = lastModifiedAt,
    personalRating = personalRating,
    review = review,
    reviewIsSpoiler = reviewIsSpoiler,
    reviewCreatedAt = reviewCreatedAt,
    reviewUpdatedAt = reviewUpdatedAt,
    personalNotes = personalNotes,
)

internal fun BackupMedia.toEntity(now: Long): MediaEntity = MediaEntity(
    id = 0,
    type = type,
    title = title,
    originalTitle = originalTitle,
    alternateTitles = alternateTitles,
    synopsis = synopsis,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    releaseDate = releaseDate,
    endDate = endDate,
    runtimeMinutes = runtimeMinutes,
    totalSeasons = totalSeasons,
    totalEpisodes = totalEpisodes,
    studio = studio,
    tags = tags,
    directorNames = directorNames,
    castNames = castNames,
    providerScore = providerScore,
    imdbId = imdbId,
    imdbRating = imdbRating,
    rottenTomatoesRating = rottenTomatoesRating,
    malRating = malRating,
    ratingsUpdatedAt = ratingsUpdatedAt,
    metadataSource = metadataSource,
    metadataUpdatedAt = metadataUpdatedAt,
    locallyEdited = locallyEdited,
    addedAt = addedAt.takeIf { it > 0 } ?: now,
    lastModifiedAt = lastModifiedAt.takeIf { it > 0 } ?: now,
    personalRating = personalRating,
    review = review,
    reviewIsSpoiler = reviewIsSpoiler,
    reviewCreatedAt = reviewCreatedAt,
    reviewUpdatedAt = reviewUpdatedAt,
    personalNotes = personalNotes,
)

internal fun BackupMedia.toEntityWithId(): MediaEntity = toEntity(0).copy(id = id)

internal fun GenreEntity.toBackup(): BackupGenre = BackupGenre(id = id, name = name)

internal fun MediaGenreCrossRef.toBackup(): BackupMediaGenre =
    BackupMediaGenre(mediaId = mediaId, genreId = genreId)

internal fun ProviderIdEntity.toBackup(): BackupProviderId = BackupProviderId(
    id = id,
    mediaId = mediaId,
    provider = provider,
    externalId = externalId,
    linkedAt = linkedAt,
)

internal fun SeasonEntity.toBackup(): BackupSeason = BackupSeason(
    id = id,
    mediaId = mediaId,
    number = number,
    title = title,
    synopsis = synopsis,
    episodeCount = episodeCount,
    airDate = airDate,
    endDate = endDate,
    posterUrl = posterUrl,
    metadataSource = metadataSource,
    sortOrder = sortOrder,
)

internal fun BackupSeason.toEntity(mediaId: Long): SeasonEntity = SeasonEntity(
    id = 0,
    mediaId = mediaId,
    number = number,
    title = title,
    synopsis = synopsis,
    episodeCount = episodeCount,
    airDate = airDate,
    endDate = endDate,
    posterUrl = posterUrl,
    metadataSource = metadataSource,
    sortOrder = sortOrder,
)

internal fun EpisodeEntity.toBackup(): BackupEpisode = BackupEpisode(
    id = id,
    mediaId = mediaId,
    seasonId = seasonId,
    number = number,
    absoluteNumber = absoluteNumber,
    title = title,
    synopsis = synopsis,
    runtimeMinutes = runtimeMinutes,
    airDate = airDate,
    stillUrl = stillUrl,
    metadataSource = metadataSource,
)

internal fun BackupEpisode.toEntity(mediaId: Long, seasonId: Long): EpisodeEntity = EpisodeEntity(
    id = 0,
    mediaId = mediaId,
    seasonId = seasonId,
    number = number,
    absoluteNumber = absoluteNumber,
    title = title,
    synopsis = synopsis,
    runtimeMinutes = runtimeMinutes,
    airDate = airDate,
    stillUrl = stillUrl,
    metadataSource = metadataSource,
)

internal fun ProgressEntity.toBackup(): BackupProgress = BackupProgress(
    mediaId = mediaId,
    status = status,
    startDate = startDate,
    completionDate = completionDate,
    pausedAt = pausedAt,
    notes = notes,
    updatedAt = updatedAt,
)

internal fun BackupProgress.toEntity(mediaId: Long): ProgressEntity = ProgressEntity(
    mediaId = mediaId,
    status = status,
    startDate = startDate,
    completionDate = completionDate,
    pausedAt = pausedAt,
    notes = notes,
    updatedAt = updatedAt,
)

internal fun WatchEventEntity.toBackup(): BackupEvent = BackupEvent(
    id = id,
    mediaId = mediaId,
    episodeId = episodeId,
    eventType = eventType,
    startedAt = startedAt,
    completedAt = completedAt,
    localDate = localDate,
    notes = notes,
)

internal fun BackupEvent.toEntity(mediaId: Long, episodeId: Long?): WatchEventEntity = WatchEventEntity(
    id = 0,
    mediaId = mediaId,
    episodeId = episodeId,
    eventType = eventType,
    startedAt = startedAt,
    completedAt = completedAt,
    localDate = localDate,
    notes = notes,
)

internal fun MediaListEntity.toBackup(): BackupList = BackupList(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    sortOrder = sortOrder,
    isBuiltin = isBuiltin,
    smartFilter = smartFilter,
    coverUrl = coverUrl,
)

internal fun BackupList.toEntity(): MediaListEntity = MediaListEntity(
    id = 0,
    name = name,
    description = description,
    createdAt = createdAt,
    sortOrder = sortOrder,
    isBuiltin = isBuiltin,
    smartFilter = smartFilter,
    coverUrl = coverUrl,
)

internal fun ListItemEntity.toBackup(): BackupListItem = BackupListItem(
    listId = listId,
    mediaId = mediaId,
    position = position,
    addedAt = addedAt,
)

internal fun GoalEntity.toBackup(): BackupGoal = BackupGoal(
    id = id,
    type = type,
    metric = metric,
    target = target,
    active = active,
    createdAt = createdAt,
)

internal fun BackupGoal.toEntity(): GoalEntity = GoalEntity(
    id = 0,
    type = type,
    metric = metric,
    target = target,
    active = active,
    createdAt = createdAt,
)

/** Returns a machine-readable error code, or null when the backup is safe to import. */
internal object BackupValidator {

    private const val MAX_MEDIA = 100_000
    private const val MAX_EPISODES = 1_000_000
    private const val MAX_EVENTS = 2_000_000
    private const val MAX_LISTS = 20_000
    private const val MAX_GOALS = 10_000

    fun validate(file: BackupFile): String? {
        if (file.schemaVersion !in 1..BackupFile.CURRENT_BACKUP_VERSION) {
            return "unsupported_backup_version"
        }
        if (file.media.size > MAX_MEDIA ||
            file.episodes.size > MAX_EPISODES ||
            file.events.size > MAX_EVENTS ||
            file.lists.size > MAX_LISTS ||
            file.goals.size > MAX_GOALS
        ) {
            return "backup_too_large"
        }

        val mediaIds = file.media.map { it.id }
        if (mediaIds.any { it <= 0 } || mediaIds.size != mediaIds.toSet().size) {
            return "invalid_backup"
        }
        val mediaById = file.media.associateBy { it.id }
        if (file.media.any { it.title.isBlank() }) return "invalid_backup"
        if (file.media.any { com.seenly.app.domain.model.MediaType.fromStorage(it.type) == null }) {
            return "invalid_backup"
        }
        if (file.media.any { m ->
                m.metadataSource != null &&
                    com.seenly.app.domain.model.ProviderId.fromStorage(m.metadataSource) == null
            }
        ) {
            return "invalid_backup"
        }
        if (file.media.any { it.personalRating != null && !Rating.isValid(it.personalRating) }) {
            return "invalid_backup"
        }
        if (file.media.any { media -> media.releaseDate != null && media.releaseDate.toLocalDateOrNull() == null }) {
            return "invalid_backup"
        }

        val genreIds = file.genres.map { it.id }
        if (genreIds.size != genreIds.toSet().size || file.genres.any { it.name.isBlank() }) {
            return "invalid_backup"
        }
        val genreIdSet = genreIds.toSet()
        if (file.mediaGenres.any { it.mediaId !in mediaById.keys || it.genreId !in genreIdSet }) {
            return "invalid_backup"
        }
        if (file.mediaGenres.map { it.mediaId to it.genreId }.size !=
            file.mediaGenres.map { it.mediaId to it.genreId }.toSet().size
        ) {
            return "invalid_backup"
        }

        val providerPairs = file.providerIds.map { it.provider to it.externalId }
        if (providerPairs.size != providerPairs.toSet().size) return "invalid_backup"
        // TMDB is accepted only so backups made before v2 (which used it) still
        // validate; the provider itself is no longer part of the app.
        val knownProviders = setOf("ANILIST", "CINEMETA", "TVMAZE", "JIKAN", "TMDB")
        if (file.providerIds.any { link ->
                link.mediaId !in mediaById.keys ||
                    link.externalId.isBlank() ||
                    link.provider !in knownProviders
            }
        ) {
            return "invalid_backup"
        }

        val seasonIds = file.seasons.map { it.id }
        if (seasonIds.size != seasonIds.toSet().size) return "invalid_backup"
        val seasonById = file.seasons.associateBy { it.id }
        if (file.seasons.any { it.mediaId !in mediaById.keys }) return "invalid_backup"
        if (file.seasons.any { season ->
                season.airDate != null && season.airDate.toLocalDateOrNull() == null
            }
        ) {
            return "invalid_backup"
        }
        val seasonNumbers = file.seasons
            .filter { it.number != null }
            .groupBy { it.mediaId }
            .any { (_, seasons) -> seasons.map { it.number }.size != seasons.map { it.number }.toSet().size }
        if (seasonNumbers) return "invalid_backup"

        val episodeIds = file.episodes.map { it.id }
        if (episodeIds.size != episodeIds.toSet().size) return "invalid_backup"
        val episodeById = file.episodes.associateBy { it.id }
        if (file.episodes.any { it.mediaId !in mediaById.keys || it.seasonId !in seasonById.keys }) {
            return "invalid_backup"
        }
        if (file.episodes.any { episode ->
                seasonById[episode.seasonId]?.mediaId != episode.mediaId ||
                    (episode.airDate != null && episode.airDate.toLocalDateOrNull() == null)
            }
        ) {
            return "invalid_backup"
        }
        val duplicateEpisodeNumbers = file.episodes
            .filter { it.number != null }
            .groupBy { it.seasonId }
            .any { (_, episodes) ->
                episodes.map { it.number }.size != episodes.map { it.number }.toSet().size
            }
        if (duplicateEpisodeNumbers) return "invalid_backup"

        val progressIds = file.progress.map { it.mediaId }
        if (progressIds.size != progressIds.toSet().size) return "invalid_backup"
        if (file.progress.any { progress ->
                progress.mediaId !in mediaById.keys ||
                    com.seenly.app.domain.model.WatchStatus.entries.none { it.name == progress.status } ||
                    (progress.startDate != null && progress.startDate.toLocalDateOrNull() == null) ||
                    (progress.completionDate != null && progress.completionDate.toLocalDateOrNull() == null)
            }
        ) {
            return "invalid_backup"
        }

        if (file.events.any { event ->
                event.mediaId !in mediaById.keys ||
                    com.seenly.app.domain.model.EventType.entries.none { it.name == event.eventType } ||
                    event.localDate.toLocalDateOrNull() == null ||
                    (event.episodeId != null && event.episodeId !in episodeById.keys) ||
                    (event.episodeId != null && episodeById[event.episodeId]?.mediaId != event.mediaId)
            }
        ) {
            return "invalid_backup"
        }

        val listIds = file.lists.map { it.id }
        if (listIds.size != listIds.toSet().size) return "invalid_backup"
        if (file.lists.any { list ->
                list.id <= 0 ||
                    list.name.isBlank() ||
                    (list.smartFilter != null && list.smartFilter !in setOf("FAVORITES", "PLAN_TO_WATCH"))
            }
        ) {
            return "invalid_backup"
        }
        val listIdSet = listIds.toSet()
        if (file.listItems.any { it.listId !in listIdSet || it.mediaId !in mediaById.keys }) {
            return "invalid_backup"
        }
        val itemPairs = file.listItems.map { it.listId to it.mediaId }
        if (itemPairs.size != itemPairs.toSet().size) return "invalid_backup"

        if (file.goals.any { goal ->
                goal.target < 1 ||
                    com.seenly.app.domain.model.GoalType.entries.none { it.name == goal.type } ||
                    com.seenly.app.domain.model.GoalMetric.entries.none { it.name == goal.metric }
            }
        ) {
            return "invalid_backup"
        }

        return null
    }

    fun localDateOrNull(value: String): LocalDate? = value.toLocalDateOrNull()
}
