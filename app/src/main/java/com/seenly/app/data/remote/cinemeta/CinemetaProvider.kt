package com.seenly.app.data.remote.cinemeta

import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.network.ProviderException
import com.seenly.app.core.network.RateLimiter
import com.seenly.app.core.network.rateLimitedCall
import com.seenly.app.domain.model.MediaCategory
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.providers.MetadataProvider
import com.seenly.app.domain.providers.Paged
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import com.seenly.app.domain.providers.ProviderRelation
import java.time.LocalDate

/**
 * Credential-free movie and series metadata from Cinemeta (the Stremio add-on).
 * Catalog endpoints are lightweight; the `meta` endpoint adds IMDb ratings,
 * cast and episode lists with real episode titles.
 */
class CinemetaProvider(
    private val api: CinemetaApi,
    private val dispatchers: AppDispatchers,
) : MetadataProvider {

    override val id: ProviderId = ProviderId.CINEMETA
    override val displayName: String = "Cinemeta (IMDb)"
    override val attributionKey: String = "cinemeta"
    override val requiresCredential: Boolean = false

    override fun isConfigured(): Boolean = true

    // Catalogs are a static CDN mirror; stay polite anyway.
    private val limiter = RateLimiter(maxPerMinute = 90, minIntervalMillis = 150)

    override suspend fun search(query: String, type: MediaType?, page: Int): Paged<ProviderMedia> {
        if (type?.category == MediaCategory.ANIME) return Paged(emptyList(), page, false)
        // The catalog search endpoint has no pagination.
        if (page > 1) return Paged(emptyList(), page, false)
        val clean = query.trim()
        if (clean.isEmpty()) return Paged(emptyList(), page, false)
        val items = mutableListOf<ProviderMedia>()
        catalogTypes(type).forEach { catalogType ->
            try {
                val response = rateLimitedCall(limiter, dispatchers) {
                    api.search(catalogType, clean)
                }
                items += response.metas.mapNotNull(CinemetaMapper::toProviderMedia)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // One failing catalog must not hide the other's results.
            }
        }
        return Paged(
            items = items.distinctBy { it.externalId },
            page = page,
            hasMore = false,
        )
    }

    override suspend fun trending(type: MediaType?, page: Int): Paged<ProviderMedia> {
        if (page > 1) return Paged(emptyList(), page, false)
        val items = mutableListOf<ProviderMedia>()
        catalogTypes(type).forEach { catalogType ->
            try {
                val response = rateLimitedCall(limiter, dispatchers) { api.top(catalogType) }
                items += response.metas.mapNotNull(CinemetaMapper::toProviderMedia)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Best effort; the other catalog may still answer.
            }
        }
        return Paged(items.distinctBy { it.externalId }, page, false)
    }

    override suspend fun upcoming(from: LocalDate, to: LocalDate): List<ProviderMedia> = emptyList()

    override suspend fun details(externalId: String): ProviderMediaDetails {
        val (type, id) = CinemetaMapper.splitExternalId(externalId)
            ?: throw ProviderException.Parse("invalid cinemeta id")
        val response = rateLimitedCall(limiter, dispatchers) { api.meta(type, id) }
        val meta = response.meta ?: throw ProviderException.Http(404, "not found")
        return CinemetaMapper.toDetails(meta) ?: throw ProviderException.Parse("unmappable meta")
    }

    override suspend fun related(externalId: String): List<ProviderRelation> = emptyList()

    /**
     * Fetches only the IMDb rating for a known IMDb id. Used to enrich titles
     * imported from providers that expose an IMDb id but no rating.
     */
    suspend fun imdbRating(imdbId: String, isSeries: Boolean): Double? {
        val type = if (isSeries) "series" else "movie"
        return try {
            val response = rateLimitedCall(limiter, dispatchers) { api.meta(type, imdbId) }
            response.meta?.imdbRating?.toDoubleOrNull()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
    }

    /** Genre browse used by the recommendation deck. */
    suspend fun byGenre(genre: String, isSeries: Boolean): List<ProviderMedia> {
        val catalogType = if (isSeries) "series" else "movie"
        return try {
            val response = rateLimitedCall(limiter, dispatchers) { api.byGenre(catalogType, genre) }
            response.metas.mapNotNull(CinemetaMapper::toProviderMedia)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun catalogTypes(type: MediaType?): List<String> = when {
        type == null -> listOf("movie", "series")
        type.category == MediaCategory.ANIME -> emptyList()
        type.hasEpisodes -> listOf("series")
        else -> listOf("movie")
    }
}
