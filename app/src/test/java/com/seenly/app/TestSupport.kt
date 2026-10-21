package com.seenly.app

import android.content.Context
import androidx.room.Room
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.common.FixedAppClock
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.SeasonEntity
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneOffset

object TestSupport {

    val clock = FixedAppClock(
        now = Instant.parse("2026-05-20T12:00:00Z").toEpochMilli(),
        zone = ZoneOffset.UTC,
    )

    val dispatchers = object : AppDispatchers {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    fun database(context: Context): SeenlyDatabase =
        Room.inMemoryDatabaseBuilder(context, SeenlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
}

suspend fun SeenlyDatabase.seedSeries(
    title: String = "Test Series",
    type: String = "TV_SERIES",
    seasonCount: Int = 1,
    episodesPerSeason: Int = 2,
    episodeRuntime: Int? = 25,
): SeededSeries {
    val mediaId = mediaDao().insert(
        MediaEntity(
            type = type,
            title = title,
            totalSeasons = seasonCount,
            totalEpisodes = seasonCount * episodesPerSeason,
            runtimeMinutes = episodeRuntime,
            addedAt = TestSupport.clock.nowMillis(),
            lastModifiedAt = TestSupport.clock.nowMillis(),
        ),
    )
    val seasonIds = mutableListOf<Long>()
    val episodeIds = mutableListOf<Long>()
    repeat(seasonCount) { seasonIndex ->
        val seasonId = seasonDao().insert(
            SeasonEntity(
                mediaId = mediaId,
                number = seasonIndex + 1,
                title = "Season ${seasonIndex + 1}",
                episodeCount = episodesPerSeason,
            ),
        )
        seasonIds += seasonId
        repeat(episodesPerSeason) { episodeIndex ->
            episodeIds += episodeDao().insert(
                EpisodeEntity(
                    mediaId = mediaId,
                    seasonId = seasonId,
                    number = episodeIndex + 1,
                    absoluteNumber = seasonIndex * episodesPerSeason + episodeIndex + 1,
                    title = "Episode ${episodeIndex + 1}",
                    runtimeMinutes = episodeRuntime,
                ),
            )
        }
    }
    return SeededSeries(mediaId, seasonIds, episodeIds)
}

suspend fun SeenlyDatabase.seedMovie(
    title: String = "Test Movie",
    type: String = "MOVIE",
    runtime: Int = 120,
): Long = mediaDao().insert(
    MediaEntity(
        type = type,
        title = title,
        runtimeMinutes = runtime,
        addedAt = TestSupport.clock.nowMillis(),
        lastModifiedAt = TestSupport.clock.nowMillis(),
    ),
)

data class SeededSeries(
    val mediaId: Long,
    val seasonIds: List<Long>,
    val episodeIds: List<Long>,
)
