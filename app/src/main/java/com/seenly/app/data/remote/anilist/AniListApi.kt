package com.seenly.app.data.remote.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: JsonObject? = null,
)

@Serializable
data class GraphQLResponse(
    val data: JsonElement? = null,
    val errors: List<GraphQLError>? = null,
)

@Serializable
data class GraphQLError(
    val message: String? = null,
    val status: Int? = null,
)

interface AniListApi {
    @POST("graphql")
    suspend fun query(@Body request: GraphQLRequest): GraphQLResponse
}

// --- DTOs (only the fields the app actually consumes) ---

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
    val userPreferred: String? = null,
)

@Serializable
data class AniListCover(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class AniListTag(
    val name: String,
    val rank: Int? = null,
    val isGeneralSpoiler: Boolean? = null,
)

@Serializable
data class AniListStudio(
    val name: String? = null,
    val isAnimationStudio: Boolean? = null,
)

@Serializable
data class AniListStudios(val nodes: List<AniListStudio> = emptyList())

@Serializable
data class AniListFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
data class AniListAiring(
    val episode: Int? = null,
    /** Unix seconds. */
    val airingAt: Long? = null,
)

@Serializable
data class AniListAiringConnection(val nodes: List<AniListAiring> = emptyList())

@Serializable
data class AniListRelationEdge(
    val relationType: String? = null,
    val node: AniListMedia? = null,
)

@Serializable
data class AniListRelationConnection(val edges: List<AniListRelationEdge> = emptyList())

@Serializable
data class AniListRecommendation(
    val mediaRecommendation: AniListMedia? = null,
    val rating: Int? = null,
)

@Serializable
data class AniListRecommendationConnection(val nodes: List<AniListRecommendation> = emptyList())

@Serializable
data class AniListMedia(
    val id: Int = 0,
    val idMal: Int? = null,
    val title: AniListTitle? = null,
    val synonyms: List<String> = emptyList(),
    val description: String? = null,
    val format: String? = null,
    val status: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val genres: List<String> = emptyList(),
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val countryOfOrigin: String? = null,
    val isAdult: Boolean? = null,
    val startDate: AniListFuzzyDate? = null,
    val endDate: AniListFuzzyDate? = null,
    val coverImage: AniListCover? = null,
    val bannerImage: String? = null,
    val studios: AniListStudios? = null,
    val tags: List<AniListTag> = emptyList(),
    val nextAiringEpisode: AniListAiring? = null,
    val relations: AniListRelationConnection? = null,
    val recommendations: AniListRecommendationConnection? = null,
    val airingSchedule: AniListAiringConnection? = null,
)

@Serializable
data class AniListPageInfo(
    val currentPage: Int? = null,
    val hasNextPage: Boolean? = null,
    val total: Int? = null,
)

@Serializable
data class AniListAiringScheduleNode(
    val episode: Int? = null,
    val airingAt: Long? = null,
    val media: AniListMedia? = null,
)

@Serializable
data class AniListAiringSchedulePage(val nodes: List<AniListAiringScheduleNode> = emptyList())

@Serializable
data class AniListPage(
    val pageInfo: AniListPageInfo? = null,
    val media: List<AniListMedia> = emptyList(),
    val airingSchedules: AniListAiringSchedulePage? = null,
)

@Serializable
data class AniListPageData(@SerialName("Page") val page: AniListPage? = null)

@Serializable
data class AniListMediaData(@SerialName("Media") val media: AniListMedia? = null)
