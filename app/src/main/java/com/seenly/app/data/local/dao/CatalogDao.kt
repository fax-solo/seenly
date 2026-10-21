package com.seenly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.SeasonEntity
import com.seenly.app.data.local.relation.EpisodeKey
import com.seenly.app.data.local.relation.EpisodeRow
import com.seenly.app.data.local.relation.UpcomingEpisodeRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SeasonDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(season: SeasonEntity): Long

    @Update
    suspend fun update(season: SeasonEntity)

    @Query("SELECT id FROM season WHERE mediaId = :mediaId AND number = :number LIMIT 1")
    suspend fun findIdByNumber(mediaId: Long, number: Int): Long?

    @Query("SELECT * FROM season")
    suspend fun getAll(): List<SeasonEntity>

    @Query("DELETE FROM season")
    suspend fun deleteAll()

    @Query("SELECT * FROM season WHERE id = :id")
    suspend fun getById(id: Long): SeasonEntity?

    @Query("SELECT * FROM season WHERE mediaId = :mediaId AND number = :number LIMIT 1")
    suspend fun findByNumber(mediaId: Long, number: Int): SeasonEntity?

    /** The single unnumbered "specials" season, if one exists. */
    @Query("SELECT * FROM season WHERE mediaId = :mediaId AND number IS NULL ORDER BY sortOrder, id LIMIT 1")
    suspend fun findSpecials(mediaId: Long): SeasonEntity?

    @Query(
        """
        SELECT * FROM season WHERE mediaId = :mediaId
        ORDER BY (number IS NULL), number, sortOrder, id
        """,
    )
    fun observeForMedia(mediaId: Long): Flow<List<SeasonEntity>>

    @Query(
        """
        SELECT * FROM season WHERE mediaId = :mediaId
        ORDER BY (number IS NULL), number, sortOrder, id
        """,
    )
    suspend fun listForMedia(mediaId: Long): List<SeasonEntity>

    /** Removes seasons that no longer exist in fresh metadata, unless they hold history. */
    @Query(
        """
        DELETE FROM season WHERE mediaId = :mediaId AND id NOT IN (:keepIds)
          AND id NOT IN (SELECT seasonId FROM episode WHERE id IN
            (SELECT episodeId FROM watch_event WHERE episodeId IS NOT NULL))
        """,
    )
    suspend fun deleteSeasonsNotIn(mediaId: Long, keepIds: List<Long>)
}

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(episode: EpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<EpisodeEntity>): List<Long>

    @Update
    suspend fun update(episode: EpisodeEntity)

    @Query("SELECT * FROM episode WHERE id = :id")
    suspend fun getById(id: Long): EpisodeEntity?

    @Query("SELECT * FROM episode WHERE seasonId = :seasonId AND number = :number LIMIT 1")
    suspend fun findBySeasonAndNumber(seasonId: Long, number: Int): EpisodeEntity?

    @Query(
        """
        SELECT e.*,
          (SELECT MAX(w.completedAt) FROM watch_event w
            WHERE w.episodeId = e.id AND w.completedAt IS NOT NULL AND w.eventType = 'FIRST_WATCH') AS watchedAt,
          EXISTS(SELECT 1 FROM watch_event w2
            WHERE w2.episodeId = e.id AND w2.eventType = 'REWATCH') AS rewatched
        FROM episode e
        WHERE e.mediaId = :mediaId
        ORDER BY (e.number IS NULL), e.number, e.absoluteNumber, e.id
        """,
    )
    fun observeForMedia(mediaId: Long): Flow<List<EpisodeRow>>

    @Query(
        """
        SELECT e.*,
          (SELECT MAX(w.completedAt) FROM watch_event w
            WHERE w.episodeId = e.id AND w.completedAt IS NOT NULL AND w.eventType = 'FIRST_WATCH') AS watchedAt,
          EXISTS(SELECT 1 FROM watch_event w2
            WHERE w2.episodeId = e.id AND w2.eventType = 'REWATCH') AS rewatched
        FROM episode e
        WHERE e.seasonId = :seasonId
        ORDER BY (e.number IS NULL), e.number, e.absoluteNumber, e.id
        """,
    )
    fun observeForSeason(seasonId: Long): Flow<List<EpisodeRow>>

    @Query("SELECT COUNT(*) FROM episode WHERE mediaId = :mediaId")
    suspend fun countForMedia(mediaId: Long): Int

    @Query("SELECT id FROM episode WHERE seasonId = :seasonId")
    suspend fun idsForSeason(seasonId: Long): List<Long>

    @Query(
        """
        SELECT e.id AS episodeId, s.number AS seasonNumber, e.number AS episodeNumber
        FROM episode e JOIN season s ON s.id = e.seasonId
        WHERE e.mediaId = :mediaId
        ORDER BY (s.number IS NULL), s.number, s.sortOrder, (e.number IS NULL), e.number, e.id
        """,
    )
    suspend fun orderedEpisodeKeys(mediaId: Long): List<EpisodeKey>

    /** First unwatched episode in season/episode order; the "continue watching" target. */
    @Query(
        """
        SELECT e.* FROM episode e
        JOIN season s ON s.id = e.seasonId
        LEFT JOIN (
          SELECT episodeId, MAX(completedAt) AS c
          FROM watch_event
          WHERE completedAt IS NOT NULL AND eventType = 'FIRST_WATCH'
          GROUP BY episodeId
        ) w ON w.episodeId = e.id
        WHERE e.mediaId = :mediaId AND (w.c IS NULL OR w.c = 0)
        ORDER BY (s.number IS NULL), s.number, s.sortOrder, (e.number IS NULL), e.number, e.id
        LIMIT 1
        """,
    )
    suspend fun firstUnwatched(mediaId: Long): EpisodeEntity?

    /** Removes episodes missing from fresh metadata unless they carry watch history. */
    @Query(
        """
        DELETE FROM episode WHERE mediaId = :mediaId AND id NOT IN (:keepIds)
          AND id NOT IN (SELECT episodeId FROM watch_event WHERE episodeId IS NOT NULL)
        """,
    )
    suspend fun deleteEpisodesNotIn(mediaId: Long, keepIds: List<Long>)

    @Query(
        """
        SELECT e.*, s.number AS seasonNumber, m.title AS mediaTitle, m.posterUrl AS mediaPosterUrl,
          m.type AS mediaType
        FROM episode e
        JOIN season s ON s.id = e.seasonId
        JOIN media m ON m.id = e.mediaId
        LEFT JOIN progress p ON p.mediaId = m.id
        WHERE e.airDate IS NOT NULL AND e.airDate >= :fromDate AND e.airDate <= :toDate
          AND (p.status IS NULL OR p.status != 'DROPPED')
          AND e.id NOT IN (
            SELECT episodeId FROM watch_event
            WHERE episodeId IS NOT NULL AND completedAt IS NOT NULL AND eventType = 'FIRST_WATCH'
          )
        ORDER BY e.airDate, (s.number IS NULL), s.number, (e.number IS NULL), e.number
        LIMIT :limit
        """,
    )
    fun observeUpcomingEpisodes(fromDate: String, toDate: String, limit: Int): Flow<List<UpcomingEpisodeRow>>

    @Query("SELECT COALESCE(SUM(runtimeMinutes), 0) FROM episode WHERE mediaId = :mediaId AND runtimeMinutes IS NOT NULL")
    suspend fun totalKnownRuntime(mediaId: Long): Int

    @Query("SELECT * FROM episode")
    suspend fun getAll(): List<EpisodeEntity>

    @Query("DELETE FROM episode")
    suspend fun deleteAll()
}
