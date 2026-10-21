package com.seenly.app.data.remote.cinemeta

import com.seenly.app.core.network.CinemetaJson
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CinemetaMapperTest {

    private fun seriesMeta() = CinemetaMeta(
        id = "tt123",
        imdbId = "tt123",
        type = "series",
        name = "Slime",
        description = "A charming slime.",
        released = "2018-10-01T00:00:00.000Z",
        runtime = "24 min",
        imdbRating = "8.1",
        genres = listOf("Animation", "Fantasy"),
        releaseInfo = "2018-2024",
        videos = listOf(
            CinemetaVideo(name = "Episode 2", season = 1, number = 2, firstAired = "2018-10-08"),
            CinemetaVideo(name = "Pilot", season = 1, number = 1, firstAired = "2018-10-01"),
            CinemetaVideo(name = "Season 2 start", season = 2, number = 1, firstAired = "2021-01-12"),
        ),
    )

    @Test
    fun `series meta maps IMDb rating and full video list`() {
        val media = CinemetaMapper.toProviderMedia(seriesMeta())
        assertNotNull(media)
        media!!

        assertEquals(ProviderId.CINEMETA, media.provider)
        assertEquals("series/tt123", media.externalId)
        assertEquals(MediaType.CARTOON_SERIES, media.type)
        assertEquals("Slime", media.title)
        assertEquals(LocalDate.of(2018, 10, 1), media.releaseDate)
        assertEquals(LocalDate.of(2024, 12, 31), media.endDate)
        assertEquals(24, media.runtimeMinutes)
        assertEquals(2, media.totalSeasons)
        assertEquals(3, media.totalEpisodes)
        assertEquals(8.1, media.imdbRating ?: 0.0, 0.0)
        assertEquals(8.1, media.score ?: 0.0, 0.0)
        assertEquals("tt123", media.imdbId)
    }

    @Test
    fun `videos become ordered season bundles with continuous absolute numbers`() {
        val bundles = CinemetaMapper.toSeasonBundles(seriesMeta())

        assertEquals(listOf(1, 2), bundles.map { it.season.number })
        assertEquals(listOf(1, 2), bundles[0].episodes.map { it.number })
        assertEquals("Pilot", bundles[0].episodes[0].title)
        assertEquals(LocalDate.of(2018, 10, 1), bundles[0].episodes[0].airDate)
        assertEquals(listOf(1, 2, 3), bundles.flatMap { it.episodes }.map { it.absoluteNumber })
        assertEquals(2, bundles[0].season.episodeCount)
    }

    @Test
    fun `cinemeta camelCase fields are decoded without the snake case strategy`() {
        val response = CinemetaJson.decodeFromString<CinemetaMetaResponse>(
            """
            {
              "meta": {
                "id": "tt9054364",
                "type": "series",
                "name": "Slime",
                "imdbRating": "8.0",
                "releaseInfo": "2018-2026",
                "videos": [
                  {
                    "name": "Pilot",
                    "season": 1,
                    "number": 1,
                    "firstAired": "2018-10-02T00:00:00.000Z"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val meta = response.meta!!
        assertEquals("8.0", meta.imdbRating)
        assertEquals("2018-2026", meta.releaseInfo)
        assertEquals("2018-10-02T00:00:00.000Z", meta.videos.single().firstAired)
        val media = CinemetaMapper.toProviderMedia(meta)!!
        assertEquals(8.0, media.imdbRating ?: 0.0, 0.0)
        assertEquals(LocalDate.of(2018, 10, 2), CinemetaMapper.toSeasonBundles(meta).single().episodes.single().airDate)
    }

    @Test
    fun `catalog rows without an IMDb id are rejected`() {
        val row = CinemetaCatalogMeta(
            id = "not-an-imdb-id",
            type = "movie",
            name = "Unknown",
        )
        assertNull(CinemetaMapper.toProviderMedia(row))

        val valid = CinemetaMapper.toProviderMedia(
            CinemetaCatalogMeta(
                id = "tt999",
                type = "movie",
                name = "A Movie",
                releaseInfo = "1999",
                imdbRating = "7.5",
                genres = listOf("Drama"),
            ),
        )
        assertNotNull(valid)
        assertEquals("movie/tt999", valid!!.externalId)
        assertEquals(MediaType.MOVIE, valid.type)
        assertEquals(7.5, valid.imdbRating ?: 0.0, 0.0)
    }
}
