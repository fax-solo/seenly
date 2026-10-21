package com.seenly.app.domain.statistics

import com.seenly.app.domain.model.EventType
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.Media
import com.seenly.app.domain.model.MediaCategory
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.statistics.CompletedViewingTestFactory.viewing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatisticsEngineTest {

    private val today = LocalDate.of(2026, 5, 20)

    private fun media(
        id: Long,
        type: MediaType,
        title: String = "Title $id",
        rating: Double? = null,
    ) = Media(id = id, type = type, title = title, personalRating = rating)

    private fun entry(
        id: Long,
        type: MediaType,
        status: WatchStatus,
        rating: Double? = null,
        lastActivity: Long? = null,
        episodesTotal: Int = 0,
    ) = LibraryEntry(
        media = media(id, type, rating = rating),
        status = status,
        episodesTotal = episodesTotal,
        lastActivityAt = lastActivity,
    )

    private fun fixture(): StatisticsInput {
        val library = listOf(
            entry(1, MediaType.ANIME_SERIES, WatchStatus.COMPLETED, 9.0, lastActivity = 1_000, episodesTotal = 12),
            entry(2, MediaType.MOVIE, WatchStatus.COMPLETED, 5.0, lastActivity = 5_000),
            entry(3, MediaType.MOVIE, WatchStatus.PLAN_TO_WATCH, 8.0),
            entry(4, MediaType.TV_SERIES, WatchStatus.WATCHING, episodesTotal = 24),
            entry(5, MediaType.CARTOON_MOVIE, WatchStatus.COMPLETED, lastActivity = 4_000),
            entry(6, MediaType.DOCUMENTARY_MOVIE, WatchStatus.COMPLETED, 7.0, lastActivity = 3_000),
        )
        val viewings = listOf(
            viewing(1, MediaType.ANIME_SERIES, 101, today.minusDays(2), 25, EventType.FIRST_WATCH),
            viewing(1, MediaType.ANIME_SERIES, 101, today.minusDays(1), 25, EventType.REWATCH),
            viewing(1, MediaType.ANIME_SERIES, 102, today, 25, EventType.FIRST_WATCH),
            viewing(2, MediaType.MOVIE, null, today, null, EventType.FIRST_WATCH),
            // Future-dated events must not affect any statistic.
            viewing(1, MediaType.ANIME_SERIES, 103, today.plusDays(2), 25, EventType.FIRST_WATCH),
        )
        val genres = mapOf(
            1L to listOf("Action", "Fantasy", "Action"),
            2L to listOf("Action"),
            3L to listOf("Action"),
            4L to listOf("Drama"),
            5L to listOf("Comedy"),
            6L to listOf("Nature"),
        )
        return StatisticsInput(library = library, viewings = viewings, genresByMedia = genres)
    }

    @Test
    fun `completed titles are counted once per library entry`() {
        val snapshot = StatisticsEngine.calculate(fixture(), today)

        assertEquals(1, snapshot.completedAnime)
        assertEquals(0, snapshot.completedSeries)
        assertEquals(2, snapshot.completedMovies)
        assertEquals(1, snapshot.completedCartoons)
        assertEquals(1, snapshot.completedDocumentaries)
        assertEquals(6, snapshot.totalLibraryTitles)
        assertEquals(36, snapshot.totalKnownEpisodes)
    }

    @Test
    fun `rewatches count as viewings but never as extra episodes`() {
        val snapshot = StatisticsEngine.calculate(fixture(), today)

        assertEquals(2, snapshot.completedEpisodes)
        assertEquals(3, snapshot.episodeViewings)
        assertEquals(1, snapshot.movieViewings)
        assertEquals(4, snapshot.totalViewings)
    }

    @Test
    fun `runtime totals use known runtimes only and report unknowns`() {
        val snapshot = StatisticsEngine.calculate(fixture(), today)

        assertEquals(75L, snapshot.estimatedMinutes)
        assertEquals(1.25, snapshot.estimatedHours, 0.0001)
        assertEquals(1, snapshot.viewingsWithUnknownRuntime)
    }

    @Test
    fun `future events are excluded from activity and trends`() {
        val snapshot = StatisticsEngine.calculate(fixture(), today)

        assertEquals(today, snapshot.lastActivityDate)
        assertEquals(today.minusDays(2), snapshot.firstActivityDate)
        assertEquals(mapOf(today.minusDays(2) to 1, today.minusDays(1) to 1, today to 2), snapshot.dailyActivity)
        assertEquals(1, snapshot.monthly.size)
        assertEquals(2026, snapshot.monthly.single().year)
        assertEquals(5, snapshot.monthly.single().month)
        assertEquals(75L, snapshot.monthly.single().knownMinutes)
        assertEquals(3, snapshot.monthly.single().episodeViewings)
        assertEquals(1, snapshot.monthly.single().movieViewings)
        assertEquals(1, snapshot.monthly.single().unknownRuntimeViewings)
    }

    @Test
    fun `streaks use distinct consecutive local dates`() {
        val snapshot = StatisticsEngine.calculate(fixture(), today)

        assertEquals(3, snapshot.currentStreak)
        assertEquals(3, snapshot.longestStreak)
    }

    @Test
    fun `ratings ignore unrated titles and sort highest first`() {
        val snapshot = StatisticsEngine.calculate(fixture(), today)

        assertEquals(7.25, snapshot.averageRating ?: 0.0, 0.0)
        assertEquals(4, snapshot.ratedCount)
        assertEquals(listOf(1L, 3L, 6L, 2L), snapshot.highestRated.map { it.mediaId })
    }

    @Test
    fun `genres are counted from completed titles only and deduplicated per title`() {
        val snapshot = StatisticsEngine.calculate(fixture(), today)

        assertEquals(
            listOf(NameCount("Action", 2), NameCount("Comedy", 1), NameCount("Fantasy", 1), NameCount("Nature", 1)),
            snapshot.genreDistribution,
        )
        assertEquals(
            listOf(
                CategoryCount(MediaCategory.ANIME, 1),
                CategoryCount(MediaCategory.MOVIES, 2),
                CategoryCount(MediaCategory.CARTOONS, 1),
            ),
            snapshot.categoryDistribution,
        )
    }

    @Test
    fun `recently completed is ordered by last activity`() {
        val snapshot = StatisticsEngine.calculate(fixture(), today)

        assertEquals(listOf(2L, 5L, 6L, 1L), snapshot.recentlyCompleted.map { it.mediaId })
        assertNull(snapshot.recentlyCompleted.first { it.mediaId == 1L }.completedOn)
    }

    @Test
    fun `empty input produces an honest empty snapshot`() {
        val snapshot = StatisticsEngine.calculate(StatisticsInput(), today)

        assertTrue(!snapshot.hasData)
        assertEquals(0, snapshot.totalViewings)
        assertEquals(0L, snapshot.estimatedMinutes)
        assertNull(snapshot.averageRating)
        assertTrue(snapshot.dailyActivity.isEmpty())
    }

    @Test
    fun `current streak survives a missing today but not a two day gap`() {
        val alive = StreakCalculator.compute(
            listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1)),
            today,
        )
        assertEquals(3, alive.current)
        assertEquals(3, alive.longest)

        val stale = StreakCalculator.compute(listOf(today.minusDays(3), today.minusDays(2)), today)
        assertEquals(0, stale.current)
        assertEquals(2, stale.longest)

        assertEquals(StreakCalculator.Streaks(0, 0), StreakCalculator.compute(emptyList(), today))
    }
}

internal object CompletedViewingTestFactory {
    fun viewing(
        mediaId: Long,
        mediaType: MediaType,
        episodeId: Long?,
        date: LocalDate,
        minutes: Int?,
        eventType: EventType = EventType.FIRST_WATCH,
    ) = CompletedViewing(
        mediaId = mediaId,
        mediaType = mediaType,
        episodeId = episodeId,
        eventType = eventType,
        localDate = date,
        minutes = minutes,
    )
}
