package com.seenly.app.data.remote.tvmaze

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
 * TVmaze adapter. No credential required; used for series search and full
 * season/episode metadata (including episode titles and IMDb cross-links).
 */
class TvmazeProvider(
    private val api: TvmazeApi,
    private val dispatchers: AppDispatchers,
) : MetadataProvider {

    override val id: ProviderId = ProviderId.TVMAZE
    override val displayName: String = "TVmaze"
    override val attributionKey: String = "tvmaze"
    override val requiresCredential: Boolean = false

    override fun isConfigured(): Boolean = true

    // TVmaze allows ~20 requests per 10 seconds.
    private val limiter = RateLimiter(maxPerMinute = 90, minIntervalMillis = 300)

    override suspend fun search(query: String, type: MediaType?, page: Int): Paged<ProviderMedia> {
        if (type?.category == MediaCategory.ANIME) return Paged(emptyList(), page, false)
        if (type != null && !type.hasEpisodes) return Paged(emptyList(), page, false)
        // The search endpoint has no pagination.
        if (page > 1) return Paged(emptyList(), page, false)
        val clean = query.trim()
        if (clean.isEmpty()) return Paged(emptyList(), page, false)
        val response = rateLimitedCall(limiter, dispatchers) { api.search(clean) }
        return Paged(
            items = response.mapNotNull { it.show?.let(TvmazeMapper::toProviderMedia) }
                .distinctBy { it.externalId },
            page = page,
            hasMore = false,
        )
    }

    /** TVmaze has no trending endpoint; catalog feeds come from Cinemeta. */
    override suspend fun trending(type: MediaType?, page: Int): Paged<ProviderMedia> =
        Paged(emptyList(), page, false)

    override suspend fun upcoming(from: LocalDate, to: LocalDate): List<ProviderMedia> = emptyList()

    override suspend fun details(externalId: String): ProviderMediaDetails {
        val id = externalId.toLongOrNull() ?: throw ProviderException.Parse("invalid tvmaze id")
        val show = rateLimitedCall(limiter, dispatchers) {
            api.show(id, embed = listOf("seasons", "episodes"))
        }
        return TvmazeMapper.toDetails(show) ?: throw ProviderException.Parse("unmappable show")
    }

    override suspend fun related(externalId: String): List<ProviderRelation> = emptyList()
}
