package com.seenly.app.data.remote.cinemeta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Cinemeta is the Stremio metadata add-on: a credential-free IMDb mirror used
 * for movie and series search, details, ratings and episode lists.
 */
interface CinemetaApi {

    @GET("meta/{type}/{id}.json")
    suspend fun meta(
        @Path("type") type: String,
        @Path("id") id: String,
    ): CinemetaMetaResponse

    @GET("catalog/{type}/top/search={query}.json")
    suspend fun search(
        @Path("type") type: String,
        @Path("query") query: String,
    ): CinemetaCatalogResponse

    @GET("catalog/{type}/top/genre={genre}.json")
    suspend fun byGenre(
        @Path("type") type: String,
        @Path("genre") genre: String,
    ): CinemetaCatalogResponse

    @GET("catalog/{type}/top.json")
    suspend fun top(@Path("type") type: String): CinemetaCatalogResponse
}

@Serializable
data class CinemetaMetaResponse(val meta: CinemetaMeta? = null)

@Serializable
data class CinemetaCatalogResponse(val metas: List<CinemetaCatalogMeta> = emptyList())

/** Lightweight row returned by catalog/search endpoints. */
@Serializable
data class CinemetaCatalogMeta(
    val id: String = "",
    @SerialName("imdb_id") val imdbId: String? = null,
    val type: String? = null,
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    @SerialName("releaseInfo") val releaseInfo: String? = null,
    @SerialName("imdbRating") val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val genre: List<String> = emptyList(),
)

@Serializable
data class CinemetaMeta(
    val id: String = "",
    @SerialName("imdb_id") val imdbId: String? = null,
    val type: String? = null,
    val name: String = "",
    val description: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val released: String? = null,
    val runtime: String? = null,
    @SerialName("imdbRating") val imdbRating: String? = null,
    val genre: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val writer: List<String> = emptyList(),
    val country: String? = null,
    val status: String? = null,
    val year: String? = null,
    @SerialName("releaseInfo") val releaseInfo: String? = null,
    val videos: List<CinemetaVideo> = emptyList(),
)

@Serializable
data class CinemetaVideo(
    val name: String? = null,
    val season: Int? = null,
    val number: Int? = null,
    @SerialName("firstAired") val firstAired: String? = null,
    val overview: String? = null,
    val rating: String? = null,
    val thumbnail: String? = null,
)
