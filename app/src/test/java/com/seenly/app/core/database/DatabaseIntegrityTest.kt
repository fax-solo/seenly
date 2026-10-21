package com.seenly.app.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.seenly.app.TestSupport
import com.seenly.app.seedMovie
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.ProviderIdEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseIntegrityTest {

    private lateinit var db: SeenlyDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TestSupport.database(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `database starts at schema version two and enforces foreign keys`() {
        runBlocking {
            assertEquals(2, db.openHelper.readableDatabase.version)

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    db.episodeDao().insert(
                        EpisodeEntity(mediaId = 999, seasonId = 999, number = 1),
                    )
                }
            }
        }
    }

    @Test
    fun `provider ids are unique across the provider and external id pair`() = runBlocking {
        val first = db.seedMovie("First")
        val second = db.seedMovie("Second")
        val link = ProviderIdEntity(
            mediaId = first,
            provider = "CINEMETA",
            externalId = "movie/tt0133093",
            linkedAt = 1,
        )

        assertTrue(db.mediaDao().insertProviderId(link) > 0)
        assertEquals(-1L, db.mediaDao().insertProviderId(link.copy(mediaId = second)))

        val found = db.mediaDao().findProviderId("CINEMETA", "movie/tt0133093")
        assertEquals(first, found?.mediaId)
    }

    @Test
    fun `unique title and type lookup is case insensitive`() = runBlocking {
        db.seedMovie("Case Test")
        assertEquals("Case Test", db.mediaDao().findByTitleAndType("case test", "MOVIE")?.title)
    }
}
