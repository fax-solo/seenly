package com.seenly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.seenly.app.data.local.entity.GoalEntity
import com.seenly.app.data.local.entity.ListItemEntity
import com.seenly.app.data.local.entity.MediaListEntity
import com.seenly.app.data.local.relation.LibraryRow
import com.seenly.app.data.local.relation.ListRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {

    @Insert
    suspend fun insert(list: MediaListEntity): Long

    @Update
    suspend fun update(list: MediaListEntity)

    @Query("DELETE FROM media_list WHERE id = :listId")
    suspend fun deleteById(listId: Long)

    @Query("SELECT * FROM media_list WHERE id = :id")
    suspend fun getById(id: Long): MediaListEntity?

    @Query("SELECT * FROM media_list WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): MediaListEntity?

    @Query("SELECT * FROM media_list WHERE smartFilter = :filter LIMIT 1")
    suspend fun findSmartList(filter: String): MediaListEntity?

    @Query(
        """
        SELECT l.*,
          (CASE WHEN l.smartFilter = 'PLAN_TO_WATCH'
            THEN (SELECT COUNT(*) FROM progress p WHERE p.status = 'PLAN_TO_WATCH')
            ELSE (SELECT COUNT(*) FROM list_item i WHERE i.listId = l.id) END) AS itemCount
        FROM media_list l
        ORDER BY l.sortOrder, l.name
        """,
    )
    fun observeLists(): Flow<List<ListRow>>

    @Query("SELECT COALESCE(MAX(position), 0) FROM list_item WHERE listId = :listId")
    suspend fun maxPosition(listId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: ListItemEntity): Long

    @Query("DELETE FROM list_item WHERE listId = :listId AND mediaId = :mediaId")
    suspend fun removeItem(listId: Long, mediaId: Long)

    @Query("UPDATE list_item SET position = :position WHERE listId = :listId AND mediaId = :mediaId")
    suspend fun updatePosition(listId: Long, mediaId: Long, position: Int)

    @Query("SELECT COUNT(*) FROM list_item WHERE listId = :listId AND mediaId = :mediaId")
    suspend fun contains(listId: Long, mediaId: Long): Int

    @Query("SELECT listId FROM list_item WHERE mediaId = :mediaId")
    fun observeMemberships(mediaId: Long): Flow<List<Long>>

    @Query("SELECT mediaId FROM list_item WHERE listId = :listId ORDER BY position")
    suspend fun orderedMediaIds(listId: Long): List<Long>

    @Query(
        """
        SELECT m.*, p.status AS status,
          (SELECT COUNT(DISTINCT w.episodeId) FROM watch_event w
            WHERE w.mediaId = m.id AND w.episodeId IS NOT NULL
              AND w.completedAt IS NOT NULL AND w.eventType = 'FIRST_WATCH') AS episodesWatched,
          (SELECT COUNT(*) FROM episode e WHERE e.mediaId = m.id) AS episodesTotal,
          (SELECT MAX(COALESCE(w.completedAt, w.startedAt)) FROM watch_event w
            WHERE w.mediaId = m.id AND w.completedAt IS NOT NULL) AS lastActivityAt,
          (SELECT COUNT(*) FROM watch_event w
            WHERE w.mediaId = m.id AND w.completedAt IS NOT NULL) AS viewingsCount,
          (SELECT COUNT(*) FROM watch_event w
            WHERE w.mediaId = m.id AND w.completedAt IS NOT NULL AND w.eventType = 'REWATCH') AS rewatchCount
        FROM list_item i
        JOIN media m ON m.id = i.mediaId
        LEFT JOIN progress p ON p.mediaId = m.id
        WHERE i.listId = :listId
        ORDER BY i.position, i.addedAt
        """,
    )
    fun observeItems(listId: Long): Flow<List<LibraryRow>>

    @Query(
        """
        SELECT m.*, p.status AS status,
          (SELECT COUNT(DISTINCT w.episodeId) FROM watch_event w
            WHERE w.mediaId = m.id AND w.episodeId IS NOT NULL
              AND w.completedAt IS NOT NULL AND w.eventType = 'FIRST_WATCH') AS episodesWatched,
          (SELECT COUNT(*) FROM episode e WHERE e.mediaId = m.id) AS episodesTotal,
          (SELECT MAX(COALESCE(w.completedAt, w.startedAt)) FROM watch_event w
            WHERE w.mediaId = m.id AND w.completedAt IS NOT NULL) AS lastActivityAt,
          (SELECT COUNT(*) FROM watch_event w
            WHERE w.mediaId = m.id AND w.completedAt IS NOT NULL) AS viewingsCount,
          (SELECT COUNT(*) FROM watch_event w
            WHERE w.mediaId = m.id AND w.completedAt IS NOT NULL AND w.eventType = 'REWATCH') AS rewatchCount
        FROM list_item i
        JOIN media_list l ON l.id = i.listId
        JOIN media m ON m.id = i.mediaId
        LEFT JOIN progress p ON p.mediaId = m.id
        WHERE l.smartFilter = 'FAVORITES'
        ORDER BY i.position, i.addedAt
        """,
    )
    fun observeFavorites(): Flow<List<LibraryRow>>

    // --- Backup / full-table access ---

    @Query("SELECT * FROM media_list")
    suspend fun getAllLists(): List<MediaListEntity>

    @Query("SELECT * FROM list_item")
    suspend fun getAllItems(): List<ListItemEntity>

    @Query("DELETE FROM list_item")
    suspend fun deleteAllItems()

    @Query("DELETE FROM media_list")
    suspend fun deleteAllLists()
}

@Dao
interface GoalDao {

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun getById(id: Long): GoalEntity?

    @Query("SELECT * FROM goal ORDER BY createdAt")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal")
    suspend fun getAll(): List<GoalEntity>

    @Query("SELECT * FROM goal WHERE active = 1")
    fun observeActive(): Flow<List<GoalEntity>>

    @Query("UPDATE goal SET active = 0")
    suspend fun deactivateAll()

    @Query("DELETE FROM goal")
    suspend fun deleteAll()
}
