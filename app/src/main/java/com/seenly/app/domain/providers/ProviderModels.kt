package com.seenly.app.domain.providers

import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import java.time.LocalDate

/** A normalized provider title. Provider DTOs never leave the data layer. */
data class ProviderMedia(
    val provider: ProviderId,
    val externalId: String,
    val type: MediaType,
    val title: String,
    val originalTitle: String? = null,
    val alternateTitles: List<String> = emptyList(),
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val runtimeMinutes: Int? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null,
    val studio: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** Provider score normalized to 0..10 when present. */
    val score: Double? = null,
    val popularity: Double? = null,
    val statusText: String? = null,
    val nextEpisodeAirDate: LocalDate? = null,
    val nextEpisodeNumber: Int? = null,
    /** IMDb id (e.g. tt0944947), when the provider exposes one. */
    val imdbId: String? = null,
    /** IMDb rating on a 0..10 scale. */
    val imdbRating: Double? = null,
    /** Rotten Tomatoes Tomatometer score 0..100. */
    val rottenTomatoesRating: Int? = null,
    /** MyAnimeList community score on a 0..10 scale. */
    val malRating: Double? = null,
    /** MyAnimeList id, used for episode-title enrichment. */
    val malId: Int? = null,
) {
    /** True when any external rating is known; drives `ratingsUpdatedAt`. */
    val hasExternalRating: Boolean
        get() = imdbRating != null || malRating != null || rottenTomatoesRating != null
}

/** A secondary provider identity linked to the same local title. */
data class ProviderLink(
    val provider: ProviderId,
    val externalId: String,
)

data class ProviderSeason(
    val number: Int?,
    val title: String? = null,
    val synopsis: String? = null,
    val episodeCount: Int? = null,
    val airDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val posterUrl: String? = null,
)

data class ProviderEpisode(
    val number: Int?,
    val absoluteNumber: Int? = null,
    val title: String? = null,
    val synopsis: String? = null,
    val runtimeMinutes: Int? = null,
    val airDate: LocalDate? = null,
    val stillUrl: String? = null,
)

data class ProviderSeasonBundle(
    val season: ProviderSeason,
    val episodes: List<ProviderEpisode> = emptyList(),
)

data class ProviderRelation(
    /** e.g. SEQUEL, PREQUEL, SIDE_STORY, SIMILAR, RECOMMENDATION */
    val relation: String,
    val media: ProviderMedia,
)

data class ProviderMediaDetails(
    val media: ProviderMedia,
    val seasons: List<ProviderSeasonBundle> = emptyList(),
    val related: List<ProviderRelation> = emptyList(),
    /** Top-billed cast names, when the provider supplies them. */
    val cast: List<String> = emptyList(),
    /** Director names, when the provider supplies them. */
    val directors: List<String> = emptyList(),
    /**
     * Additional provider identities that resolve to the same local title
     * (e.g. every season of an anime franchise). Persisted so re-imports and
     * searches recognize the title instead of creating duplicates.
     */
    val additionalLinks: List<ProviderLink> = emptyList(),
    /**
     * For grouped franchises, which provider season corresponds to the external
     * id the user opened. Used to migrate watch history when an older import
     * only contained that single season and a refresh expands the franchise.
     */
    val selectedSeasonNumber: Int? = null,
)

data class Paged<T>(
    val items: List<T>,
    val page: Int,
    val hasMore: Boolean,
)

data class ProviderInfo(
    val id: ProviderId,
    val displayName: String,
    val requiresCredential: Boolean,
    val configured: Boolean,
    val attributionKey: String,
)

/**
 * Common interface implemented by every metadata source. Implementations return
 * normalized domain models and throw [com.seenly.app.core.network.ProviderException]
 * subclasses for recoverable failures.
 */
interface MetadataProvider {
    val id: ProviderId
    val displayName: String
    val attributionKey: String
    val requiresCredential: Boolean

    /** False when a provider needs a credential that is not configured. */
    fun isConfigured(): Boolean

    suspend fun search(query: String, type: MediaType?, page: Int): Paged<ProviderMedia>

    suspend fun trending(type: MediaType?, page: Int): Paged<ProviderMedia>

    /** Titles releasing between the two dates; empty when unsupported. */
    suspend fun upcoming(from: LocalDate, to: LocalDate): List<ProviderMedia>

    suspend fun details(externalId: String): ProviderMediaDetails

    suspend fun related(externalId: String): List<ProviderRelation>

    /** Seasonal catalog (anime). Returns null when unsupported by the provider. */
    suspend fun seasonal(year: Int, season: String, page: Int): Paged<ProviderMedia>? = null

    /** Recommendations derived from a provider title, when available. */
    suspend fun recommendations(externalId: String): List<ProviderMedia> = emptyList()
}
