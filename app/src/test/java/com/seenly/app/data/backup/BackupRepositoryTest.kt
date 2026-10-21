package com.seenly.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.seenly.app.TestSupport
import com.seenly.app.core.common.AppJson
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.data.repository.GoalsRepositoryImpl
import com.seenly.app.data.repository.LibraryRepositoryImpl
import com.seenly.app.data.repository.ListsRepositoryImpl
import com.seenly.app.data.repository.WatchRepositoryImpl
import com.seenly.app.domain.model.GoalMetric
import com.seenly.app.domain.model.GoalType
import com.seenly.app.domain.model.ManualMediaInput
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.repository.BackupSummary
import com.seenly.app.domain.repository.ImportMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: SeenlyDatabase
    private lateinit var backup: BackupRepositoryImpl
    private lateinit var library: LibraryRepositoryImpl
    private lateinit var lists: ListsRepositoryImpl
    private lateinit var watch: WatchRepositoryImpl
    private lateinit var goals: GoalsRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = TestSupport.database(context)
        backup = BackupRepositoryImpl(context, db, TestSupport.clock, TestSupport.dispatchers, "1.0.0-test")
        library = LibraryRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
        lists = ListsRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
        watch = WatchRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
        goals = GoalsRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedLibrary(): Long {
        lists.ensureBuiltinLists()
        val mediaId = (library.addManual(
            ManualMediaInput(
                title = "Original",
                type = MediaType.TV_SERIES,
                totalSeasons = 1,
                totalEpisodes = 2,
                runtimeMinutes = 25,
                genres = listOf("Drama"),
            ),
        ) as Outcome.Success).value
        library.updatePersonalInfo(mediaId, 8.5, "Loved it", false, "Private note")
        val ep1 = db.episodeDao().getAll().first { it.number == 1 }.id
        watch.markEpisodeWatched(ep1)
        lists.toggleFavorite(mediaId)
        goals.saveGoal(GoalType.WEEKLY, GoalMetric.EPISODES, 3)
        return mediaId
    }

    private fun backupFile(name: String = "seenly-test-backup.json"): File {
        val file = File(context.cacheDir, name)
        file.delete()
        return file
    }

    private suspend fun exportTo(file: File): BackupSummary {
        val outcome = backup.export(Uri.fromFile(file))
        assertTrue("export failed: $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }

    @Test
    fun `export writes a complete versioned json file`() = runBlocking {
        seedLibrary()
        val file = backupFile()

        val summary = exportTo(file)

        assertTrue(file.isFile)
        assertTrue(file.length() > 0)
        assertEquals(1, summary.mediaCount)
        assertEquals(2, summary.episodeCount)
        assertEquals(1, summary.eventCount)
        assertEquals(2, summary.listCount)
        assertEquals(1, summary.goalCount)
        assertEquals(2, summary.schemaVersion)

        val decoded = AppJson.decodeFromString<BackupFile>(file.readText())
        assertEquals(2, decoded.schemaVersion)
        assertEquals("1.0.0-test", decoded.appVersion)
        assertEquals("Original", decoded.media.single().title)
        assertEquals(8.5, decoded.media.single().personalRating ?: 0.0, 0.0)
        assertFalse(file.readText().contains("tmdb_api_key", ignoreCase = true))
    }

    @Test
    fun `merge restores deleted data and is idempotent`() = runBlocking {
        val originalId = seedLibrary()
        val file = backupFile()
        exportTo(file)

        library.deleteMedia(originalId)
        val extraId = (library.addManual(ManualMediaInput("Extra", MediaType.MOVIE)) as Outcome.Success).value
        assertTrue(library.observeLibrary().first().none { it.media.title == "Original" })

        val first = backup.import(Uri.fromFile(file), ImportMode.MERGE)
        assertTrue("merge failed: $first", first is Outcome.Success)
        val firstSummary = (first as Outcome.Success).value
        assertEquals(1, firstSummary.mediaCount)
        assertEquals(2, firstSummary.episodeCount)
        assertEquals(1, firstSummary.eventCount)

        var entries = library.observeLibrary().first()
        assertEquals(setOf("Original", "Extra"), entries.map { it.media.title }.toSet())
        val restored = entries.single { it.media.title == "Original" }
        assertEquals(1, restored.episodesWatched)
        assertEquals(2, restored.episodesTotal)
        assertEquals(8.5, restored.media.personalRating ?: 0.0, 0.0)
        assertEquals("Loved it", restored.media.review)
        assertEquals("Private note", restored.media.personalNotes)
        assertNotNull(extraId)
        assertEquals(1, lists.observeFavorites().first().size)
        assertEquals(1, goals.observeGoals().first().size)

        val countsBefore = Snapshot(
            media = db.mediaDao().getAll().size,
            episodes = db.episodeDao().getAll().size,
            events = db.watchEventDao().getAll().size,
            items = db.listDao().getAllItems().size,
            goals = db.goalDao().getAll().size,
        )

        val second = backup.import(Uri.fromFile(file), ImportMode.MERGE)
        assertTrue("second merge failed: $second", second is Outcome.Success)
        val secondSummary = (second as Outcome.Success).value
        assertEquals(0, secondSummary.mediaCount)
        assertEquals(0, secondSummary.episodeCount)
        assertEquals(0, secondSummary.eventCount)

        assertEquals(countsBefore, Snapshot(
            media = db.mediaDao().getAll().size,
            episodes = db.episodeDao().getAll().size,
            events = db.watchEventDao().getAll().size,
            items = db.listDao().getAllItems().size,
            goals = db.goalDao().getAll().size,
        ))
    }

    @Test
    fun `replace removes current-only data and preserves backup ids and relationships`() = runBlocking {
        val originalId = seedLibrary()
        val file = backupFile()
        exportTo(file)

        val extraId = (library.addManual(ManualMediaInput("Only Local", MediaType.MOVIE)) as Outcome.Success).value
        watch.markMovieWatched(extraId)
        val extraList = (lists.createList("Only List", null) as Outcome.Success).value
        lists.addToList(extraList, extraId)
        goals.saveGoal(GoalType.MONTHLY, GoalMetric.MOVIES, 4)

        val result = backup.import(Uri.fromFile(file), ImportMode.REPLACE)
        assertTrue("replace failed: $result", result is Outcome.Success)

        val entries = library.observeLibrary().first()
        assertEquals(listOf("Original"), entries.map { it.media.title })
        assertEquals(originalId, entries.single().media.id)
        assertEquals(8.5, entries.single().media.personalRating ?: 0.0, 0.0)
        assertEquals(1, db.watchEventDao().getAll().size)
        assertEquals(2, db.episodeDao().getAll().size)
        assertEquals(2, db.listDao().getAllLists().size)
        assertEquals(1, db.goalDao().getAll().size)
        assertTrue(lists.observeLists().first().none { it.name == "Only List" })
    }

    @Test
    fun `invalid or newer backups fail without touching existing data`() = runBlocking {
        seedLibrary()
        val extraId = (library.addManual(ManualMediaInput("Keep Me", MediaType.MOVIE)) as Outcome.Success).value
        watch.markMovieWatched(extraId)
        val before = Snapshot(
            media = db.mediaDao().getAll().size,
            episodes = db.episodeDao().getAll().size,
            events = db.watchEventDao().getAll().size,
            items = db.listDao().getAllItems().size,
            goals = db.goalDao().getAll().size,
        )

        val corrupt = backupFile("corrupt.json")
        corrupt.writeText("{ definitely not json")
        val corruptResult = backup.import(Uri.fromFile(corrupt), ImportMode.REPLACE)
        assertTrue(corruptResult is Outcome.Failure)
        assertEquals(before, Snapshot(
            media = db.mediaDao().getAll().size,
            episodes = db.episodeDao().getAll().size,
            events = db.watchEventDao().getAll().size,
            items = db.listDao().getAllItems().size,
            goals = db.goalDao().getAll().size,
        ))

        val validFile = backupFile("valid.json")
        exportTo(validFile)
        val newer = backupFile("newer.json")
        val upgraded = AppJson.decodeFromString<BackupFile>(validFile.readText())
            .copy(schemaVersion = 99)
        newer.writeText(AppJson.encodeToString(upgraded))
        val newerResult = backup.import(Uri.fromFile(newer), ImportMode.REPLACE)
        assertTrue(newerResult is Outcome.Failure)
        assertEquals(before, Snapshot(
            media = db.mediaDao().getAll().size,
            episodes = db.episodeDao().getAll().size,
            events = db.watchEventDao().getAll().size,
            items = db.listDao().getAllItems().size,
            goals = db.goalDao().getAll().size,
        ))
    }

    private data class Snapshot(
        val media: Int,
        val episodes: Int,
        val events: Int,
        val items: Int,
        val goals: Int,
    )
}
