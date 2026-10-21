package com.seenly.app.data.remote.jikan

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Optional Jikan (unofficial MyAnimeList API) adapter. Never required by the app;
 * used as a secondary anime source when configured/available.
 */
interface JikanApi {

    @GET("anime")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("sfw") sfw: Boolean = true,
    ): JikanListResponse<JikanAnime>

    @GET("anime/{id}/full")
    suspend fun details(@Path("id") id: Int): JikanSingleResponse<JikanAnime>

    @GET("anime/{id}/episodes")
    suspend fun episodes(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
    ): JikanListResponse<JikanEpisode>

    @GET("anime/{id}/recommendations")
    suspend fun recommendations(@Path("id") id: Int): JikanListResponse<JikanRecommendation>

    @GET("top/anime")
    suspend fun top(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("sfw") sfw: Boolean = true,
    ): JikanListResponse<JikanAnime>

    @GET("seasons/upcoming")
    suspend fun upcoming(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("sfw") sfw: Boolean = true,
    ): JikanListResponse<JikanAnime>

    @GET("seasons/{year}/{season}")
    suspend fun seasonal(
        @Path("year") year: Int,
        @Path("season") season: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
    ): JikanListResponse<JikanAnime>
}

@Serializable
data class JikanListResponse<T>(
    val data: List<T> = emptyList(),
    val pagination: JikanPagination? = null,
)

@Serializable
data class JikanSingleResponse<T>(val data: T? = null)

@Serializable
data class JikanPagination(
    val lastVisiblePage: Int? = null,
    val hasNextPage: Boolean? = null,
    val currentPage: Int? = null,
    val items: JikanPaginationItems? = null,
)

@Serializable
data class JikanPaginationItems(
    val count: Int? = null,
    val total: Int? = null,
    val perPage: Int? = null,
)

@Serializable
data class JikanTitle(val type: String? = null, val title: String? = null)

@Serializable
data class JikanAired(
    val from: String? = null,
    val to: String? = null,
    val string: String? = null,
)

@Serializable
data class JikanNamedEntity(
    val malId: Int = 0,
    val type: String? = null,
    val name: String? = null,
    val url: String? = null,
)

@Serializable
data class JikanImageUrls(
    val imageUrl: String? = null,
    val smallImageUrl: String? = null,
    val largeImageUrl: String? = null,
)

@Serializable
data class JikanImages(
    val jpg: JikanImageUrls? = null,
    val webp: JikanImageUrls? = null,
)

@Serializable
data class JikanBroadcast(
    val day: String? = null,
    val time: String? = null,
    val timezone: String? = null,
    val string: String? = null,
)

@Serializable
data class JikanAnime(
    val malId: Int = 0,
    val url: String? = null,
    val title: String? = null,
    val titleEnglish: String? = null,
    val titleJapanese: String? = null,
    val titles: List<JikanTitle> = emptyList(),
    val synopsis: String? = null,
    val type: String? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val aired: JikanAired? = null,
    val duration: String? = null,
    val rating: String? = null,
    val score: Double? = null,
    val scoredBy: Int? = null,
    val popularity: Int? = null,
    val genres: List<JikanNamedEntity> = emptyList(),
    val studios: List<JikanNamedEntity> = emptyList(),
    val images: JikanImages? = null,
    val season: String? = null,
    val year: Int? = null,
    val broadcast: JikanBroadcast? = null,
)

@Serializable
data class JikanEpisode(
    val malId: Int = 0,
    val url: String? = null,
    val title: String? = null,
    val titleJapanese: String? = null,
    val aired: String? = null,
    val score: Double? = null,
    val filler: Boolean? = null,
    val recap: Boolean? = null,
)

@Serializable
data class JikanRecommendation(
    val entry: JikanAnime? = null,
    val url: String? = null,
    val votes: Int = 0,
)
