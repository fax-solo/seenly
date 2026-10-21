package com.seenly.app.domain.statistics

import com.seenly.app.domain.model.EventType
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.MediaCategory
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.WatchStatus
import java.time.LocalDate
import java.time.YearMonth

/** One completed viewing event, already joined with runtime data. */
data class CompletedViewing(
    val mediaId: Long,
    val mediaType: MediaType,
    val episodeId: Long?,
    val eventType: EventType,
    val localDate: LocalDate,
    /** Episode runtime when known, else title runtime; null means unknown. */
    val minutes: Int?,
)

data class StatisticsInput(
    val library: List<LibraryEntry> = emptyList(),
    val viewings: List<CompletedViewing> = emptyList(),
    val genresByMedia: Map<Long, List<String>> = emptyMap(),
)

data class MonthlyViewing(
    val year: Int,
    val month: Int,
    val knownMinutes: Long,
    val episodeViewings: Int,
    val movieViewings: Int,
    val unknownRuntimeViewings: Int,
)

data class YearActivity(
    val year: Int,
    val episodeViewings: Int,
    val movieViewings: Int,
    val knownMinutes: Long,
)

data class NameCount(val name: String, val count: Int)

data class CategoryCount(val category: MediaCategory, val count: Int)

data class RatedTitle(
    val mediaId: Long,
    val title: String,
    val posterUrl: String?,
    val mediaType: MediaType,
    val rating: Double,
)

data class CompletedTitle(
    val mediaId: Long,
    val title: String,
    val posterUrl: String?,
    val mediaType: MediaType,
    val completedOn: LocalDate?,
    val lastActivity: LocalDate?,
)

data class StatisticsSnapshot(
    val completedMovies: Int = 0,
    val completedSeries: Int = 0,
    val completedAnime: Int = 0,
    val completedCartoons: Int = 0,
    val completedDocumentaries: Int = 0,
    val completedEpisodes: Int = 0,
    val episodeViewings: Int = 0,
    val movieViewings: Int = 0,
    val totalViewings: Int = 0,
    val estimatedMinutes: Long = 0,
    val viewingsWithUnknownRuntime: Int = 0,
    val monthly: List<MonthlyViewing> = emptyList(),
    val yearly: List<YearActivity> = emptyList(),
    val dailyActivity: Map<LocalDate, Int> = emptyMap(),
    val genreDistribution: List<NameCount> = emptyList(),
    val categoryDistribution: List<CategoryCount> = emptyList(),
    val averageRating: Double? = null,
    val ratedCount: Int = 0,
    val highestRated: List<RatedTitle> = emptyList(),
    val recentlyCompleted: List<CompletedTitle> = emptyList(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalLibraryTitles: Int = 0,
    val totalKnownEpisodes: Int = 0,
    val firstActivityDate: LocalDate? = null,
    val lastActivityDate: LocalDate? = null,
) {
    val hasData: Boolean get() = totalViewings > 0 || totalLibraryTitles > 0

    /** Hours with one decimal, based on known runtimes only. */
    val estimatedHours: Double get() = estimatedMinutes / 60.0
}

object StreakCalculator {

    data class Streaks(val current: Int, val longest: Int)

    /**
     * A streak is the number of consecutive local calendar dates with at least
     * one qualifying completed viewing. The current streak is still alive if the
     * most recent activity was today or yesterday.
     */
    fun compute(dates: Collection<LocalDate>, today: LocalDate): Streaks {
        val distinct = dates.filter { !it.isAfter(today) }.distinct().sorted()
        if (distinct.isEmpty()) return Streaks(0, 0)

        var longest = 1
        var run = 1
        for (i in 1 until distinct.size) {
            run = if (distinct[i] == distinct[i - 1].plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
        }

        val set = distinct.toSet()
        val last = distinct.last()
        val anchor = when {
            set.contains(today) -> today
            set.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return Streaks(current = 0, longest = longest)
        }
        var current = 0
        var cursor = anchor
        while (set.contains(cursor)) {
            current += 1
            cursor = cursor.minusDays(1)
        }
        return Streaks(current = current, longest = longest)
    }
}

/**
 * Statistics are computed from actual local records. Documented rules:
 *
 * - Completed movies/series/anime/cartoons count unique titles whose status is
 *   COMPLETED (not viewings).
 * - Completed episodes count DISTINCT completed episodes; episode viewings count
 *   every completed viewing event, so rewatches never inflate unique episodes.
 * - Estimated viewing time sums only known runtimes. Viewings with unknown
 *   runtime are counted and labelled separately; they are never silently 0.
 * - Average rating ignores unrated titles. Genre stats ignore unknown genres.
 * - Only activity dated today or earlier is counted; future-dated events are
 *   ignored in activity/trend/streak calculations.
 * - All periods are local calendar periods.
 */
object StatisticsEngine {

    fun calculate(input: StatisticsInput, today: LocalDate): StatisticsSnapshot {
        val viewings = input.viewings.filter { !it.localDate.isAfter(today) }
        val library = input.library
        val completed = library.filter { it.status == WatchStatus.COMPLETED }

        val completedMovies = completed.count { it.media.type.category == MediaCategory.MOVIES }
        val completedSeries = completed.count { it.media.type.category == MediaCategory.SERIES }
        val completedAnime = completed.count { it.media.type.category == MediaCategory.ANIME }
        val completedCartoons = completed.count { it.media.type.category == MediaCategory.CARTOONS }
        val completedDocumentaries = completed.count { it.media.type.isDocumentary }

        val knownRuntimeViewings = viewings.filter { (it.minutes ?: 0) > 0 }
        val estimatedMinutes = knownRuntimeViewings.sumOf { (it.minutes ?: 0).toLong() }
        val unknownRuntimeCount = viewings.size - knownRuntimeViewings.size

        val daily = viewings.groupingBy { it.localDate }.eachCount().toSortedMap()

        val monthly = viewings.groupBy { YearMonth.from(it.localDate) }
            .map { (month, monthViewings) ->
                MonthlyViewing(
                    year = month.year,
                    month = month.monthValue,
                    knownMinutes = monthViewings.sumOf { (it.minutes ?: 0).toLong() },
                    episodeViewings = monthViewings.count { it.episodeId != null },
                    movieViewings = monthViewings.count { it.episodeId == null },
                    unknownRuntimeViewings = monthViewings.count { (it.minutes ?: 0) <= 0 },
                )
            }
            .sortedWith(compareBy({ it.year }, { it.month }))

        val yearly = viewings.groupBy { it.localDate.year }
            .map { (year, yearViewings) ->
                YearActivity(
                    year = year,
                    episodeViewings = yearViewings.count { it.episodeId != null },
                    movieViewings = yearViewings.count { it.episodeId == null },
                    knownMinutes = yearViewings.sumOf { (it.minutes ?: 0).toLong() },
                )
            }
            .sortedBy { it.year }

        val genreCounts = mutableMapOf<String, Int>()
        completed.forEach { entry ->
            val genres = input.genresByMedia[entry.media.id].orEmpty()
            genres.filter { it.isNotBlank() }.distinct().forEach { genre ->
                genreCounts[genre] = (genreCounts[genre] ?: 0) + 1
            }
        }
        val genreDistribution = genreCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(12)
            .map { NameCount(it.key, it.value) }

        val categoryDistribution = listOf(
            MediaCategory.ANIME,
            MediaCategory.SERIES,
            MediaCategory.MOVIES,
            MediaCategory.CARTOONS,
        ).mapNotNull { category ->
            val count = completed.count { it.media.type.category == category }
            if (count > 0) CategoryCount(category, count) else null
        }

        val rated = library.mapNotNull { entry -> entry.media.personalRating?.let { entry to it } }
        val averageRating = if (rated.isEmpty()) null else rated.map { it.second }.average()

        val highestRated = rated
            .sortedWith(compareByDescending<Pair<LibraryEntry, Double>> { it.second }.thenBy { it.first.media.title })
            .take(10)
            .map { (entry, rating) ->
                RatedTitle(
                    mediaId = entry.media.id,
                    title = entry.media.title,
                    posterUrl = entry.media.posterUrl,
                    mediaType = entry.media.type,
                    rating = rating,
                )
            }

        val recentlyCompleted = completed
            .sortedByDescending { it.lastActivityAt ?: 0L }
            .take(10)
            .map { entry ->
                CompletedTitle(
                    mediaId = entry.media.id,
                    title = entry.media.title,
                    posterUrl = entry.media.posterUrl,
                    mediaType = entry.media.type,
                    completedOn = null,
                    lastActivity = entry.lastActivityAt?.let { millis ->
                        java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                    },
                )
            }

        val streaks = StreakCalculator.compute(daily.keys, today)

        return StatisticsSnapshot(
            completedMovies = completedMovies,
            completedSeries = completedSeries,
            completedAnime = completedAnime,
            completedCartoons = completedCartoons,
            completedDocumentaries = completedDocumentaries,
            completedEpisodes = viewings.filter { it.episodeId != null }.distinctBy { it.episodeId }.size,
            episodeViewings = viewings.count { it.episodeId != null },
            movieViewings = viewings.count { it.episodeId == null },
            totalViewings = viewings.size,
            estimatedMinutes = estimatedMinutes,
            viewingsWithUnknownRuntime = unknownRuntimeCount,
            monthly = monthly,
            yearly = yearly,
            dailyActivity = daily,
            genreDistribution = genreDistribution,
            categoryDistribution = categoryDistribution,
            averageRating = averageRating,
            ratedCount = rated.size,
            highestRated = highestRated,
            recentlyCompleted = recentlyCompleted,
            currentStreak = streaks.current,
            longestStreak = streaks.longest,
            totalLibraryTitles = library.size,
            totalKnownEpisodes = library.sumOf { it.episodesTotal },
            firstActivityDate = daily.keys.firstOrNull(),
            lastActivityDate = daily.keys.lastOrNull(),
        )
    }
}
