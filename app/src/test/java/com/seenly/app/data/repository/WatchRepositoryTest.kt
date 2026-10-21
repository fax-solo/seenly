package com.seenly.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seenly.app.TestSupport
import com.seenly.app.seedMovie
import com.seenly.app.seedSeries
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.domain.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchRepositoryTest {

    private lateinit var db: SeenlyDatabase
    private lateinit var repository: WatchRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TestSupport.database(context)
        repository = WatchRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `double tapping an episode creates exactly one completion`() = runBlocking {
        val series = db.seedSeries()

        assertTrue(repository.markEpisodeWatched(series.episodeIds[0]) is Outcome.Success)
        assertTrue(repository.markEpisodeWatched(series.episodeIds[0]) is Outcome.Success)

        assertEquals(1, db.watchEventDao().completedCountForMedia(series.mediaId))
        val summary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(WatchStatus.WATCHING, summary.status)
        assertEquals(1, summary.episodesWatched)
        assertEquals(2, summary.episodesTotal)
        assertEquals(2, summary.nextEpisode?.number)
        assertEquals(TestSupport.clock.today(), summary.startDate)
        assertEquals(1, summary.viewingsCount)
    }

    @Test
    fun `choosing completed marks every known episode watched`() = runBlocking {
        val series = db.seedSeries()

        assertTrue(repository.setStatus(series.mediaId, WatchStatus.COMPLETED) is Outcome.Success)

        assertEquals(2, db.watchEventDao().completedCountForMedia(series.mediaId))
        val summary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(WatchStatus.COMPLETED, summary.status)
        assertEquals(2, summary.episodesWatched)
        assertEquals(2, summary.episodesTotal)
        assertEquals(TestSupport.clock.today(), summary.completionDate)
    }

    @Test
    fun `an upcoming title can move from plan to watching and completed`() = runBlocking {
        val now = TestSupport.clock.nowMillis()
        val mediaId = db.mediaDao().insert(
            MediaEntity(
                type = "ANIME_SERIES",
                title = "Upcoming Anime",
                releaseDate = "2027-01-10",
                addedAt = now,
                lastModifiedAt = now,
            ),
        )
        db.progressDao().upsert(
            ProgressEntity(
                mediaId = mediaId,
                status = WatchStatus.PLAN_TO_WATCH.name,
                updatedAt = now,
            ),
        )

        assertTrue(repository.setStatus(mediaId, WatchStatus.WATCHING) is Outcome.Success)
        var summary = repository.observeProgressSummary(mediaId).first()
        assertEquals(WatchStatus.WATCHING, summary.status)
        assertEquals(0, summary.episodesWatched)
        assertEquals(0, summary.episodesTotal)

        assertTrue(repository.setStatus(mediaId, WatchStatus.COMPLETED) is Outcome.Success)
        summary = repository.observeProgressSummary(mediaId).first()
        assertEquals(WatchStatus.COMPLETED, summary.status)
        assertEquals(TestSupport.clock.today(), summary.completionDate)
    }

    @Test
    fun `bulk completion completes movies and every episode of a series`() = runBlocking {
        val movieId = db.seedMovie("Bulk Movie")
        val series = db.seedSeries("Bulk Series")
        val alreadyDone = db.seedMovie("Already Done")
        repository.setStatus(alreadyDone, WatchStatus.COMPLETED)

        val notices = async(Dispatchers.Unconfined) {
            repository.completions.take(2).toList()
        }
        val result = repository.markAllCompleted(listOf(movieId, series.mediaId, alreadyDone))

        assertEquals(2, (result as Outcome.Success).value)
        assertEquals(WatchStatus.COMPLETED, repository.observeProgressSummary(movieId).first().status)
        val seriesSummary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(WatchStatus.COMPLETED, seriesSummary.status)
        assertEquals(2, seriesSummary.episodesWatched)
        assertEquals(setOf(movieId, series.mediaId), withTimeout(2_000) { notices.await() }.map { it.mediaId }.toSet())
    }

    @Test
    fun `completing the last episode completes the series once`() = runBlocking {
        val series = db.seedSeries()

        repository.markEpisodeWatched(series.episodeIds[0])
        repository.markEpisodeWatched(series.episodeIds[1])
        repository.markEpisodeWatched(series.episodeIds[1])

        val summary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(WatchStatus.COMPLETED, summary.status)
        assertEquals(2, summary.episodesWatched)
        assertNull(summary.nextEpisode)
        assertEquals(TestSupport.clock.today(), summary.completionDate)
        assertEquals(2, summary.viewingsCount)
        assertEquals(2, db.watchEventDao().completedCountForMedia(series.mediaId))
    }

    @Test
    fun `series completes from its episode rows when the provider total is missing`() = runBlocking {
        val series = db.seedSeries()
        val media = db.mediaDao().getById(series.mediaId)!!
        db.mediaDao().update(media.copy(totalEpisodes = null))

        repository.markEpisodeWatched(series.episodeIds[0])
        repository.markEpisodeWatched(series.episodeIds[1])

        val summary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(WatchStatus.COMPLETED, summary.status)
        assertEquals(2, summary.episodesWatched)
    }

    @Test
    fun `finishing a series emits exactly one completion notice`() = runBlocking {
        val series = db.seedSeries()
        val notice = async(Dispatchers.Unconfined) { repository.completions.first() }

        repository.markEpisodeWatched(series.episodeIds[0])
        repository.markEpisodeWatched(series.episodeIds[1])
        repository.markEpisodeWatched(series.episodeIds[1])

        val emitted = withTimeout(2_000) { notice.await() }
        assertEquals(series.mediaId, emitted.mediaId)
        assertEquals("Test Series", emitted.title)
    }

    @Test
    fun `rewatching an episode adds a viewing but not an extra unique episode`() = runBlocking {
        val series = db.seedSeries()
        repository.markEpisodeWatched(series.episodeIds[0])
        repository.markEpisodeWatched(series.episodeIds[1])

        assertTrue(repository.recordRewatch(series.mediaId, series.episodeIds[0]) is Outcome.Success)

        val summary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(2, summary.episodesWatched)
        assertEquals(3, summary.viewingsCount)
        assertEquals(1, summary.rewatchCount)
        assertEquals(WatchStatus.COMPLETED, summary.status)
        assertEquals(3, db.watchEventDao().completedCountForMedia(series.mediaId))
    }

    @Test
    fun `set rewatch count replaces the current rewatch history`() = runBlocking {
        val series = db.seedSeries()
        repository.markEpisodeWatched(series.episodeIds[0])
        repository.markEpisodeWatched(series.episodeIds[1])
        repository.recordRewatch(series.mediaId, series.episodeIds[0])

        assertTrue(repository.setRewatchCount(series.mediaId, 4) is Outcome.Success)

        var summary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(4, summary.rewatchCount)
        assertEquals(2, summary.episodesWatched)
        assertEquals(WatchStatus.COMPLETED, summary.status)

        assertTrue(repository.setRewatchCount(series.mediaId, 0) is Outcome.Success)
        summary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(0, summary.rewatchCount)
        assertEquals(2, summary.viewingsCount)
    }

    @Test
    fun `season watch is idempotent and marks every episode`() = runBlocking {
        val series = db.seedSeries(episodesPerSeason = 3)

        repository.markSeasonWatched(series.seasonIds.single())
        repository.markSeasonWatched(series.seasonIds.single())

        assertEquals(3, db.watchEventDao().completedCountForMedia(series.mediaId))
        val summary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(3, summary.episodesWatched)
        assertEquals(WatchStatus.COMPLETED, summary.status)
    }

    @Test
    fun `watch up to previous episodes is idempotent and excludes the target`() = runBlocking {
        val series = db.seedSeries(seasonCount = 2, episodesPerSeason = 2)
        val target = series.episodeIds[3]

        repository.markPreviousEpisodesWatched(target)
        repository.markPreviousEpisodesWatched(target)

        assertEquals(3, db.watchEventDao().completedCountForMedia(series.mediaId))
        val summary = repository.observeProgressSummary(series.mediaId).first()
        assertEquals(3, summary.episodesWatched)
        assertEquals(series.episodeIds[3], summary.nextEpisode?.id)
        assertEquals(WatchStatus.WATCHING, summary.status)
    }

    @Test
    fun `movie completion is idempotent and rewatching keeps it completed`() = runBlocking {
        val movieId = db.seedMovie()

        repository.markMovieWatched(movieId)
        repository.markMovieWatched(movieId)

        assertEquals(1, db.watchEventDao().completedCountForMedia(movieId))
        var summary = repository.observeProgressSummary(movieId).first()
        assertEquals(WatchStatus.COMPLETED, summary.status)
        assertEquals(TestSupport.clock.today(), summary.completionDate)

        repository.recordRewatch(movieId, null)
        summary = repository.observeProgressSummary(movieId).first()
        assertEquals(2, summary.viewingsCount)
        assertEquals(1, summary.rewatchCount)
        assertEquals(WatchStatus.COMPLETED, summary.status)
        assertEquals(2, db.watchEventDao().completedCountForMedia(movieId))
    }

    @Test
    fun `reset progress removes all history and restores plan to watch`() = runBlocking {
        val movieId = db.seedMovie()
        repository.markMovieWatched(movieId)
        repository.recordRewatch(movieId, null)

        assertTrue(repository.resetProgress(movieId) is Outcome.Success)

        assertEquals(0, db.watchEventDao().completedCountForMedia(movieId))
        val summary = repository.observeProgressSummary(movieId).first()
        assertEquals(WatchStatus.PLAN_TO_WATCH, summary.status)
        assertEquals(0, summary.viewingsCount)
        assertNull(summary.completionDate)
    }

    @Test
    fun `manual status change and dates are persisted`() = runBlocking {
        val movieId = db.seedMovie()

        repository.setStatus(movieId, WatchStatus.WATCHING)
        var summary = repository.observeProgressSummary(movieId).first()
        assertEquals(WatchStatus.WATCHING, summary.status)
        assertEquals(TestSupport.clock.today(), summary.startDate)

        repository.setStatus(movieId, WatchStatus.COMPLETED)
        summary = repository.observeProgressSummary(movieId).first()
        assertEquals(WatchStatus.COMPLETED, summary.status)
        assertEquals(TestSupport.clock.today(), summary.completionDate)
        assertEquals(1, db.watchEventDao().completedCountForMedia(movieId))
    }
}
