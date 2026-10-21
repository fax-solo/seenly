package com.seenly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.entity.WatchEventEntity
import com.seenly.app.data.local.relation.ActivityRow
import com.seenly.app.data.local.relation.WatchedEventStatRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Upsert
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Long): ProgressEntity?

    @Query("SELECT * FROM progress WHERE mediaId = :mediaId")
    fun observe(mediaId: Long): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE status = :status")
    suspend fun byStatus(status: String): List<ProgressEntity>

    @Query("DELETE FROM progress WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)

    @Query("SELECT * FROM progress")
    suspend fun getAll(): List<ProgressEntity>

    @Query("DELETE FROM progress")
    suspend fun deleteAll()
}

@Dao
interface WatchEventDao {

    @Insert
    suspend fun insert(event: WatchEventEntity): Long

    @Update
    suspend fun update(event: WatchEventEntity)

    @Query("SELECT * FROM watch_event WHERE id = :id")
    suspend fun getById(id: Long): WatchEventEntity?

    /** Existing completed first-watch for an episode — used to prevent duplicates. */
    @Query(
        """
        SELECT * FROM watch_event
        WHERE episodeId = :episodeId AND completedAt IS NOT NULL AND eventType = 'FIRST_WATCH'
        LIMIT 1
        """,
    )
    suspend fun completedFirstWatchForEpisode(episodeId: Long): WatchEventEntity?

    @Query("SELECT * FROM watch_event WHERE mediaId = :mediaId AND completedAt IS NOT NULL")
    suspend fun completedForMedia(mediaId: Long): List<WatchEventEntity>

    @Query("SELECT * FROM watch_event WHERE mediaId = :mediaId AND completedAt IS NOT NULL")
    fun observeCompletedForMedia(mediaId: Long): Flow<List<WatchEventEntity>>

    @Query("SELECT episodeId FROM watch_event WHERE mediaId = :mediaId AND completedAt IS NOT NULL AND episodeId IS NOT NULL")
    suspend fun watchedEpisodeIds(mediaId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM watch_event WHERE mediaId = :mediaId AND completedAt IS NOT NULL")
    suspend fun completedCountForMedia(mediaId: Long): Int

    @Query("UPDATE watch_event SET startedAt = :startedAt, completedAt = :completedAt, localDate = :localDate WHERE id = :id")
    suspend fun updateDate(id: Long, startedAt: Long, completedAt: Long?, localDate: String)

    @Query("DELETE FROM watch_event WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM watch_event WHERE episodeId = :episodeId")
    suspend fun deleteForEpisode(episodeId: Long)

    @Query("UPDATE watch_event SET episodeId = :newEpisodeId WHERE episodeId = :oldEpisodeId")
    suspend fun remapEpisode(oldEpisodeId: Long, newEpisodeId: Long)

    @Query("DELETE FROM watch_event WHERE mediaId = :mediaId")
    suspend fun deleteForMedia(mediaId: Long)

    @Query("DELETE FROM watch_event WHERE mediaId = :mediaId AND eventType = :eventType")
    suspend fun deleteForMediaByType(mediaId: Long, eventType: String)

    @Query("SELECT * FROM watch_event WHERE mediaId = :mediaId")
    suspend fun allForMedia(mediaId: Long): List<WatchEventEntity>

    @Query("SELECT * FROM watch_event")
    suspend fun getAll(): List<WatchEventEntity>

    @Query("DELETE FROM watch_event")
    suspend fun deleteAll()

    @Query(
        """
        SELECT w.id AS eventId, w.mediaId AS mediaId, m.title AS mediaTitle, m.posterUrl AS posterUrl,
          m.type AS mediaType, w.episodeId AS episodeId, s.number AS seasonNumber,
          e.number AS episodeNumber, e.title AS episodeTitle,
          COALESCE(w.completedAt, w.startedAt) AS watchedAtMillis,
          w.localDate AS localDate, w.eventType AS eventType
        FROM watch_event w
        JOIN media m ON m.id = w.mediaId
        LEFT JOIN episode e ON e.id = w.episodeId
        LEFT JOIN season s ON s.id = e.seasonId
        WHERE w.completedAt IS NOT NULL
        ORDER BY COALESCE(w.completedAt, w.startedAt) DESC
        """,
    )
    fun observeAllActivity(): Flow<List<ActivityRow>>

    @Query(
        """
        SELECT w.id AS eventId, w.mediaId AS mediaId, m.title AS mediaTitle, m.posterUrl AS posterUrl,
          m.type AS mediaType, w.episodeId AS episodeId, s.number AS seasonNumber,
          e.number AS episodeNumber, e.title AS episodeTitle,
          COALESCE(w.completedAt, w.startedAt) AS watchedAtMillis,
          w.localDate AS localDate, w.eventType AS eventType
        FROM watch_event w
        JOIN media m ON m.id = w.mediaId
        LEFT JOIN episode e ON e.id = w.episodeId
        LEFT JOIN season s ON s.id = e.seasonId
        WHERE w.completedAt IS NOT NULL
        ORDER BY COALESCE(w.completedAt, w.startedAt) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentActivity(limit: Int): Flow<List<ActivityRow>>

    @Query(
        """
        SELECT w.mediaId AS mediaId, m.type AS mediaType, w.episodeId AS episodeId,
          w.eventType AS eventType, w.localDate AS localDate,
          COALESCE(w.completedAt, w.startedAt) AS completedAtMillis,
          COALESCE(e.runtimeMinutes, m.runtimeMinutes) AS minutes
        FROM watch_event w
        JOIN media m ON m.id = w.mediaId
        LEFT JOIN episode e ON e.id = w.episodeId
        WHERE w.completedAt IS NOT NULL
        """,
    )
    fun observeCompletedEventsForStats(): Flow<List<WatchedEventStatRow>>

    @Query(
        """
        SELECT COUNT(*) FROM watch_event
        WHERE completedAt IS NOT NULL AND localDate >= :fromDate AND localDate <= :toDate
        """,
    )
    fun observeCountInRange(fromDate: String, toDate: String): Flow<Int>
}
