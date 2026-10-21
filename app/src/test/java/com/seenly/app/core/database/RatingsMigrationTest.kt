package com.seenly.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RatingsMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SeenlyDatabase::class.java,
    )

    @Test
    fun `migrate 1 to 2 keeps media and adds nullable ratings`() {
        helper.createDatabase(TEST_DB, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO media (
                    id, type, title, alternateTitles, tags, directorNames,
                    castNames, locallyEdited, addedAt, lastModifiedAt, reviewIsSpoiler
                ) VALUES (1, 'MOVIE', 'Before migration', '[]', '[]', '[]', '[]', 0, 10, 10, 0)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            SeenlyDatabase.MIGRATION_1_2,
        )
        migrated.query(
            """
            SELECT title, imdbId, imdbRating, rottenTomatoesRating, malRating, ratingsUpdatedAt
            FROM media WHERE id = 1
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Before migration", cursor.getString(0))
            for (column in 1..5) {
                assertTrue("rating column $column should default to null", cursor.isNull(column))
            }
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "ratings-migration-test.db"
    }
}
