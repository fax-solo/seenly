package com.seenly.app.data.local.dao

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seenly.app.TestSupport
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.entity.SeasonEntity
import com.seenly.app.data.local.entity.WatchEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogDaoCalendarTest {

    private lateinit var db: SeenlyDatabase

    @Before
    fun setUp() {
        db = TestSupport.database(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `calendar includes every non-dropped status and hides watched episodes`() = runBlocking {
        val now = TestSupport.clock.nowMillis()
        val watching = seedTitle("Watching Anime", "WATCHING", now)
        val completed = seedTitle("Completed Anime", "COMPLETED", now)
        val planned = seedTitle("Planned Anime", "PLAN_TO_WATCH", now)
        val dropped = seedTitle("Dropped Anime", "DROPPED", now)

        val watchedEpisode = seedEpisode(watching.first, watching.second, number = 1, "2026-05-21")
        seedEpisode(watching.first, watching.second, number = 2, "2026-05-22")
        seedEpisode(completed.first, completed.second, number = 1, "2026-05-23")
        seedEpisode(planned.first, planned.second, number = 1, "2026-05-24")
        seedEpisode(dropped.first, dropped.second, number = 1, "2026-05-25")
        db.watchEventDao().insert(
            WatchEventEntity(
                mediaId = watching.first,
                episodeId = watchedEpisode,
                eventType = "FIRST_WATCH",
                startedAt = now,
                completedAt = now,
                localDate = "2026-05-21",
            ),
        )

        val rows = db.episodeDao()
            .observeUpcomingEpisodes("2026-05-20", "2026-06-20", 50)
            .first()

        assertEquals(
            listOf("Watching Anime", "Completed Anime", "Planned Anime"),
            rows.map { it.mediaTitle },
        )
        assertEquals(listOf(2, 1, 1), rows.map { it.episode.number })
    }

    @Test
    fun `upcoming title releases exclude dropped titles`() = runBlocking {
        val now = TestSupport.clock.nowMillis()
        seedTitle(
            "Planned Movie",
            "PLAN_TO_WATCH",
            now,
            releaseDate = "2026-05-22",
        )
        seedTitle(
            "Dropped Movie",
            "DROPPED",
            now,
            releaseDate = "2026-05-23",
        )

        val rows = db.mediaDao().observeUpcomingReleases("2026-05-20", 50).first()

        assertEquals(listOf("Planned Movie"), rows.map { it.media.title })
    }

    private suspend fun seedTitle(
        title: String,
        status: String,
        now: Long,
        releaseDate: String? = null,
    ): Pair<Long, Long> {
        val mediaId = db.mediaDao().insert(
            MediaEntity(
                type = "ANIME",
                title = title,
                releaseDate = releaseDate,
                totalSeasons = 1,
                totalEpisodes = 2,
                addedAt = now,
                lastModifiedAt = now,
            ),
        )
        val seasonId = db.seasonDao().insert(
            SeasonEntity(
                mediaId = mediaId,
                number = 1,
                title = "Season 1",
                episodeCount = 2,
            ),
        )
        db.progressDao().upsert(
            ProgressEntity(
                mediaId = mediaId,
                status = status,
                updatedAt = now,
            ),
        )
        return mediaId to seasonId
    }

    private suspend fun seedEpisode(
        mediaId: Long,
        seasonId: Long,
        number: Int,
        airDate: String,
    ): Long = db.episodeDao().insert(
        EpisodeEntity(
            mediaId = mediaId,
            seasonId = seasonId,
            number = number,
            absoluteNumber = number,
            title = "Episode $number",
            airDate = airDate,
        ),
    )
}
