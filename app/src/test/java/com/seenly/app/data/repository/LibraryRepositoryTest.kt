package com.seenly.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seenly.app.TestSupport
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.domain.model.ManualMediaInput
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.WatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRepositoryTest {

    private lateinit var db: SeenlyDatabase
    private lateinit var repository: LibraryRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TestSupport.database(context)
        repository = LibraryRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun manualInput(title: String = "Manual Title") = ManualMediaInput(
        title = title,
        type = MediaType.TV_SERIES,
        originalTitle = "Original",
        releaseDate = LocalDate.of(2024, 3, 1),
        totalSeasons = 2,
        totalEpisodes = 20,
        runtimeMinutes = 24,
        synopsis = "Synopsis",
        posterUrl = "https://example.invalid/poster.jpg",
        genres = listOf("Drama", "Drama", "Sci-Fi"),
    )

    @Test
    fun `manual entry is trimmed stored with genres and initialized as planned`() = runBlocking {
        val id = (repository.addManual(manualInput("  Manual Title  ")) as Outcome.Success).value

        val media = repository.getMedia(id)
        assertNotNull(media)
        assertEquals("Manual Title", media!!.title)
        assertEquals(MediaType.TV_SERIES, media.type)
        assertEquals(LocalDate.of(2024, 3, 1), media.releaseDate)
        assertEquals(2, media.totalSeasons)
        assertEquals(20, media.totalEpisodes)
        assertEquals(24, media.runtimeMinutes)
        assertEquals(listOf("Drama", "Sci-Fi"), media.genres)
        assertEquals(com.seenly.app.domain.model.ProviderId.MANUAL, media.metadataSource)

        val entry = repository.observeLibrary().first().single()
        assertEquals(WatchStatus.PLAN_TO_WATCH, entry.status)
        assertEquals(20, entry.episodesTotal)
        assertEquals(2, db.seasonDao().getAll().size)
        assertEquals(20, db.episodeDao().getAll().size)
        assertEquals(10, db.episodeDao().getAll().count { it.seasonId == db.seasonDao().getAll().first().id })
    }

    @Test
    fun `manual title uniqueness ignores case and is scoped to type`() = runBlocking {
        repository.addManual(manualInput("Same Name"))

        assertTrue(repository.addManual(manualInput("same name")) is Outcome.Failure)
        assertTrue(
            repository.addManual(manualInput("same name").copy(type = MediaType.MOVIE)) is Outcome.Success,
        )
    }

    @Test
    fun `manual validation rejects empty titles and negative counts`() = runBlocking {
        assertTrue(repository.addManual(manualInput("   ")) is Outcome.Failure)
        assertTrue(
            repository.addManual(manualInput().copy(totalEpisodes = -1)) is Outcome.Failure,
        )
        assertTrue(
            repository.addManual(manualInput().copy(totalSeasons = -1)) is Outcome.Failure,
        )
    }

    @Test
    fun `personal data validation timestamps and clearing work`() = runBlocking {
        val id = (repository.addManual(manualInput()) as Outcome.Success).value

        assertTrue(repository.updatePersonalInfo(id, 7.3, null, false, null) is Outcome.Failure)
        assertTrue(
            repository.updatePersonalInfo(id, 7.5, "  Great  ", true, "  note  ") is Outcome.Success,
        )

        var media = repository.getMedia(id)!!
        assertEquals(7.5, media.personalRating ?: 0.0, 0.0)
        assertEquals("Great", media.review)
        assertEquals("note", media.personalNotes)
        assertTrue(media.reviewIsSpoiler)
        assertNotNull(media.reviewCreatedAt)
        assertNotNull(media.reviewUpdatedAt)

        assertTrue(
            repository.updatePersonalInfo(id, null, null, true, null) is Outcome.Success,
        )
        media = repository.getMedia(id)!!
        assertNull(media.personalRating)
        assertNull(media.review)
        assertFalse(media.reviewIsSpoiler)
        assertNull(media.reviewCreatedAt)
        assertNull(media.reviewUpdatedAt)
    }

    @Test
    fun `local metadata edits are marked and never clear personal data`() = runBlocking {
        val id = (repository.addManual(manualInput()) as Outcome.Success).value
        repository.updatePersonalInfo(id, 9.0, "Review", false, "Notes")

        val result = repository.updateLocalInfo(
            mediaId = id,
            title = "Edited Title",
            type = MediaType.MOVIE,
            totalSeasons = null,
            totalEpisodes = null,
            runtimeMinutes = 95,
        )
        assertTrue(result is Outcome.Success)

        val media = repository.getMedia(id)!!
        assertEquals("Edited Title", media.title)
        assertEquals(MediaType.MOVIE, media.type)
        assertEquals(95, media.runtimeMinutes)
        assertNull(media.totalSeasons)
        assertTrue(db.mediaDao().getById(id)!!.locallyEdited)
        assertEquals(9.0, media.personalRating ?: 0.0, 0.0)
        assertEquals("Review", media.review)
        assertEquals("Notes", media.personalNotes)
    }

    @Test
    fun `deleting a title cascades to seasons episodes progress history and lists`() = runBlocking {
        val lists = ListsRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
        lists.ensureBuiltinLists()
        val id = (repository.addManual(manualInput()) as Outcome.Success).value
        val generated = db.episodeDao().getAll()
        assertTrue(generated.isNotEmpty())
        val seasonId = generated.first().seasonId
        val episodeId = generated.first().id
        val watch = WatchRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
        watch.markEpisodeWatched(episodeId)
        val customList = (lists.createList("Custom", null) as Outcome.Success).value
        lists.addToList(customList, id)

        assertNotNull(db.progressDao().get(id))
        repository.deleteMedia(id)

        assertNull(db.mediaDao().getById(id))
        assertNull(db.seasonDao().getById(seasonId))
        assertNull(db.episodeDao().getById(episodeId))
        assertNull(db.progressDao().get(id))
        assertTrue(db.watchEventDao().allForMedia(id).isEmpty())
        assertTrue(db.listDao().getAllItems().none { it.mediaId == id })
        assertTrue(repository.observeLibrary().first().isEmpty())
    }
}
