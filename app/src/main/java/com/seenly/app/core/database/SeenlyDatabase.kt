package com.seenly.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.seenly.app.data.local.dao.EpisodeDao
import com.seenly.app.data.local.dao.GoalDao
import com.seenly.app.data.local.dao.ListDao
import com.seenly.app.data.local.dao.MediaDao
import com.seenly.app.data.local.dao.ProgressDao
import com.seenly.app.data.local.dao.SeasonDao
import com.seenly.app.data.local.dao.WatchEventDao
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.GoalEntity
import com.seenly.app.data.local.entity.GenreEntity
import com.seenly.app.data.local.entity.ListItemEntity
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.MediaGenreCrossRef
import com.seenly.app.data.local.entity.MediaListEntity
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.entity.ProviderIdEntity
import com.seenly.app.data.local.entity.RemoteCacheEntity
import com.seenly.app.data.local.entity.SeasonEntity
import com.seenly.app.data.local.entity.WatchEventEntity

/**
 * The single Room database. Local data is the source of truth for everything
 * personal; provider metadata is cached in the same schema.
 *
 * Migration policy: SQLite is additive-only. Never delete or rewrite user data
 * during migrations. Every schema change must ship a Migration in [MIGRATIONS]
 * and a migration test. Destructive fallback is deliberately NOT configured:
 * losing the library is worse than an explicit, visible failure.
 */
@Database(
    entities = [
        MediaEntity::class,
        GenreEntity::class,
        MediaGenreCrossRef::class,
        ProviderIdEntity::class,
        RemoteCacheEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        ProgressEntity::class,
        WatchEventEntity::class,
        MediaListEntity::class,
        ListItemEntity::class,
        GoalEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SeenlyDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun seasonDao(): SeasonDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun progressDao(): ProgressDao
    abstract fun watchEventDao(): WatchEventDao
    abstract fun listDao(): ListDao
    abstract fun goalDao(): GoalDao

    companion object {
        const val NAME = "seenly.db"

        /**
         * v1 -> v2: external ratings (IMDb, Rotten Tomatoes, MyAnimeList). Purely
         * additive columns; existing rows keep null ratings.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN imdbId TEXT")
                db.execSQL("ALTER TABLE media ADD COLUMN imdbRating REAL")
                db.execSQL("ALTER TABLE media ADD COLUMN rottenTomatoesRating INTEGER")
                db.execSQL("ALTER TABLE media ADD COLUMN malRating REAL")
                db.execSQL("ALTER TABLE media ADD COLUMN ratingsUpdatedAt INTEGER")
            }
        }

        /** Registered migrations. Schema v1 is the initial full schema. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context, name: String = NAME): SeenlyDatabase =
            Room.databaseBuilder(context.applicationContext, SeenlyDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
