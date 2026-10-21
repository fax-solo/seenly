package com.seenly.app.data.repository

import androidx.room.withTransaction
import com.seenly.app.core.common.AppClock
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.data.local.entity.ListItemEntity
import com.seenly.app.data.local.entity.MediaListEntity
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.mapper.toDomain
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.MediaList
import com.seenly.app.domain.model.SmartList
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.repository.ListsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Lists and favorites.
 *
 * The built-in "Favorites" list is real membership; the built-in "Plan to Watch"
 * list is computed from the PLAN_TO_WATCH status so the two can never drift.
 * Custom list names are unique (case-insensitive) and deleting a list never
 * deletes titles.
 */
class ListsRepositoryImpl(
    private val db: SeenlyDatabase,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
) : ListsRepository {

    private val listDao = db.listDao()
    private val mediaDao = db.mediaDao()
    private val progressDao = db.progressDao()

    override fun observeLists(): Flow<List<MediaList>> =
        listDao.observeLists()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observeListItems(listId: Long): Flow<List<LibraryEntry>> =
        combine(
            listDao.observeLists(),
            listDao.observeItems(listId),
            mediaDao.observeLibraryRows(),
            mediaDao.observeMediaGenres(),
        ) { lists, storedItems, allRows, genreRows ->
            val genres = genreRows.groupBy({ it.mediaId }, { it.name })
            val list = lists.firstOrNull { it.list.id == listId }?.list
            val rows = if (SmartList.fromStorage(list?.smartFilter) == SmartList.PLAN_TO_WATCH) {
                allRows.filter { it.status == WatchStatus.PLAN_TO_WATCH.name }
            } else {
                storedItems
            }
            rows.map { row -> row.toDomain(genres[row.media.id].orEmpty()) }
        }.flowOn(dispatchers.io)

    override fun observeMembership(mediaId: Long): Flow<Set<Long>> =
        combine(
            listDao.observeMemberships(mediaId),
            listDao.observeLists(),
        ) { memberships, lists ->
            val storedLists = lists.mapNotNull { it.list }
                .filter { it.smartFilter == null || it.smartFilter == SmartList.FAVORITES.name }
                .map { it.id }
                .toSet()
            memberships.filter { it in storedLists }.toSet()
        }.flowOn(dispatchers.io)

    override suspend fun ensureBuiltinLists() = withContext(dispatchers.io) {
        db.withTransaction {
            val now = clock.nowMillis()
            if (listDao.findSmartList(SmartList.FAVORITES.name) == null &&
                listDao.findByName(BUILTIN_FAVORITES_NAME) == null
            ) {
                listDao.insert(
                    MediaListEntity(
                        name = BUILTIN_FAVORITES_NAME,
                        createdAt = now,
                        sortOrder = -20,
                        isBuiltin = true,
                        smartFilter = SmartList.FAVORITES.name,
                    ),
                )
            }
            if (listDao.findSmartList(SmartList.PLAN_TO_WATCH.name) == null &&
                listDao.findByName(BUILTIN_PLAN_NAME) == null
            ) {
                listDao.insert(
                    MediaListEntity(
                        name = BUILTIN_PLAN_NAME,
                        createdAt = now,
                        sortOrder = -10,
                        isBuiltin = true,
                        smartFilter = SmartList.PLAN_TO_WATCH.name,
                    ),
                )
            }
        }
    }

    override suspend fun createList(name: String, description: String?): Outcome<Long> =
        withContext(dispatchers.io) {
            val clean = name.trim()
            if (clean.isEmpty()) return@withContext Outcome.Failure(AppError.Invalid("empty_list_name"))
            if (clean.length > 60) return@withContext Outcome.Failure(AppError.Invalid("list_name_too_long"))
            db.withTransaction {
                if (listDao.findByName(clean) != null) {
                    return@withTransaction Outcome.Failure(AppError.Invalid("duplicate_list"))
                }
                val now = clock.nowMillis()
                val id = listDao.insert(
                    MediaListEntity(
                        name = clean,
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                        createdAt = now,
                        sortOrder = ((now / 1000L) % 1_000_000L).toInt(),
                        isBuiltin = false,
                    ),
                )
                Outcome.Success(id)
            }
        }

    override suspend fun updateList(listId: Long, name: String, description: String?): Outcome<Unit> =
        withContext(dispatchers.io) {
            val clean = name.trim()
            if (clean.isEmpty()) return@withContext Outcome.Failure(AppError.Invalid("empty_list_name"))
            db.withTransaction {
                val list = listDao.getById(listId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("list_not_found"))
                if (list.isBuiltin) {
                    // Built-in lists are localized in the UI, so only notes change.
                    listDao.update(
                        list.copy(description = description?.trim()?.takeIf { it.isNotEmpty() }),
                    )
                    return@withTransaction Outcome.Success(Unit)
                }
                val duplicate = listDao.findByName(clean)
                if (duplicate != null && duplicate.id != listId) {
                    return@withTransaction Outcome.Failure(AppError.Invalid("duplicate_list"))
                }
                listDao.update(
                    list.copy(
                        name = clean,
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
                Outcome.Success(Unit)
            }
        }

    override suspend fun deleteList(listId: Long): Outcome<Unit> = withContext(dispatchers.io) {
        db.withTransaction {
            val list = listDao.getById(listId)
                ?: return@withTransaction Outcome.Success(Unit)
            if (list.isBuiltin) {
                return@withTransaction Outcome.Failure(AppError.Invalid("builtin_list"))
            }
            listDao.deleteById(listId)
            Outcome.Success(Unit)
        }
    }

    override suspend fun addToList(listId: Long, mediaId: Long): Outcome<Unit> =
        withContext(dispatchers.io) {
            db.withTransaction {
                val list = listDao.getById(listId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("list_not_found"))
                if (mediaDao.getById(mediaId) == null) {
                    return@withTransaction Outcome.Failure(AppError.Invalid("title_not_found"))
                }
                if (SmartList.fromStorage(list.smartFilter) == SmartList.PLAN_TO_WATCH) {
                    val progress = progressDao.get(mediaId)
                    val current = WatchStatus.fromStorage(progress?.status)
                    if (current == WatchStatus.PLAN_TO_WATCH) {
                        return@withTransaction Outcome.Success(Unit)
                    }
                    val now = clock.nowMillis()
                    progressDao.upsert(
                        ProgressEntity(
                            mediaId = mediaId,
                            status = WatchStatus.PLAN_TO_WATCH.name,
                            startDate = null,
                            completionDate = null,
                            pausedAt = null,
                            notes = progress?.notes,
                            updatedAt = now,
                        ),
                    )
                    return@withTransaction Outcome.Success(Unit)
                }
                if (listDao.contains(listId, mediaId) > 0) {
                    return@withTransaction Outcome.Success(Unit)
                }
                listDao.addItem(
                    ListItemEntity(
                        listId = listId,
                        mediaId = mediaId,
                        position = listDao.maxPosition(listId) + 1,
                        addedAt = clock.nowMillis(),
                    ),
                )
                Outcome.Success(Unit)
            }
        }

    override suspend fun removeFromList(listId: Long, mediaId: Long) = withContext(dispatchers.io) {
        db.withTransaction {
            val list = listDao.getById(listId) ?: return@withTransaction
            if (SmartList.fromStorage(list.smartFilter) == SmartList.PLAN_TO_WATCH) {
                // Plan to Watch is status-backed; removing would be ambiguous, so
                // the UI hides the action for this computed list.
                return@withTransaction
            }
            listDao.removeItem(listId, mediaId)
        }
    }

    override suspend fun reorder(listId: Long, orderedMediaIds: List<Long>) =
        withContext(dispatchers.io) {
            db.withTransaction {
                val members = listDao.orderedMediaIds(listId).toSet()
                orderedMediaIds.filter { it in members }
                    .distinct()
                    .forEachIndexed { index, mediaId ->
                        listDao.updatePosition(listId, mediaId, index)
                    }
            }
        }

    override suspend fun toggleFavorite(mediaId: Long): Outcome<Boolean> =
        withContext(dispatchers.io) {
            db.withTransaction {
                if (mediaDao.getById(mediaId) == null) {
                    return@withTransaction Outcome.Failure(AppError.Invalid("title_not_found"))
                }
                val listId = favoritesListId()
                if (listDao.contains(listId, mediaId) > 0) {
                    listDao.removeItem(listId, mediaId)
                    Outcome.Success(false)
                } else {
                    listDao.addItem(
                        ListItemEntity(
                            listId = listId,
                            mediaId = mediaId,
                            position = listDao.maxPosition(listId) + 1,
                            addedAt = clock.nowMillis(),
                        ),
                    )
                    Outcome.Success(true)
                }
            }
        }

    override fun observeFavorites(): Flow<List<LibraryEntry>> =
        combine(
            listDao.observeFavorites(),
            mediaDao.observeMediaGenres(),
        ) { rows, genreRows ->
            val genres = genreRows.groupBy({ it.mediaId }, { it.name })
            rows.map { row -> row.toDomain(genres[row.media.id].orEmpty()) }
        }.flowOn(dispatchers.io)

    private suspend fun favoritesListId(): Long {
        listDao.findSmartList(SmartList.FAVORITES.name)?.let { return it.id }
        val byName = listDao.findByName(BUILTIN_FAVORITES_NAME)
        if (byName != null) return byName.id
        return listDao.insert(
            MediaListEntity(
                name = BUILTIN_FAVORITES_NAME,
                createdAt = clock.nowMillis(),
                sortOrder = -20,
                isBuiltin = true,
                smartFilter = SmartList.FAVORITES.name,
            ),
        )
    }

    private companion object {
        // Stored identifiers, not user-visible labels; the UI localizes built-ins.
        const val BUILTIN_FAVORITES_NAME = "Favorites"
        const val BUILTIN_PLAN_NAME = "Plan to Watch"
    }
}

/** Locale-independent normalized comparison for list names. */
internal fun normalizeListName(name: String): String = name.trim().lowercase(Locale.ROOT)
