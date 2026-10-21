package com.seenly.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A season of a series. [number] is null for specials / unknown season numbers;
 * duplicate-prevention happens in the repository because SQLite treats NULLs as
 * distinct in unique indexes.
 */
@Entity(
    tableName = "season",
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
        Index(value = ["mediaId", "number"], unique = true),
    ],
)
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
    tableName = "episode",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["id"],
            childColumns = ["seasonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("mediaId"),
        Index("seasonId"),
        Index(value = ["seasonId", "number"], unique = true),
        Index("airDate"),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val seasonId: Long,
    /** Episode number inside the season; null when the provider does not specify one. */
    val number: Int? = null,
    val absoluteNumber: Int? = null,
    val title: String? = null,
    val synopsis: String? = null,
    val runtimeMinutes: Int? = null,
    val airDate: String? = null,
    val stillUrl: String? = null,
    val metadataSource: String? = null,
)
