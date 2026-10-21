package com.seenly.app.data.remote.anilist

import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AniListMapperTest {

    private val utc = ZoneOffset.UTC

    private fun sample() = AniListMedia(
        id = 42,
        title = AniListTitle(
            romaji = "Romaji",
            english = "English",
            native = "Native",
            userPreferred = "Preferred",
        ),
        description = "<p>Hello<br>World &amp; &#039;friends&#039;</p>",
        format = "TV",
        episodes = 12,
        duration = 24,
        genres = listOf("Action", "Action", "Fantasy"),
        averageScore = 85,
        popularity = 1000,
        isAdult = false,
        startDate = AniListFuzzyDate(2024, 4, 5),
        endDate = AniListFuzzyDate(2024, 6, 28),
        coverImage = AniListCover(extraLarge = "xl", large = "l", medium = "m"),
        bannerImage = "banner",
        studios = AniListStudios(
            nodes = listOf(
                AniListStudio(name = "Other", isAnimationStudio = false),
                AniListStudio(name = "Animation Studio", isAnimationStudio = true),
            ),
        ),
        tags = listOf(
            AniListTag("Spoiler", 100, isGeneralSpoiler = true),
            AniListTag("Low", 10, isGeneralSpoiler = false),
            AniListTag("High", 80, isGeneralSpoiler = false),
        ),
        nextAiringEpisode = AniListAiring(episode = 5, airingAt = 1_700_000_000),
    )

    @Test
    fun `html is stripped and entities decoded`() {
        assertEquals("Hello\nWorld & 'friends'", AniListMapper.stripHtml(sample().description))
        assertNull(AniListMapper.stripHtml("   "))
        assertNull(AniListMapper.stripHtml("<br/>"))
    }

    @Test
    fun `partial air dates default missing parts and invalid dates are ignored`() {
        assertEquals(LocalDate.of(2024, 4, 1), AniListMapper.fuzzyDate(AniListFuzzyDate(year = 2024, month = 4)))
        assertNull(AniListMapper.fuzzyDate(AniListFuzzyDate(year = 2024, month = 13, day = 40)))
        assertNull(AniListMapper.fuzzyDate(AniListFuzzyDate(year = null, month = 4, day = 2)))
        assertNull(AniListMapper.fuzzyDate(null))
    }

    @Test
    fun `media is mapped without inventing fields`() {
        val mapped = AniListMapper.toProviderMedia(sample(), utc)
        assertNotNull(mapped)
        mapped!!

        assertEquals(ProviderId.ANILIST, mapped.provider)
        assertEquals("42", mapped.externalId)
        assertEquals(MediaType.ANIME_SERIES, mapped.type)
        assertEquals("English", mapped.title)
        assertEquals("Native", mapped.originalTitle)
        assertEquals(listOf("Romaji", "Native", "Preferred"), mapped.alternateTitles)
        assertEquals("Hello\nWorld & 'friends'", mapped.synopsis)
        assertEquals("xl", mapped.posterUrl)
        assertEquals("banner", mapped.backdropUrl)
        assertEquals(LocalDate.of(2024, 4, 5), mapped.releaseDate)
        assertEquals(LocalDate.of(2024, 6, 28), mapped.endDate)
        assertEquals(24, mapped.runtimeMinutes)
        assertEquals(12, mapped.totalEpisodes)
        assertNull(mapped.totalSeasons)
        assertEquals("Animation Studio", mapped.studio)
        assertEquals(listOf("Action", "Fantasy"), mapped.genres)
        assertEquals(listOf("High", "Low"), mapped.tags)
        assertEquals(8.5, mapped.score ?: 0.0, 0.0)
        assertEquals(
            Instant.ofEpochSecond(1_700_000_000).atZone(utc).toLocalDate(),
            mapped.nextEpisodeAirDate,
        )
        assertEquals(5, mapped.nextEpisodeNumber)
    }

    @Test
    fun `adult and untitled media are excluded`() {
        assertNull(AniListMapper.toProviderMedia(sample().copy(isAdult = true), utc))
        assertNull(AniListMapper.toProviderMedia(sample().copy(title = null), utc))
        assertNull(AniListMapper.toProviderMedia(sample().copy(title = AniListTitle()), utc))
    }

    @Test
    fun `movies are typed as movies and have no episode list`() {
        val movie = sample().copy(format = "MOVIE")
        assertEquals(MediaType.ANIME_MOVIE, AniListMapper.typeOf("movie"))
        assertEquals(MediaType.ANIME_SERIES, AniListMapper.typeOf("OVA"))
        assertTrue(AniListMapper.toEpisodeBundle(movie, utc).isEmpty())
    }

    @Test
    fun `episode bundles keep known dates and project the rest weekly`() {
        val withSchedule = sample().copy(
            airingSchedule = AniListAiringConnection(
                nodes = listOf(AniListAiring(episode = 2, airingAt = 1_700_000_000)),
            ),
        )
        val bundle = AniListMapper.toEpisodeBundle(withSchedule, utc).single()

        assertEquals(1, bundle.season.number)
        assertEquals(12, bundle.season.episodeCount)
        assertEquals(12, bundle.episodes.size)
        assertEquals((1..12).toList(), bundle.episodes.map { it.number })
        assertNull(bundle.episodes.first().title)
        assertEquals(LocalDate.of(2024, 4, 5), bundle.episodes[0].airDate)
        assertEquals(Instant.ofEpochSecond(1_700_000_000).atZone(utc).toLocalDate(), bundle.episodes[1].airDate)
        assertEquals(LocalDate.of(2024, 6, 21), bundle.episodes[11].airDate)
    }

    @Test
    fun `year-only start dates are not projected to January`() {
        val partial = sample().copy(startDate = AniListFuzzyDate(2024, null, null))
        val bundle = AniListMapper.toEpisodeBundle(partial, utc).single()
        assertTrue(bundle.episodes.all { it.airDate == null })
    }

    @Test
    fun `relations and recommendations are flattened`() {
        val related = sample().copy(id = 7, title = AniListTitle(english = "Related"))
        val recommended = sample().copy(id = 8, title = AniListTitle(english = "Recommended"))
        val detailed = sample().copy(
            relations = AniListRelationConnection(
                edges = listOf(AniListRelationEdge("SEQUEL", related)),
            ),
            recommendations = AniListRecommendationConnection(
                nodes = listOf(AniListRecommendation(recommended)),
            ),
        )

        val relations = AniListMapper.toRelationList(detailed, utc)
        assertEquals(2, relations.size)
        assertEquals("SEQUEL", relations[0].relation)
        assertEquals("Related", relations[0].media.title)
        assertEquals("RECOMMENDATION", relations[1].relation)
        assertEquals("Recommended", relations[1].media.title)
    }

    @Test
    fun `franchise chain maps to numbered seasons with continuous absolute numbers`() {
        val first = sample().copy(
            id = 101,
            title = AniListTitle(english = "Slime Season 1"),
            episodes = 24,
            startDate = AniListFuzzyDate(2018, 10, 1),
        )
        val second = sample().copy(
            id = 102,
            title = AniListTitle(english = "Slime Season 2"),
            episodes = 12,
            startDate = AniListFuzzyDate(2021, 1, 1),
        )
        val third = sample().copy(
            id = 103,
            title = AniListTitle(english = "Slime Season 3"),
            episodes = 24,
            startDate = AniListFuzzyDate(2024, 4, 1),
        )

        val bundles = AniListMapper.toFranchiseBundles(listOf(first, second, third), utc)

        assertEquals(3, bundles.size)
        assertEquals(listOf(1, 2, 3), bundles.map { it.season.number })
        assertEquals(listOf(24, 12, 24), bundles.map { it.season.episodeCount })
        assertEquals((1..24).toList(), bundles[0].episodes.map { it.absoluteNumber })
        assertEquals((25..36).toList(), bundles[1].episodes.map { it.absoluteNumber })
        assertEquals((37..60).toList(), bundles[2].episodes.map { it.absoluteNumber })
        assertEquals("Slime Season 2", bundles[1].season.title)
    }

    @Test
    fun `single-entry chain remains season one`() {
        val bundles = AniListMapper.toFranchiseBundles(listOf(sample()), utc)
        assertEquals(1, bundles.size)
        assertEquals(1, bundles.single().season.number)
        assertEquals(12, bundles.single().episodes.size)
    }

    @Test
    fun `upcoming title without an episode count keeps a season placeholder`() {
        val upcoming = sample().copy(
            episodes = null,
            status = "NOT_YET_RELEASED",
            startDate = AniListFuzzyDate(2027, 1, 1),
            endDate = null,
        )

        val bundle = AniListMapper.toEpisodeBundle(upcoming, utc).single()

        assertEquals(1, bundle.season.number)
        assertNull(bundle.season.episodeCount)
        assertTrue(bundle.episodes.isEmpty())
        assertEquals(LocalDate.of(2027, 1, 1), bundle.season.airDate)
    }

    @Test
    fun `franchise keeps an upcoming season without episodes selectable`() {
        val upcoming = sample().copy(
            id = 104,
            title = AniListTitle(english = "Slime Season 4"),
            episodes = null,
            status = "NOT_YET_RELEASED",
            startDate = AniListFuzzyDate(2027, 1, 1),
            endDate = null,
        )
        val first = sample().copy(id = 101, episodes = 24)
        val second = sample().copy(id = 102, episodes = 12)

        val bundles = AniListMapper.toFranchiseBundles(listOf(first, second, upcoming), utc)

        assertEquals(3, bundles.size)
        assertEquals(listOf(1, 2, 3), bundles.map { it.season.number })
        assertEquals(listOf(24, 12, null), bundles.map { it.season.episodeCount })
        assertTrue(bundles.last().episodes.isEmpty())
        assertEquals("Slime Season 4", bundles.last().season.title)
    }
}
