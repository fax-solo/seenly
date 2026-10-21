package com.seenly.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** User-created and built-in lists. Deleting a list never deletes titles. */
@Entity(
    tableName = "media_list",
    indices = [Index(value = ["name"], unique = true)],
)
data class MediaListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
    val sortOrder: Int = 0,
    val isBuiltin: Boolean = false,
    /** Non-null for smart lists computed from data (e.g. PLAN_TO_WATCH). */
    val smartFilter: String? = null,
    val coverUrl: String? = null,
)

/** Ordered membership of titles in a list. Duplicate prevention is the PK. */
@Entity(
    tableName = "list_item",
    primaryKeys = ["listId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = MediaListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaId"), Index("listId")],
)
data class ListItemEntity(
    val listId: Long,
    val mediaId: Long,
    val position: Int,
    val addedAt: Long,
)

/** Viewing goals (weekly / monthly / yearly). */
@Entity(tableName = "goal", indices = [Index("active")])
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val metric: String,
    val target: Int,
    val active: Boolean = true,
    val createdAt: Long,
)
