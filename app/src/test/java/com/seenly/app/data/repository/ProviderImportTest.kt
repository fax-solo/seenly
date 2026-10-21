package com.seenly.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seenly.app.TestSupport
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.domain.model.ManualMediaInput
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.providers.ProviderEpisode
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderLink
import com.seenly.app.domain.providers.ProviderMediaDetails
import com.seenly.app.domain.providers.ProviderSeason
import com.seenly.app.domain.providers.ProviderSeasonBundle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderImportTest {

    private lateinit var db: SeenlyDatabase
    private lateinit var library: LibraryRepositoryImpl
    private lateinit var watch: WatchRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TestSupport.database(context)
        library = LibraryRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
        watch = WatchRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun providerDetails(
        title: String = "Provider Title",
        seasons: List<ProviderSeasonBundle> = listOf(
            ProviderSeasonBundle(
                season = ProviderSeason(number = 1, title = "Season 1", episodeCount = 2),
                episodes = listOf(
                    ProviderEpisode(number = 2, absoluteNumber = 2, title = "Provider Episode 2"),
                ),
            ),
        ),
    ) = ProviderMediaDetails(
        media = ProviderMedia(
            provider = ProviderId.CINEMETA,
            externalId = "series/tt1234567",
            type = MediaType.TV_SERIES,
            title = title,
            synopsis = "Provider synopsis",
            releaseDate = LocalDate.of(2020, 1, 1),
            runtimeMinutes = 30,
            totalSeasons = 1,
            totalEpisodes = 2,
            genres = listOf("Mystery"),
        ),
        seasons = seasons,
        cast = listOf("Actor One"),
        directors = listOf("Director One"),
    )

    @Test
    fun `refresh preserves personal fields and watched episodes`() = runBlocking {
        val mediaId = (library.addManual(
            ManualMediaInput(
                title = "Local Title",
                type = MediaType.TV_SERIES,
                totalSeasons = 1,
                totalEpisodes = 2,
                runtimeMinutes = 25,
            ),
        ) as Outcome.Success).value
        library.updatePersonalInfo(mediaId, 9.5, "My review", true, "My notes")
        val firstEpisode = db.episodeDao().getAll().first { it.number == 1 }
        watch.markEpisodeWatched(firstEpisode.id)

        val importedId = ProviderImport.persistDetails(
            db = db,
            details = providerDetails(),
            now = TestSupport.clock.nowMillis(),
            linkToMediaId = mediaId,
        )

        assertEquals(mediaId, importedId)
        val media = library.getMedia(mediaId)!!
        assertEquals("Provider Title", media.title)
        assertEquals(9.5, media.personalRating ?: 0.0, 0.0)
        assertEquals("My review", media.review)
        assertEquals("My notes", media.personalNotes)
        assertTrue(media.reviewIsSpoiler)

        // The watched episode disappeared from the provider payload but must be
        // retained so history never loses its episode link.
        assertEquals(2, db.episodeDao().countForMedia(mediaId))
        val events = db.watchEventDao().allForMedia(mediaId)
        assertEquals(1, events.size)
        assertEquals(firstEpisode.id, events.single().episodeId)
        assertEquals(WatchStatus.WATCHING, watch.observeProgressSummary(mediaId).first().status)

        assertEquals(1, db.mediaDao().providerIdsFor(mediaId).size)
        assertEquals(1L, db.mediaDao().findProviderId("CINEMETA", "series/tt1234567")?.mediaId)
    }

    @Test
    fun `franchise expansion moves legacy single-season history to the selected season`() = runBlocking {
        val mediaId = (library.addManual(
            ManualMediaInput(
                title = "Single Cour",
                type = MediaType.TV_SERIES,
                totalSeasons = 1,
                totalEpisodes = 2,
            ),
        ) as Outcome.Success).value
        val oldEpisodes = db.episodeDao().getAll().sortedBy { it.number }
        watch.markEpisodeWatched(oldEpisodes[0].id)
        watch.markEpisodeWatched(oldEpisodes[1].id)
        assertEquals(WatchStatus.COMPLETED, watch.observeProgressSummary(mediaId).first().status)

        val details = providerDetails(
            seasons = listOf(1, 2).map { season ->
                ProviderSeasonBundle(
                    season = ProviderSeason(number = season, episodeCount = 2),
                    episodes = (1..2).map { number ->
                        ProviderEpisode(
                            number = number,
                            absoluteNumber = (season - 1) * 2 + number,
                            title = "S$season E$number",
                        )
                    },
                )
            },
        ).copy(selectedSeasonNumber = 2)

        ProviderImport.persistDetails(
            db = db,
            details = details,
            now = TestSupport.clock.nowMillis(),
            linkToMediaId = mediaId,
        )

        val seasonTwo = db.seasonDao().findByNumber(mediaId, 2)!!
        val seasonTwoEpisodeIds = db.episodeDao().idsForSeason(seasonTwo.id).toSet()
        val events = db.watchEventDao().allForMedia(mediaId)
        assertEquals(2, events.size)
        assertTrue(events.all { it.episodeId in seasonTwoEpisodeIds })
        assertEquals(4, db.mediaDao().getById(mediaId)!!.totalEpisodes)
        assertEquals(WatchStatus.WATCHING, watch.observeProgressSummary(mediaId).first().status)
    }

    @Test
    fun `a secondary franchise id resolves to the existing title instead of a duplicate`() = runBlocking {
        val first = providerDetails().copy(
            media = providerDetails().media.copy(
                externalId = "series/tt1111111",
                imdbId = "tt1111111",
                title = "First Cour",
            ),
        )
        val firstId = ProviderImport.persistDetails(
            db = db,
            details = first,
            now = TestSupport.clock.nowMillis(),
        )
        val second = providerDetails().copy(
            media = providerDetails().media.copy(
                externalId = "series/tt2222222",
                imdbId = "tt2222222",
                title = "Second Cour",
            ),
            additionalLinks = listOf(
                ProviderLink(ProviderId.CINEMETA, "series/tt1111111"),
            ),
        )

        val secondId = ProviderImport.persistDetails(
            db = db,
            details = second,
            now = TestSupport.clock.nowMillis(),
        )

        assertEquals(firstId, secondId)
        assertEquals(1, db.mediaDao().getAll().size)
        assertEquals("Second Cour", db.mediaDao().getById(firstId)?.title)
        assertEquals(
            listOf("series/tt1111111", "series/tt2222222"),
            db.mediaDao().providerIdsFor(firstId).map { it.externalId },
        )
    }

    @Test
    fun `repeated refresh is idempotent and locally edited core fields win`() = runBlocking {
        val mediaId = (library.addManual(
            ManualMediaInput(
                title = "Local Title",
                type = MediaType.TV_SERIES,
                totalSeasons = 1,
                totalEpisodes = 2,
            ),
        ) as Outcome.Success).value
        library.updateLocalInfo(
            mediaId = mediaId,
            title = "My Edited Title",
            type = MediaType.TV_SERIES,
            totalSeasons = 1,
            totalEpisodes = 2,
            runtimeMinutes = 25,
        )

        ProviderImport.persistDetails(db, providerDetails(), TestSupport.clock.nowMillis(), mediaId)
        ProviderImport.persistDetails(db, providerDetails(), TestSupport.clock.nowMillis(), mediaId)

        val media = library.getMedia(mediaId)!!
        assertEquals("My Edited Title", media.title)
        assertEquals(25, media.runtimeMinutes)
        assertEquals(1, db.episodeDao().countForMedia(mediaId))
        assertEquals(1, db.mediaDao().providerIdsFor(mediaId).size)
    }
}
