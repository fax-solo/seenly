package com.seenly.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seenly.app.TestSupport
import com.seenly.app.seedMovie
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.domain.model.SmartList
import com.seenly.app.domain.model.WatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListsRepositoryTest {

    private lateinit var db: SeenlyDatabase
    private lateinit var repository: ListsRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TestSupport.database(context)
        repository = ListsRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
        repository.ensureBuiltinLists()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `built-in lists are created once`() = runBlocking {
        repository.ensureBuiltinLists()
        val lists = repository.observeLists().first()

        assertEquals(2, lists.size)
        assertEquals(SmartList.FAVORITES, lists.single { it.smartFilter == SmartList.FAVORITES }.smartFilter)
        assertEquals(SmartList.PLAN_TO_WATCH, lists.single { it.smartFilter == SmartList.PLAN_TO_WATCH }.smartFilter)
        assertTrue(lists.all { it.isBuiltin })
    }

    @Test
    fun `custom list names are trimmed unique and validated`() = runBlocking {
        val id = (repository.createList("  Sci-Fi  ", "Space") as Outcome.Success).value
        val lists = repository.observeLists().first()
        assertEquals("Sci-Fi", lists.single { it.id == id }.name)
        assertEquals("Space", lists.single { it.id == id }.description)

        assertTrue(repository.createList("sci-fi", null) is Outcome.Failure)
        assertTrue(repository.createList("   ", null) is Outcome.Failure)
        assertTrue(repository.createList("x".repeat(61), null) is Outcome.Failure)
    }

    @Test
    fun `adding the same title twice does not duplicate membership`() = runBlocking {
        val movieId = db.seedMovie()
        val listId = (repository.createList("Watch later", null) as Outcome.Success).value

        repository.addToList(listId, movieId)
        repository.addToList(listId, movieId)

        assertEquals(1, repository.observeListItems(listId).first().size)
        assertEquals(setOf(listId), repository.observeMembership(movieId).first())
        assertEquals(1, db.listDao().getAllItems().size)
    }

    @Test
    fun `reordering only affects members and keeps a deterministic order`() = runBlocking {
        val first = db.seedMovie("First")
        val second = db.seedMovie("Second")
        val outsider = db.seedMovie("Outsider")
        val listId = (repository.createList("Ordered", null) as Outcome.Success).value
        repository.addToList(listId, first)
        repository.addToList(listId, second)

        repository.reorder(listId, listOf(second, outsider, first, second))

        assertEquals(listOf(second, first), db.listDao().orderedMediaIds(listId))
        assertEquals(listOf("Second", "First"), repository.observeListItems(listId).first().map { it.media.title })
        assertNotNull(db.mediaDao().getById(outsider))
    }

    @Test
    fun `deleting a list removes memberships but never titles`() = runBlocking {
        val movieId = db.seedMovie()
        val listId = (repository.createList("Temporary", null) as Outcome.Success).value
        repository.addToList(listId, movieId)

        assertTrue(repository.deleteList(listId) is Outcome.Success)
        assertNotNull(db.mediaDao().getById(movieId))
        assertTrue(db.listDao().getAllItems().isEmpty())
        assertTrue(repository.observeLists().first().none { it.id == listId })
    }

    @Test
    fun `built-in lists cannot be deleted or renamed`() = runBlocking {
        val favorites = repository.observeLists().first().single { it.smartFilter == SmartList.FAVORITES }

        assertTrue(repository.deleteList(favorites.id) is Outcome.Failure)
        assertTrue(repository.updateList(favorites.id, "Renamed", null) is Outcome.Success)
        assertEquals(favorites.name, repository.observeLists().first().single { it.id == favorites.id }.name)
    }

    @Test
    fun `favorites toggle both ways and appear in the favorites feed`() = runBlocking {
        val movieId = db.seedMovie()

        assertEquals(true, (repository.toggleFavorite(movieId) as Outcome.Success).value)
        assertEquals(listOf(movieId), repository.observeFavorites().first().map { it.media.id })
        assertEquals(true, repository.observeMembership(movieId).first().isNotEmpty())

        assertEquals(false, (repository.toggleFavorite(movieId) as Outcome.Success).value)
        assertTrue(repository.observeFavorites().first().isEmpty())
        assertFalse(repository.observeMembership(movieId).first().isNotEmpty())
    }

    @Test
    fun `plan to watch is computed from status and is read only`() = runBlocking {
        val movieId = db.seedMovie()
        val watch = WatchRepositoryImpl(db, TestSupport.clock, TestSupport.dispatchers)
        watch.markMovieWatched(movieId)
        val plan = repository.observeLists().first().single { it.smartFilter == SmartList.PLAN_TO_WATCH }

        repository.addToList(plan.id, movieId)
        val entries = repository.observeListItems(plan.id).first()
        assertEquals(listOf(movieId), entries.map { it.media.id })
        assertEquals(WatchStatus.PLAN_TO_WATCH, entries.single().status)

        repository.removeFromList(plan.id, movieId)
        assertEquals(listOf(movieId), repository.observeListItems(plan.id).first().map { it.media.id })
    }
}
