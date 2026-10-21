package com.seenly.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A title in the user's library (or a cached provider title).
 *
 * Personal fields (rating, review, notes) live in the same row but are only ever
 * written by the user's own actions; metadata refreshes never touch them.
 */
@Entity(
    tableName = "media",
    indices = [
        Index("type"),
        Index("addedAt"),
        Index("personalRating"),
        Index("title"),
        Index("releaseDate"),
    ],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val originalTitle: String? = null,
    val alternateTitles: List<String> = emptyList(),
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    /** ISO-8601 local date, e.g. 2024-04-01. */
    val releaseDate: String? = null,
    val endDate: String? = null,
    val runtimeMinutes: Int? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null,
    val studio: String? = null,
    val tags: List<String> = emptyList(),
    /** Director names and top-billed cast, when the provider supplies them. */
    val directorNames: List<String> = emptyList(),
    val castNames: List<String> = emptyList(),
    val providerScore: Double? = null,
    /** External ratings, best-effort. Null means "not known", never a guess. */
    val imdbId: String? = null,
    val imdbRating: Double? = null,
    val rottenTomatoesRating: Int? = null,
    val malRating: Double? = null,
    val ratingsUpdatedAt: Long? = null,
    val metadataSource: String? = null,
    val metadataUpdatedAt: Long? = null,
    /** True when the user edited local fields that provider refreshes must not overwrite. */
    val locallyEdited: Boolean = false,
    val addedAt: Long,
    val lastModifiedAt: Long,
    // --- Personal data ---
    val personalRating: Double? = null,
    val review: String? = null,
    val reviewIsSpoiler: Boolean = false,
    val reviewCreatedAt: Long? = null,
    val reviewUpdatedAt: Long? = null,
    val personalNotes: String? = null,
)

/** Genre catalog. Shared across all titles; manually entered titles can create genres. */
@Entity(tableName = "genre", indices = [Index(value = ["name"], unique = true)])
data class GenreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "media_genre",
    primaryKeys = ["mediaId", "genreId"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["id"],
            childColumns = ["genreId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("genreId")],
)
data class MediaGenreCrossRef(
    val mediaId: Long,
    val genreId: Long,
)

/**
 * External provider identifiers. The unique (provider, externalId) index is the
 * duplicate-detection mechanism when importing the same title twice.
 */
@Entity(
    tableName = "provider_id",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("mediaId"),
        Index(value = ["provider", "externalId"], unique = true),
    ],
)
data class ProviderIdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val provider: String,
    val externalId: String,
    val linkedAt: Long,
)

/**
 * Lightweight provider-response cache (trending pages, seasonal lists, upcoming
 * releases). Expiry only ever removes cached rows — never user records.
 */
@Entity(tableName = "remote_cache", indices = [Index("provider"), Index("fetchedAt")])
data class RemoteCacheEntity(
    @PrimaryKey val key: String,
    val provider: String,
    val payload: String,
    val fetchedAt: Long,
)
