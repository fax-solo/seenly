package com.seenly.app.domain.recommendation

import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.WatchStatus
import kotlin.random.Random

data class Recommendation(
    val entry: LibraryEntry,
    val score: Double,
    /** The genre that contributed most to the score, or null for non-personalised. */
    val reasonGenre: String?,
)

/**
 * Deterministic, local-only recommendations.
 *
 * The user profile is derived from: personal ratings (centred around 5, so a 9 is
 * +4 and a 2 is -3), completed titles (+3) and favorites (+4). Candidate titles
 * are saved titles that are not yet started (PLAN_TO_WATCH) or paused (ON_HOLD).
 * Genres seen only in low-rated titles get negative weight.
 *
 * When the profile is empty (no ratings/completions/favourites), the newest saved
 * titles are returned without a personalised reason, never fabricated scores.
 */
object RecommendationEngine {

    private const val COMPLETED_BOOST = 3.0
    private const val FAVORITE_BOOST = 4.0

    fun recommend(
        library: List<LibraryEntry>,
        genresByMedia: Map<Long, List<String>>,
        favoriteIds: Set<Long> = emptySet(),
        limit: Int = 20,
    ): List<Recommendation> {
        if (library.isEmpty()) return emptyList()
        val genreWeights = mutableMapOf<String, Double>()
        library.forEach { entry ->
            val genres = genresByMedia[entry.media.id].orEmpty().filter { it.isNotBlank() }
            if (genres.isEmpty()) return@forEach
            val base = when {
                entry.media.personalRating != null -> (entry.media.personalRating - 5.0)
                entry.status == WatchStatus.COMPLETED -> COMPLETED_BOOST
                entry.media.id in favoriteIds -> FAVORITE_BOOST
                else -> 0.0
            }
            if (entry.media.id in favoriteIds) {
                // Favorites count even when they are not completed yet.
                genres.forEach { genre -> genreWeights[genre] = (genreWeights[genre] ?: 0.0) + FAVORITE_BOOST }
            }
            if (base != 0.0) {
                genres.forEach { genre -> genreWeights[genre] = (genreWeights[genre] ?: 0.0) + base }
            }
        }

        val candidates = library.filter {
            it.status == WatchStatus.PLAN_TO_WATCH || it.status == WatchStatus.ON_HOLD
        }
        if (candidates.isEmpty()) return emptyList()

        if (genreWeights.values.all { it == 0.0 } && genreWeights.isEmpty()) {
            return candidates
                .sortedWith(compareByDescending<LibraryEntry> { it.media.addedAt }.thenBy { it.media.title })
                .take(limit)
                .map { Recommendation(entry = it, score = 0.0, reasonGenre = null) }
        }

        return candidates
            .map { entry ->
                val genres = genresByMedia[entry.media.id].orEmpty()
                val contributions = genres.associateWith { genreWeights[it] ?: 0.0 }
                val score = contributions.values.sum()
                val reason = contributions.entries
                    .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
                    .firstOrNull { it.value > 0.0 }
                    ?.key
                Recommendation(entry = entry, score = score, reasonGenre = reason)
            }
            .sortedWith(
                compareByDescending<Recommendation> { it.score }
                    .thenByDescending { it.entry.media.addedAt }
                    .thenBy { it.entry.media.title },
            )
            .take(limit)
    }

    /**
     * Random picker over a chosen set (watchlist by default). Deterministic when
     * supplied a seeded [Random] so behaviour is unit-testable.
     */
    fun randomPick(
        library: List<LibraryEntry>,
        statuses: Set<WatchStatus> = setOf(WatchStatus.PLAN_TO_WATCH),
        random: Random = Random.Default,
    ): LibraryEntry? {
        val candidates = library.filter { it.status in statuses }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }
}
