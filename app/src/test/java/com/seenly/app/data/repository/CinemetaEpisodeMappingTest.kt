package com.seenly.app.data.repository

import com.seenly.app.domain.providers.ProviderEpisode
import com.seenly.app.domain.providers.ProviderSeason
import com.seenly.app.domain.providers.ProviderSeasonBundle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CinemetaEpisodeMappingTest {

    private fun bundle(
        season: Int,
        start: LocalDate?,
        count: Int,
        absoluteStart: Int,
        dates: List<LocalDate?> = (0 until count).map { offset -> start?.plusWeeks(offset.toLong()) },
        prefix: String = "L$season",
    ) = ProviderSeasonBundle(
        season = ProviderSeason(
            number = season,
            episodeCount = count,
            airDate = start,
        ),
        episodes = (1..count).map { number ->
            ProviderEpisode(
                number = number,
                absoluteNumber = absoluteStart + number - 1,
                title = "$prefix$number",
                airDate = dates.getOrNull(number - 1),
            )
        },
    )

    @Test
    fun `split cours stay aligned when IMDb has fewer episodes than AniList`() {
        val local = listOf(
            bundle(1, LocalDate.of(2021, 1, 11), 11, 1),
            bundle(2, LocalDate.of(2021, 10, 4), 12, 12),
            bundle(3, LocalDate.of(2023, 7, 3), 13, 24),
            bundle(4, LocalDate.of(2024, 4, 8), 12, 37),
            bundle(5, LocalDate.of(2026, 7, 4), 14, 49),
        )
        val firstSeasonDates = (0 until 11).map { LocalDate.of(2021, 1, 10).plusWeeks(it.toLong()) } +
            (0 until 12).map { LocalDate.of(2021, 10, 3).plusWeeks(it.toLong()) }
        val secondSeasonDates = (0 until 12).map { LocalDate.of(2023, 7, 9).plusWeeks(it.toLong()) } +
            (0 until 12).map { LocalDate.of(2024, 4, 7).plusWeeks(it.toLong()) }
        val cinema = listOf(
            bundle(1, LocalDate.of(2021, 1, 10), 23, 1, firstSeasonDates, prefix = "C1E"),
            bundle(2, LocalDate.of(2023, 7, 9), 24, 24, secondSeasonDates, prefix = "C2E"),
            bundle(3, LocalDate.of(2026, 7, 3), 14, 48, prefix = "C3E"),
        )

        val mapped = CinemetaEpisodeMapping.map(local, cinema)

        assertEquals("C1E1", mapped[1]?.title)
        assertEquals("C1E11", mapped[11]?.title)
        assertEquals("C1E12", mapped[12]?.title)
        assertEquals("C1E23", mapped[23]?.title)
        assertEquals("C2E1", mapped[24]?.title)
        assertEquals("C2E12", mapped[35]?.title)
        assertNull(mapped[36])
        assertEquals("C2E13", mapped[37]?.title)
        assertEquals("C2E24", mapped[48]?.title)
        assertEquals("C3E1", mapped[49]?.title)
        assertEquals("C3E14", mapped[62]?.title)
    }

    @Test
    fun `undated seasons fall back to absolute numbering`() {
        val local = listOf(bundle(1, start = null, count = 3, absoluteStart = 1))
        val cinema = listOf(bundle(1, start = null, count = 3, absoluteStart = 1, prefix = "C"))

        val mapped = CinemetaEpisodeMapping.map(local, cinema)

        assertEquals("C1", mapped[1]?.title)
        assertEquals("C3", mapped[3]?.title)
    }
}
