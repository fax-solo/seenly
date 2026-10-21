package com.seenly.app.data.remote.anilist

import com.seenly.app.TestSupport
import com.seenly.app.core.common.AppJson
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for franchises whose cours are connected through an
 * OVA/OAD bridge rather than a direct TV-to-TV sequel.
 */
class AniListProviderFranchiseTest {

    @Test
    fun `walks through a non-series bridge and keeps only TV seasons`() = runBlocking {
        val seasonOne = media(id = 1, title = "Root Saga", year = 2020)
        val bridge = media(
            id = 2,
            title = "Bridge OVA",
            year = 2021,
            format = "OVA",
            sequel = 3,
        ).copy(
            relations = AniListRelationConnection(
                edges = listOf(
                    AniListRelationEdge(
                        relationType = "PREQUEL",
                        node = seasonOne,
                    ),
                    AniListRelationEdge(
                        relationType = "SEQUEL",
                        node = media(id = 3, title = "Sequel Saga", year = 2022),
                    ),
                ),
            ),
        )
        val seasonTwo = media(
            id = 3,
            title = "Sequel Saga",
            year = 2022,
        ).copy(
            relations = AniListRelationConnection(
                edges = listOf(
                    AniListRelationEdge(
                        relationType = "PREQUEL",
                        // The immediate prequel is the OVA bridge, not season one.
                        node = AniListMedia(id = 2, format = "OVA"),
                    ),
                ),
            ),
        )
        val api = FakeAniListApi(
            mapOf(
                1 to seasonOne.copy(
                    relations = AniListRelationConnection(
                        edges = listOf(
                            AniListRelationEdge(relationType = "SEQUEL", node = bridge),
                        ),
                    ),
                ),
                2 to bridge,
                3 to seasonTwo,
            ),
        )
        val provider = AniListProvider(
            api = api,
            dispatchers = TestSupport.dispatchers,
            zoneProvider = { ZoneOffset.UTC },
        )

        val details = provider.details("3")

        assertEquals("Root Saga", details.media.title)
        assertEquals(2, details.media.totalSeasons)
        assertEquals(4, details.media.totalEpisodes)
        assertEquals(listOf(1, 2), details.seasons.map { it.season.number })
        assertEquals(listOf("1"), details.additionalLinks.map { it.externalId })
        assertEquals(2, details.selectedSeasonNumber)
        assertEquals(2, details.seasons.first().episodes.size)
    }

    @Test
    fun `opens an announced sequel even before its episode count exists`() = runBlocking {
        val seasonOne = media(id = 1, title = "Root Saga", year = 2020)
        val upcoming = media(
            id = 2,
            title = "Upcoming Saga",
            year = 2027,
            episodes = null,
            status = "NOT_YET_RELEASED",
        )
        val api = FakeAniListApi(
            mapOf(
                1 to seasonOne.copy(
                    relations = AniListRelationConnection(
                        edges = listOf(
                            AniListRelationEdge(
                                relationType = "SEQUEL",
                                node = AniListMedia(id = 2, format = "TV"),
                            ),
                        ),
                    ),
                ),
                2 to upcoming.copy(
                    relations = AniListRelationConnection(
                        edges = listOf(
                            AniListRelationEdge(
                                relationType = "PREQUEL",
                                node = AniListMedia(id = 1, format = "TV"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val provider = AniListProvider(
            api = api,
            dispatchers = TestSupport.dispatchers,
            zoneProvider = { ZoneOffset.UTC },
        )

        val details = provider.details("2")

        assertEquals("Root Saga", details.media.title)
        assertEquals(2, details.media.totalSeasons)
        assertEquals(2, details.media.totalEpisodes)
        assertEquals(listOf(1, 2), details.seasons.map { it.season.number })
        assertEquals(2, details.selectedSeasonNumber)
        assertEquals(2, details.seasons.last().season.number)
        assertEquals(0, details.seasons.last().episodes.size)
    }

    private fun media(
        id: Int,
        title: String,
        year: Int,
        format: String = "TV",
        sequel: Int? = null,
        episodes: Int? = 2,
        status: String = "FINISHED",
    ): AniListMedia {
        val edges = buildList {
            if (sequel != null) {
                add(
                    AniListRelationEdge(
                        relationType = "SEQUEL",
                        node = AniListMedia(id = sequel, format = format),
                    ),
                )
            }
        }
        return AniListMedia(
            id = id,
            idMal = id,
            title = AniListTitle(english = title, romaji = title),
            format = format,
            status = status,
            episodes = episodes,
            startDate = AniListFuzzyDate(year = year, month = 1, day = 1),
            relations = AniListRelationConnection(edges = edges),
        )
    }

    private class FakeAniListApi(
        private val media: Map<Int, AniListMedia>,
    ) : AniListApi {
        override suspend fun query(request: GraphQLRequest): GraphQLResponse {
            val id = request.variables?.get("id")?.jsonPrimitive?.int
            val item = media[id]
                ?: return GraphQLResponse(errors = listOf(GraphQLError("missing test media", 404)))
            return GraphQLResponse(
                data = buildJsonObject {
                    put("Media", AppJson.encodeToJsonElement(AniListMedia.serializer(), item))
                },
            )
        }
    }
}
