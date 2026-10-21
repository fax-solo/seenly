package com.seenly.app.core.network

import com.seenly.app.core.common.AppJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Jikan payload JSON uses snake_case field names.
 * Announced experimentally stable; explicit opt-in keeps the build clean.
 */
@OptIn(ExperimentalSerializationApi::class)
val ProviderJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
    coerceInputValues = true
}

/**
 * Cinemeta's JSON is already camelCase (for example `imdbRating`, `releaseInfo`
 * and `firstAired`). The snake-case [ProviderJson] strategy rewrites those names
 * even when they are explicitly annotated, which silently dropped IMDb ratings
 * and episode air dates. Use a plain JSON parser for this provider.
 */
val CinemetaJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * TVmaze also returns camelCase names (`averageRuntime`, `officialSite`,
 * `_embedded`, `episodeOrder`). The snake-case strategy mangles them for the
 * same reason it mangles Cinemeta fields, so use plain JSON here too.
 */
val TvmazeJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

object Endpoints {
    const val ANILIST = "https://graphql.anilist.co/"
    const val CINEMETA = "https://v3-cinemeta.strem.io/"
    const val TVMAZE = "https://api.tvmaze.com/"
    const val JIKAN = "https://api.jikan.moe/v4/"
}

/**
 * Builds the shared OkHttp client. Deliberately NO logging interceptor: search
 * terms and API traffic must never end up in logcat.
 */
fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    // Keep provider outages short. The optional sources are best-effort; a
    // missing rating or episode title must never hold the UI for a minute.
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .callTimeout(25, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

private fun retrofit(
    baseUrl: String,
    client: OkHttpClient,
    json: Json,
): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

fun anilistRetrofit(client: OkHttpClient): Retrofit = retrofit(Endpoints.ANILIST, client, AppJson)

fun cinemetaRetrofit(client: OkHttpClient): Retrofit = retrofit(Endpoints.CINEMETA, client, CinemetaJson)

fun tvmazeRetrofit(client: OkHttpClient): Retrofit = retrofit(Endpoints.TVMAZE, client, TvmazeJson)

fun jikanRetrofit(client: OkHttpClient): Retrofit = retrofit(Endpoints.JIKAN, client, ProviderJson)
