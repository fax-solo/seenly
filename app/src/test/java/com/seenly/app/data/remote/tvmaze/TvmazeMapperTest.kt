package com.seenly.app.data.remote.tvmaze

import com.seenly.app.core.network.TvmazeJson
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TvmazeMapperTest {

    private fun show() = TvmazeShow(
        id = 82,
        name = "Game of Thrones",
        type = "Scripted",
        language = "English",
        genres = listOf("Drama", "Fantasy"),
        status = "Ended",
        averageRuntime = 60,
        premiered = "2011-04-17",
        ended = "2019-05-19",
        rating = TvmazeRating(average = 8.7),
        network = TvmazeNetwork(name = "HBO"),
        summary = "<p>Winter <b>is</b> coming &amp; more.</p>",
        externals = TvmazeExternals(imdb = "tt0944947"),
        embedded = TvmazeEmbedded(
            seasons = listOf(
                TvmazeSeason(id = 1, number = 1, name = "Season 1", episodeOrder = 10),
            ),
            episodes = listOf(
                TvmazeEpisode(id = 1, name = "Winter Is Coming", season = 1, number = 1, airdate = "2011-04-17", runtime = 62),
                TvmazeEpisode(id = 2, name = "The Kingsroad", season = 1, number = 2, airdate = "2011-04-24", runtime = 56),
            ),
        ),
    )

    @Test
    fun `show maps HTML-free summary IMDb id and ratings`() {
        val media = TvmazeMapper.toProviderMedia(show())
        assertNotNull(media)
        media!!

        assertEquals(ProviderId.TVMAZE, media.provider)
        assertEquals("82", media.externalId)
        assertEquals(MediaType.TV_SERIES, media.type)
        assertEquals("Game of Thrones", media.title)
        assertEquals("Winter is coming & more.", media.synopsis)
        assertEquals("HBO", media.studio)
        assertEquals(8.7, media.score ?: 0.0, 0.0)
        assertEquals("tt0944947", media.imdbId)
        assertEquals(LocalDate.of(2011, 4, 17), media.releaseDate)
        assertEquals(LocalDate.of(2019, 5, 19), media.endDate)
        assertEquals(60, media.runtimeMinutes)
        assertEquals(1, media.totalSeasons)
        assertEquals(2, media.totalEpisodes)
    }

    @Test
    fun `tvmaze camelCase and embedded fields decode without the snake case strategy`() {
        val show = TvmazeJson.decodeFromString<TvmazeShow>(
            """
            {
              "id": 82,
              "name": "Game of Thrones",
              "type": "Scripted",
              "averageRuntime": 60,
              "officialSite": "https://www.hbo.com/game-of-thrones",
              "externals": { "imdb": "tt0944947" },
              "_embedded": {
                "seasons": [
                  {
                    "id": 1,
                    "number": 1,
                    "episodeOrder": 10,
                    "premiereDate": "2011-04-17",
                    "endDate": "2011-06-19"
                  }
                ],
                "episodes": [
                  {
                    "id": 1,
                    "name": "Winter Is Coming",
                    "season": 1,
                    "number": 1,
                    "airdate": "2011-04-17"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(60, show.averageRuntime)
        assertEquals("https://www.hbo.com/game-of-thrones", show.officialSite)
        assertEquals("tt0944947", show.externals?.imdb)
        assertEquals(10, show.embedded?.seasons?.single()?.episodeOrder)
        assertEquals("2011-04-17", show.embedded?.seasons?.single()?.premiereDate)
        assertEquals("Winter Is Coming", show.embedded?.episodes?.single()?.name)
    }

    @Test
    fun `anime genre produces the anime type`() {
        val media = TvmazeMapper.toProviderMedia(show().copy(genres = listOf("Anime", "Action")))
        assertEquals(MediaType.ANIME_SERIES, media!!.type)
    }

    @Test
    fun `embedded episodes become ordered season bundles`() {
        val bundles = TvmazeMapper.toSeasonBundles(show())

        assertEquals(1, bundles.size)
        val season = bundles.single()
        assertEquals(1, season.season.number)
        assertEquals("Season 1", season.season.title)
        assertEquals(listOf(1, 2), season.episodes.map { it.number })
        assertEquals(listOf(1, 2), season.episodes.map { it.absoluteNumber })
        assertEquals("Winter Is Coming", season.episodes[0].title)
        assertEquals(62, season.episodes[0].runtimeMinutes)
    }

    @Test
    fun `shows without id or name are rejected`() {
        assertNull(TvmazeMapper.toProviderMedia(show().copy(id = 0)))
        assertNull(TvmazeMapper.toProviderMedia(show().copy(name = " ")))
        assertNull(TvmazeMapper.stripHtml("  "))
    }
}
