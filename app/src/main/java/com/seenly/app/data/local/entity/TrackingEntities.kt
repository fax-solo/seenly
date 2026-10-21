package com.seenly.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Watch status row, one per library title.
 *
 * Episodes watched, current episode, rewatch counts and last activity are all
 * DERIVED from [WatchEventEntity] rows so the two can never contradict each
 * other. This table stores only the user's explicit status decisions and dates.
 */
@Entity(
    tableName = "progress",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("status"), Index("completionDate")],
)
data class ProgressEntity(
    @PrimaryKey val mediaId: Long,
    val status: String,
    val startDate: String? = null,
    val completionDate: String? = null,
    val pausedAt: Long? = null,
    val notes: String? = null,
    val updatedAt: Long,
)

/**
 * Append-only viewing history. One row per viewing session; rewatches add rows
 * instead of replacing anything. A row with a null [completedAt] is an
 * in-progress session and never counts as a completed watch.
 */
@Entity(
    tableName = "watch_event",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("mediaId"),
        Index("episodeId"),
        Index("localDate"),
        Index("completedAt"),
    ],
)
data class WatchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val episodeId: Long? = null,
    val eventType: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    /** ISO-8601 local date in the device time zone at the time of the event. */
    val localDate: String,
    val notes: String? = null,
)
