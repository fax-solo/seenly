package com.seenly.app.data.remote.tvmaze

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TVmaze — credential-free TV series index with full season/episode listings
 * and IMDb cross-references. Used as the series fallback/companion to Cinemeta.
 */
interface TvmazeApi {

    @GET("search/shows")
    suspend fun search(
        @Query("q") query: String,
    ): List<TvmazeSearchResult>

    @GET("shows/{id}")
    suspend fun show(
        @Path("id") id: Long,
        @Query("embed[]") embed: List<String>? = null,
    ): TvmazeShow
}

@Serializable
data class TvmazeSearchResult(
    val score: Double? = null,
    val show: TvmazeShow? = null,
)

@Serializable
data class TvmazeRating(val average: Double? = null)

@Serializable
data class TvmazeCountry(
    val name: String? = null,
    val code: String? = null,
)

@Serializable
data class TvmazeNetwork(
    val id: Long? = null,
    val name: String? = null,
    val country: TvmazeCountry? = null,
)

@Serializable
data class TvmazeImage(
    val medium: String? = null,
    val original: String? = null,
)

@Serializable
data class TvmazeExternals(
    val tvrage: Long? = null,
    val thetvdb: Long? = null,
    val imdb: String? = null,
)

@Serializable
data class TvmazeShow(
    val id: Long = 0,
    val name: String = "",
    val type: String? = null,
    val language: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val runtime: Int? = null,
    @SerialName("averageRuntime") val averageRuntime: Int? = null,
    val premiered: String? = null,
    val ended: String? = null,
    val officialSite: String? = null,
    val rating: TvmazeRating? = null,
    val weight: Int? = null,
    val network: TvmazeNetwork? = null,
    val webChannel: TvmazeNetwork? = null,
    val image: TvmazeImage? = null,
    val summary: String? = null,
    val externals: TvmazeExternals? = null,
    @SerialName("_embedded") val embedded: TvmazeEmbedded? = null,
)

@Serializable
data class TvmazeEmbedded(
    val seasons: List<TvmazeSeason> = emptyList(),
    val episodes: List<TvmazeEpisode> = emptyList(),
)

@Serializable
data class TvmazeSeason(
    val id: Long = 0,
    val number: Int? = null,
    val name: String? = null,
    @SerialName("episodeOrder") val episodeOrder: Int? = null,
    @SerialName("premiereDate") val premiereDate: String? = null,
    @SerialName("endDate") val endDate: String? = null,
    val image: TvmazeImage? = null,
    val summary: String? = null,
)

@Serializable
data class TvmazeEpisode(
    val id: Long = 0,
    val name: String? = null,
    val season: Int? = null,
    val number: Int? = null,
    val airdate: String? = null,
    val runtime: Int? = null,
    val summary: String? = null,
    val image: TvmazeImage? = null,
)
