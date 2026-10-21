package com.seenly.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.seenly.app.core.common.AppClock
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.AppJson
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.core.time.IsoDates.toLocalDateOrNull
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.GoalEntity
import com.seenly.app.data.local.entity.GenreEntity
import com.seenly.app.data.local.entity.ListItemEntity
import com.seenly.app.data.local.entity.MediaGenreCrossRef
import com.seenly.app.data.local.entity.MediaListEntity
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.entity.ProviderIdEntity
import com.seenly.app.data.local.entity.SeasonEntity
import com.seenly.app.data.local.entity.WatchEventEntity
import com.seenly.app.domain.model.SmartList
import com.seenly.app.domain.repository.BackupRepository
import com.seenly.app.domain.repository.BackupSummary
import com.seenly.app.domain.repository.ImportMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * JSON backup/restore over Android's Storage Access Framework.
 *
 * Guarantees:
 * - The complete file is parsed and validated before any database write.
 * - The import runs in a single transaction; a failure leaves the library intact.
 * - Merge mode is idempotent: provider links, exact title/type signatures,
 *   seasons, episodes, lists and events are deduplicated.
 * - Replace mode is explicit (the UI confirms) and inserts the backup in one
 *   transaction, preserving IDs and therefore all relationships.
 * - API credentials and cached network responses are never exported.
 */
class BackupRepositoryImpl(
    context: Context,
    private val db: SeenlyDatabase,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
    private val appVersion: String,
) : BackupRepository {

    private val resolver = context.applicationContext.contentResolver
    private val mediaDao = db.mediaDao()
    private val seasonDao = db.seasonDao()
    private val episodeDao = db.episodeDao()
    private val progressDao = db.progressDao()
    private val eventDao = db.watchEventDao()
    private val listDao = db.listDao()
    private val goalDao = db.goalDao()

    override suspend fun export(uri: Uri): Outcome<BackupSummary> = withContext(dispatchers.io) {
        try {
            val file = collectBackup()
            val text = AppJson.encodeToString(file)
            val stream = resolver.openOutputStream(uri, "wt")
                ?: return@withContext Outcome.Failure(AppError.Data("cannot_open"))
            stream.use { output -> output.write(text.toByteArray(Charsets.UTF_8)) }
            Outcome.Success(
                BackupSummary(
                    mediaCount = file.media.size,
                    episodeCount = file.episodes.size,
                    eventCount = file.events.size,
                    listCount = file.lists.size,
                    goalCount = file.goals.size,
                    schemaVersion = file.schemaVersion,
                    exportedAt = file.exportedAt,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Outcome.Failure(AppError.Data("export_failed"))
        }
    }

    override suspend fun import(uri: Uri, mode: ImportMode): Outcome<BackupSummary> =
        withContext(dispatchers.io) {
            val file = try {
                readBackup(uri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: BackupTooLarge) {
                return@withContext Outcome.Failure(AppError.Invalid("backup_too_large"))
            } catch (_: SerializationException) {
                return@withContext Outcome.Failure(AppError.Invalid("invalid_backup"))
            } catch (_: IllegalArgumentException) {
                return@withContext Outcome.Failure(AppError.Invalid("invalid_backup"))
            } catch (_: Exception) {
                return@withContext Outcome.Failure(AppError.Data("cannot_open"))
            }

            BackupValidator.validate(file)?.let { code ->
                return@withContext Outcome.Failure(AppError.Invalid(code))
            }

            try {
                val summary = db.withTransaction {
                    if (mode == ImportMode.REPLACE) replaceAll(file) else merge(file)
                }
                Outcome.Success(summary)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Outcome.Failure(AppError.Data("import_failed"))
            }
        }

    // --- Export ---

    private suspend fun collectBackup(): BackupFile = BackupFile(
        schemaVersion = BackupFile.CURRENT_BACKUP_VERSION,
        appVersion = appVersion,
        exportedAt = clock.nowMillis(),
        media = mediaDao.getAll().map { it.toBackup() },
        genres = mediaDao.getAllGenres().map { it.toBackup() },
        mediaGenres = mediaDao.getAllMediaGenres().map { it.toBackup() },
        providerIds = mediaDao.getAllProviderIds().map { it.toBackup() },
        seasons = seasonDao.getAll().map { it.toBackup() },
        episodes = episodeDao.getAll().map { it.toBackup() },
        progress = progressDao.getAll().map { it.toBackup() },
        events = eventDao.getAll().map { it.toBackup() },
        lists = listDao.getAllLists().map { it.toBackup() },
        listItems = listDao.getAllItems().map { it.toBackup() },
        goals = goalDao.getAll().map { it.toBackup() },
    )

    private suspend fun readBackup(uri: Uri): BackupFile {
        val input: InputStream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("cannot open backup")
        val bytes = input.use { readLimited(it, MAX_BACKUP_BYTES) }
        return AppJson.decodeFromString<BackupFile>(String(bytes, Charsets.UTF_8))
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(16 * 1024)
        val output = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw BackupTooLarge()
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    // --- Replace ---

    private suspend fun replaceAll(file: BackupFile): BackupSummary {
        eventDao.deleteAll()
        progressDao.deleteAll()
        listDao.deleteAllItems()
        listDao.deleteAllLists()
        goalDao.deleteAll()
        episodeDao.deleteAll()
        seasonDao.deleteAll()
        mediaDao.deleteAllProviderIds()
        mediaDao.deleteAllMediaGenres()
        mediaDao.deleteAllGenres()
        mediaDao.deleteAllMedia()
        mediaDao.clearCache()

        file.genres.forEach { genre ->
            mediaDao.insertGenre(GenreEntity(id = genre.id, name = genre.name))
        }
        file.media.forEach { media ->
            mediaDao.insert(media.toEntityWithId())
        }
        file.mediaGenres.forEach { ref ->
            mediaDao.linkGenre(MediaGenreCrossRef(mediaId = ref.mediaId, genreId = ref.genreId))
        }
        file.providerIds.forEach { link ->
            mediaDao.insertProviderId(
                ProviderIdEntity(
                    id = link.id,
                    mediaId = link.mediaId,
                    provider = link.provider,
                    externalId = link.externalId,
                    linkedAt = link.linkedAt,
                ),
            )
        }
        file.seasons.forEach { season ->
            seasonDao.insert(season.toEntity(season.mediaId).copy(id = season.id))
        }
        file.episodes.forEach { episode ->
            episodeDao.insert(
                episode.toEntity(episode.mediaId, episode.seasonId).copy(id = episode.id),
            )
        }
        file.progress.forEach { progress ->
            progressDao.upsert(progress.toEntity(progress.mediaId))
        }
        file.events.forEach { event ->
            eventDao.insert(event.toEntity(event.mediaId, event.episodeId).copy(id = event.id))
        }
        file.lists.forEach { list ->
            listDao.insert(list.toEntity().copy(id = list.id))
        }
        file.listItems.forEach { item ->
            listDao.addItem(
                ListItemEntity(
                    listId = item.listId,
                    mediaId = item.mediaId,
                    position = item.position,
                    addedAt = item.addedAt,
                ),
            )
        }
        file.goals.forEach { goal ->
            goalDao.insert(goal.toEntity().copy(id = goal.id))
        }

        return BackupSummary(
            mediaCount = file.media.size,
            episodeCount = file.episodes.size,
            eventCount = file.events.size,
            listCount = file.lists.size,
            goalCount = file.goals.size,
            schemaVersion = file.schemaVersion,
            exportedAt = file.exportedAt,
        )
    }

    // --- Merge ---

    private suspend fun merge(file: BackupFile): BackupSummary {
        val now = clock.nowMillis()
        val localProviderPairs = mutableMapOf<Pair<String, String>, Long>()
        mediaDao.getAllProviderIds().forEach { link ->
            localProviderPairs[link.provider to link.externalId] = link.mediaId
        }
        val incomingLinksByMedia = file.providerIds.groupBy { it.mediaId }

        val mediaMap = mutableMapOf<Long, Long>()
        var mediaAdded = 0
        for (media in file.media) {
            val providerTarget = incomingLinksByMedia[media.id].orEmpty()
                .firstNotNullOfOrNull { link -> localProviderPairs[link.provider to link.externalId] }
            val titleTarget = if (providerTarget == null) {
                mediaDao.findByTitleAndType(media.title, media.type)
                    ?.takeIf { local ->
                        val localDate = local.releaseDate.toLocalDateOrNull()
                        val incomingDate = media.releaseDate.toLocalDateOrNull()
                        localDate == null || incomingDate == null || localDate == incomingDate
                    }
                    ?.id
            } else {
                null
            }
            val target = providerTarget ?: titleTarget
            if (target != null) {
                mediaMap[media.id] = target
            } else {
                val newId = mediaDao.insert(media.toEntity(now))
                mediaMap[media.id] = newId
                mediaAdded++
            }
        }

        val genreMap = mutableMapOf<Long, Long>()
        for (genre in file.genres) {
            val name = genre.name.trim()
            if (name.isEmpty()) continue
            val existing = mediaDao.genreIdByName(name)
            val id = existing ?: run {
                mediaDao.insertGenre(GenreEntity(name = name))
                mediaDao.genreIdByName(name)
            } ?: continue
            genreMap[genre.id] = id
        }
        file.mediaGenres.forEach { ref ->
            val mediaId = mediaMap[ref.mediaId] ?: return@forEach
            val genreId = genreMap[ref.genreId] ?: return@forEach
            mediaDao.linkGenre(MediaGenreCrossRef(mediaId = mediaId, genreId = genreId))
        }

        file.providerIds.forEach { link ->
            val mediaId = mediaMap[link.mediaId] ?: return@forEach
            val pair = link.provider to link.externalId
            if (localProviderPairs[pair] == null) {
                mediaDao.insertProviderId(
                    ProviderIdEntity(
                        mediaId = mediaId,
                        provider = link.provider,
                        externalId = link.externalId,
                        linkedAt = link.linkedAt.takeIf { it > 0 } ?: now,
                    ),
                )
                localProviderPairs[pair] = mediaId
            }
        }

        var seasonsAdded = 0
        val seasonMap = mutableMapOf<Long, Long>()
        for (season in file.seasons) {
            val mediaId = mediaMap[season.mediaId] ?: continue
            val existing = season.number?.let { seasonDao.findByNumber(mediaId, it) }
            val id = if (existing != null) {
                existing.id
            } else {
                val inserted = seasonDao.insert(season.toEntity(mediaId))
                if (inserted > 0) {
                    seasonsAdded++
                    inserted
                } else {
                    season.number?.let { seasonDao.findByNumber(mediaId, it)?.id } ?: 0L
                }
            }
            if (id > 0) seasonMap[season.id] = id
        }

        var episodesAdded = 0
        val episodeMap = mutableMapOf<Long, Long>()
        for (episode in file.episodes) {
            val mediaId = mediaMap[episode.mediaId] ?: continue
            val seasonId = seasonMap[episode.seasonId] ?: continue
            val existing = episode.number?.let { episodeDao.findBySeasonAndNumber(seasonId, it) }
            val id = if (existing != null) {
                existing.id
            } else {
                val inserted = episodeDao.insert(episode.toEntity(mediaId, seasonId))
                if (inserted > 0) {
                    episodesAdded++
                    inserted
                } else {
                    episode.number?.let { episodeDao.findBySeasonAndNumber(seasonId, it)?.id } ?: 0L
                }
            }
            if (id > 0) episodeMap[episode.id] = id
        }

        file.progress.forEach { progress ->
            val mediaId = mediaMap[progress.mediaId] ?: return@forEach
            if (progressDao.get(mediaId) == null) {
                progressDao.upsert(progress.toEntity(mediaId))
            }
        }

        var eventsAdded = 0
        val eventKeys = mutableMapOf<Long, MutableSet<String>>()
        for (event in file.events) {
            val mediaId = mediaMap[event.mediaId] ?: continue
            val episodeId = if (event.episodeId == null) {
                null
            } else {
                episodeMap[event.episodeId] ?: continue
            }
            val keys = eventKeys.getOrPut(mediaId) {
                eventDao.allForMedia(mediaId)
                    .map { existing ->
                        eventKey(
                            eventType = existing.eventType,
                            episodeId = existing.episodeId,
                            startedAt = existing.startedAt,
                            completedAt = existing.completedAt,
                        )
                    }
                    .toMutableSet()
            }
            val key = eventKey(event.eventType, episodeId, event.startedAt, event.completedAt)
            if (!keys.add(key)) continue
            eventDao.insert(event.toEntity(mediaId, episodeId))
            eventsAdded++
        }

        val listMap = mutableMapOf<Long, Long>()
        for (list in file.lists) {
            val smart = list.smartFilter?.let { value ->
                SmartList.entries.firstOrNull { it.name == value }
            }
            val existing = when {
                smart != null -> listDao.findSmartList(smart.name) ?: listDao.findByName(list.name)
                else -> listDao.findByName(list.name)
            }
            val id = existing?.id ?: run {
                val inserted = listDao.insert(
                    MediaListEntity(
                        name = list.name.trim(),
                        description = list.description,
                        createdAt = list.createdAt.takeIf { it > 0 } ?: now,
                        sortOrder = list.sortOrder,
                        isBuiltin = smart != null,
                        smartFilter = smart?.name,
                        coverUrl = list.coverUrl,
                    ),
                )
                inserted.takeIf { it > 0 } ?: listDao.findByName(list.name)?.id ?: 0L
            }
            if (id > 0) listMap[list.id] = id
        }

        var itemsAdded = 0
        val listsTouched = mutableSetOf<Long>()
        for (item in file.listItems) {
            val listId = listMap[item.listId] ?: continue
            val mediaId = mediaMap[item.mediaId] ?: continue
            val list = listDao.getById(listId) ?: continue
            if (SmartList.entries.firstOrNull { it.name == list.smartFilter } == SmartList.PLAN_TO_WATCH) {
                continue
            }
            if (listDao.contains(listId, mediaId) > 0) continue
            listDao.addItem(
                ListItemEntity(
                    listId = listId,
                    mediaId = mediaId,
                    position = listDao.maxPosition(listId) + 1,
                    addedAt = item.addedAt.takeIf { it > 0 } ?: now,
                ),
            )
            itemsAdded++
            listsTouched += listId
        }

        var goalsAdded = 0
        val existingGoals = goalDao.getAll()
        for (goal in file.goals) {
            if (existingGoals.any { it.type == goal.type && it.metric == goal.metric }) continue
            goalDao.insert(goal.toEntity())
            goalsAdded++
        }

        return BackupSummary(
            mediaCount = mediaAdded,
            episodeCount = episodesAdded,
            eventCount = eventsAdded,
            listCount = listsTouched.size,
            goalCount = goalsAdded,
            schemaVersion = file.schemaVersion,
            exportedAt = file.exportedAt,
        )
    }

    private fun eventKey(
        eventType: String,
        episodeId: Long?,
        startedAt: Long,
        completedAt: Long?,
    ): String = "$eventType|${episodeId ?: "movie"}|$startedAt|${completedAt ?: -1L}"

    private class BackupTooLarge : Exception()

    private companion object {
        const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    }
}
