package com.seenly.app.domain.recommendation

import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.Media
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecommendationEngineTest {

    private fun entry(
        id: Long,
        status: WatchStatus,
        rating: Double? = null,
        addedAt: Long = id,
    ) = LibraryEntry(
        media = Media(
            id = id,
            type = MediaType.TV_SERIES,
            title = "Title $id",
            personalRating = rating,
            addedAt = addedAt,
        ),
        status = status,
    )

    private val library = listOf(
        entry(1, WatchStatus.COMPLETED, rating = 9.0, addedAt = 1),
        entry(2, WatchStatus.PLAN_TO_WATCH, addedAt = 100),
        entry(3, WatchStatus.PLAN_TO_WATCH, addedAt = 50),
        entry(4, WatchStatus.ON_HOLD, addedAt = 25),
        entry(5, WatchStatus.WATCHING, addedAt = 80),
    )

    private val genres = mapOf(
        1L to listOf("Action", "Drama"),
        2L to listOf("Action"),
        3L to listOf("Comedy"),
        4L to listOf("Action", "Comedy"),
        5L to listOf("Action"),
    )

    @Test
    fun `rated genres rank unstarted candidates and provide a reason`() {
        val recommendations = RecommendationEngine.recommend(library, genres)

        assertEquals(listOf(2L, 4L, 3L), recommendations.map { it.entry.media.id })
        assertEquals("Action", recommendations[0].reasonGenre)
        assertEquals("Action", recommendations[1].reasonGenre)
        assertNull(recommendations[2].reasonGenre)
        assertEquals(4.0, recommendations[0].score, 0.0)
        assertEquals(0.0, recommendations[2].score, 0.0)
    }

    @Test
    fun `completed and actively watched titles are never recommendations`() {
        val ids = RecommendationEngine.recommend(library, genres).map { it.entry.media.id }.toSet()

        assertEquals(setOf(2L, 3L, 4L), ids)
    }

    @Test
    fun `empty profile returns newest saved titles without fabricated scores`() {
        val unrated = library.map { entry ->
            entry.copy(
                status = WatchStatus.PLAN_TO_WATCH,
                media = entry.media.copy(personalRating = null),
            )
        }
        val recommendations = RecommendationEngine.recommend(unrated, genres)

        assertEquals(listOf(2L, 5L, 3L, 4L, 1L), recommendations.map { it.entry.media.id })
        assertTrue(recommendations.all { it.score == 0.0 && it.reasonGenre == null })
    }

    @Test
    fun `favorites influence the profile even before completion`() {
        val recommendations = RecommendationEngine.recommend(
            library = library,
            genresByMedia = genres,
            favoriteIds = setOf(3L),
        )
        // Comedy now has a positive weight, so title 4 gets a score and outranks title 3 only by score.
        assertEquals(setOf(2L, 3L, 4L), recommendations.map { it.entry.media.id }.toSet())
        assertEquals(12.0, recommendations.first { it.entry.media.id == 4L }.score, 0.0)
    }

    @Test
    fun `limit is respected and no candidates yields an empty list`() {
        assertEquals(1, RecommendationEngine.recommend(library, genres, limit = 1).size)
        assertTrue(
            RecommendationEngine.recommend(
                library.filter { it.status == WatchStatus.COMPLETED },
                genres,
            ).isEmpty(),
        )
    }

    @Test
    fun `random picker is seed-deterministic and constrained to the chosen statuses`() {
        val first = RecommendationEngine.randomPick(library, random = Random(42))
        val second = RecommendationEngine.randomPick(library, random = Random(42))

        assertEquals(first?.media?.id, second?.media?.id)
        assertTrue(first?.status in setOf(WatchStatus.PLAN_TO_WATCH))
        assertNull(RecommendationEngine.randomPick(library.filter { it.status == WatchStatus.WATCHING }))
    }
}
