package com.seenly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.seenly.app.data.local.entity.GenreEntity
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.MediaGenreCrossRef
import com.seenly.app.data.local.entity.ProviderIdEntity
import com.seenly.app.data.local.entity.RemoteCacheEntity
import com.seenly.app.data.local.relation.LibraryRow
import com.seenly.app.data.local.relation.MediaGenreName
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Insert
    suspend fun insert(media: MediaEntity): Long

    @Update
    suspend fun update(media: MediaEntity)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id = :id")
    fun observeById(id: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media")
    suspend fun getAll(): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int

    @Query("SELECT * FROM media WHERE title = :title COLLATE NOCASE AND type = :type LIMIT 1")
    suspend fun findByTitleAndType(title: String, type: String): MediaEntity?

    @Query("UPDATE media SET lastModifiedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

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
        FROM media m
        LEFT JOIN progress p ON p.mediaId = m.id
        ORDER BY m.addedAt DESC
        """,
    )
    fun observeLibraryRows(): Flow<List<LibraryRow>>

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
        FROM media m
        LEFT JOIN progress p ON p.mediaId = m.id
        WHERE m.releaseDate IS NOT NULL AND m.releaseDate >= :fromDate
          AND (p.status IS NULL OR p.status != 'DROPPED')
        ORDER BY m.releaseDate
        LIMIT :limit
        """,
    )
    fun observeUpcomingReleases(fromDate: String, limit: Int): Flow<List<LibraryRow>>

    @Query(
        """
        SELECT mg.mediaId AS mediaId, g.name AS name
        FROM media_genre mg JOIN genre g ON g.id = mg.genreId
        """,
    )
    fun observeMediaGenres(): Flow<List<MediaGenreName>>

    // --- Genres ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenre(genre: GenreEntity): Long

    @Query("SELECT id FROM genre WHERE name = :name COLLATE NOCASE")
    suspend fun genreIdByName(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkGenre(ref: MediaGenreCrossRef)

    @Query("DELETE FROM media_genre WHERE mediaId = :mediaId")
    suspend fun clearGenres(mediaId: Long)

    @Query("SELECT DISTINCT g.name FROM genre g ORDER BY g.name")
    fun observeAllGenreNames(): Flow<List<String>>

    @Query(
        """
        SELECT g.name FROM genre g
        JOIN media_genre mg ON mg.genreId = g.id
        WHERE mg.mediaId = :mediaId
        ORDER BY g.name
        """,
    )
    suspend fun genresForMedia(mediaId: Long): List<String>

    // --- Provider ids ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProviderId(ref: ProviderIdEntity): Long

    @Query("SELECT * FROM provider_id WHERE provider = :provider AND externalId = :externalId LIMIT 1")
    suspend fun findProviderId(provider: String, externalId: String): ProviderIdEntity?

    /** Primary identity first, then the additional franchise identities. */
    @Query("SELECT * FROM provider_id WHERE mediaId = :mediaId ORDER BY id ASC")
    suspend fun providerIdsFor(mediaId: Long): List<ProviderIdEntity>

    @Query("SELECT mediaId FROM provider_id WHERE provider = :provider AND externalId = :externalId LIMIT 1")
    fun observeLocalMediaId(provider: String, externalId: String): Flow<Long?>

    @Query("DELETE FROM provider_id WHERE mediaId = :mediaId AND provider = :provider")
    suspend fun deleteProviderId(mediaId: Long, provider: String)

    // --- Remote cache ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCache(entry: RemoteCacheEntity)

    @Query("SELECT * FROM remote_cache WHERE key = :key")
    suspend fun getCache(key: String): RemoteCacheEntity?

    @Query("DELETE FROM remote_cache WHERE key = :key")
    suspend fun deleteCache(key: String)

    @Query("DELETE FROM remote_cache WHERE fetchedAt < :olderThan")
    suspend fun deleteCacheOlderThan(olderThan: Long)

    @Query("DELETE FROM remote_cache")
    suspend fun clearCache()

    @Query("SELECT COUNT(*) FROM remote_cache")
    suspend fun cacheCount(): Int

    @Query("SELECT COUNT(*) FROM remote_cache WHERE provider = :provider")
    suspend fun cacheCountForProvider(provider: String): Int

    // --- Backup / full-table access ---

    @Query("SELECT * FROM genre")
    suspend fun getAllGenres(): List<GenreEntity>

    @Query("SELECT * FROM media_genre")
    suspend fun getAllMediaGenres(): List<MediaGenreCrossRef>

    @Query("SELECT * FROM provider_id")
    suspend fun getAllProviderIds(): List<ProviderIdEntity>

    @Query("DELETE FROM media")
    suspend fun deleteAllMedia()

    @Query("DELETE FROM media_genre")
    suspend fun deleteAllMediaGenres()

    @Query("DELETE FROM genre")
    suspend fun deleteAllGenres()

    @Query("DELETE FROM provider_id")
    suspend fun deleteAllProviderIds()
}
