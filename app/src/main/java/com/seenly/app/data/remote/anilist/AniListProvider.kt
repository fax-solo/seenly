package com.seenly.app.data.remote.anilist

import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.common.AppJson
import com.seenly.app.core.network.ProviderException
import com.seenly.app.core.network.RateLimiter
import com.seenly.app.core.network.rateLimitedCall
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.providers.MetadataProvider
import com.seenly.app.domain.providers.Paged
import com.seenly.app.domain.providers.ProviderLink
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import com.seenly.app.domain.providers.ProviderRelation
import com.seenly.app.domain.providers.ProviderSeasonBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * AniList GraphQL adapter — the primary, credential-free anime metadata source.
 * Respects AniList's published rate limits (currently ~90 requests/minute) with a
 * conservative limiter plus retry/backoff.
 *
 * `details` resolves the prequel/sequel franchise chain of TV-format entries and
 * returns it as numbered seasons so multi-cour anime are imported as one title.
 */
class AniListProvider(
    private val api: AniListApi,
    private val dispatchers: AppDispatchers,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
    /**
     * Optional episode-title lookup by MyAnimeList id (Jikan). AniList only
     * publishes airing schedules, never episode titles.
     */
    private val episodeTitleSource: (suspend (Int) -> Map<Int, String>)? = null,
) : MetadataProvider {

    override val id: ProviderId = ProviderId.ANILIST
    override val displayName: String = "AniList"
    override val attributionKey: String = "anilist"
    override val requiresCredential: Boolean = false

    override fun isConfigured(): Boolean = true

    private val limiter = RateLimiter(maxPerMinute = 60, minIntervalMillis = 700)

    private val perPage = 24

    /** Franchise chains resolved this process, keyed by any member's AniList id. */
    private val chainCache = ConcurrentHashMap<Int, List<AniListMedia>>()

    private val seriesFormats = setOf("TV", "TV_SHORT", "ONA")
    private val chainRelations = setOf("PREQUEL", "SEQUEL")

    private suspend fun page(
        query: String,
        variables: JsonObject,
        maxAttempts: Int = 3,
    ): AniListPage {
        val response = rateLimitedCall(limiter, dispatchers, maxAttempts) {
            api.query(GraphQLRequest(query = query, variables = variables))
        }
        if (response.errors?.isNotEmpty() == true) {
            val status = response.errors.firstNotNullOfOrNull { it.status }
            val message = response.errors.firstNotNullOfOrNull { it.message }
            throw when (status) {
                429 -> ProviderException.RateLimited(null)
                401, 403 -> ProviderException.Unauthorized(message)
                else -> ProviderException.Parse(message ?: "GraphQL error")
            }
        }
        val data = response.data ?: throw ProviderException.Parse("empty response")
        return runCatching {
            AppJson.decodeFromJsonElement<AniListPageData>(data).page
        }.getOrNull() ?: throw ProviderException.Parse("unexpected page payload")
    }

    private suspend fun mediaData(query: String, variables: JsonObject): AniListMedia {
        val response = rateLimitedCall(limiter, dispatchers) {
            api.query(GraphQLRequest(query = query, variables = variables))
        }
        if (response.errors?.isNotEmpty() == true) {
            val status = response.errors.firstNotNullOfOrNull { it.status }
            val message = response.errors.firstNotNullOfOrNull { it.message }
            throw when (status) {
                429 -> ProviderException.RateLimited(null)
                401, 403 -> ProviderException.Unauthorized(message)
                else -> ProviderException.Parse(message ?: "GraphQL error")
            }
        }
        val data = response.data ?: throw ProviderException.Parse("empty response")
        return runCatching {
            AppJson.decodeFromJsonElement<AniListMediaData>(data).media
        }.getOrNull() ?: throw ProviderException.Http(404, "not found")
    }

    private suspend fun mediaById(id: Int): AniListMedia =
        mediaData(AniListQueries.DETAILS, buildJsonObject { put("id", JsonPrimitive(id)) })

    override suspend fun search(query: String, type: MediaType?, page: Int): Paged<ProviderMedia> {
        val variables = buildJsonObject {
            put("search", JsonPrimitive(query))
            put("page", JsonPrimitive(page.coerceAtLeast(1)))
            put("perPage", JsonPrimitive(perPage))
        }
        val result = page(AniListQueries.SEARCH, variables)
        val items = result.media.mapNotNull { AniListMapper.toProviderMedia(it, zoneProvider()) }
        return Paged(items, result.pageInfo?.currentPage ?: page, result.pageInfo?.hasNextPage == true)
    }

    override suspend fun trending(type: MediaType?, page: Int): Paged<ProviderMedia> {
        val variables = buildJsonObject {
            put("page", JsonPrimitive(page.coerceAtLeast(1)))
            put("perPage", JsonPrimitive(perPage))
            put("sort", JsonArray(listOf(JsonPrimitive("TRENDING_DESC"))))
        }
        val result = page(AniListQueries.TRENDING, variables)
        val items = result.media.mapNotNull { AniListMapper.toProviderMedia(it, zoneProvider()) }
        return Paged(items, result.pageInfo?.currentPage ?: page, result.pageInfo?.hasNextPage == true)
    }

    override suspend fun seasonal(year: Int, season: String, page: Int): Paged<ProviderMedia> {
        val variables = buildJsonObject {
            put("season", JsonPrimitive(season.uppercase()))
            put("year", JsonPrimitive(year))
            put("page", JsonPrimitive(page.coerceAtLeast(1)))
            put("perPage", JsonPrimitive(perPage))
        }
        val result = page(AniListQueries.SEASONAL, variables)
        val items = result.media.mapNotNull { AniListMapper.toProviderMedia(it, zoneProvider()) }
        return Paged(items, result.pageInfo?.currentPage ?: page, result.pageInfo?.hasNextPage == true)
    }

    override suspend fun upcoming(from: LocalDate, to: LocalDate): List<ProviderMedia> {
        val zone = zoneProvider()
        val fromSeconds = from.atStartOfDay(zone).toEpochSecond()
        val toSeconds = to.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val results = mutableListOf<AniListAiringScheduleNode>()
        var currentPage = 1
        while (currentPage <= 3) {
            val variables = buildJsonObject {
                put("from", JsonPrimitive(fromSeconds))
                put("to", JsonPrimitive(toSeconds))
                put("page", JsonPrimitive(currentPage))
                put("perPage", JsonPrimitive(50))
            }
            val result = page(AniListQueries.UPCOMING_AIRING, variables)
            val nodes = result.airingSchedules?.nodes.orEmpty()
            results += nodes
            if (result.pageInfo?.hasNextPage != true || nodes.isEmpty()) break
            currentPage += 1
        }
        return results.mapNotNull { node ->
            val media = node.media ?: return@mapNotNull null
            val mapped = AniListMapper.toProviderMedia(media, zone) ?: return@mapNotNull null
            val airDate = node.airingAt?.let {
                java.time.Instant.ofEpochSecond(it).atZone(zone).toLocalDate()
            }
            mapped.copy(
                nextEpisodeAirDate = airDate,
                nextEpisodeNumber = node.episode,
            )
        }
            .distinctBy { it.externalId }
            .sortedBy { it.nextEpisodeAirDate }
    }

    override suspend fun details(externalId: String): ProviderMediaDetails {
        val id = externalId.toIntOrNull() ?: throw ProviderException.Parse("invalid AniList id")
        val media = mediaById(id)
        val mapped = AniListMapper.toProviderMedia(media, zoneProvider())
            ?: throw ProviderException.Parse("unsupported media")
        val isSeriesFormat = media.format?.uppercase() in seriesFormats
        val chain = if (isSeriesFormat) franchiseChain(media) else listOf(media)
        val seasons = enrichEpisodeTitles(
            seasons = AniListMapper.toFranchiseBundles(chain, zoneProvider()),
            chain = chain,
        )
        // Count announced seasons even when their episode list is still empty.
        val seasonCount = seasons.count { it.season.number != null && it.season.number > 0 }
        val selectedSeason = if (isSeriesFormat) {
            chain.indexOfFirst { it.id == media.id }
                .takeIf { index -> index >= 0 }
                ?.plus(1)
        } else {
            1
        }?.takeIf { number -> seasons.any { it.season.number == number } }
        return ProviderMediaDetails(
            media = franchiseBase(mapped, chain, zoneProvider())
                .copy(totalSeasons = seasonCount.takeIf { it > 1 }),
            seasons = seasons,
            related = AniListMapper.toRelationList(media, zoneProvider()),
            additionalLinks = chain
                .filter { it.id != media.id }
                .map { ProviderLink(provider = ProviderId.ANILIST, externalId = it.id.toString()) },
            selectedSeasonNumber = selectedSeason,
        )
    }

    override suspend fun related(externalId: String): List<ProviderRelation> {
        val media = mediaById(externalId.toIntOrNull() ?: -1)
        return AniListMapper.toRelationList(media, zoneProvider())
    }

    override suspend fun recommendations(externalId: String): List<ProviderMedia> {
        val media = mediaData(
            AniListQueries.RECOMMENDATIONS,
            buildJsonObject { put("id", JsonPrimitive(externalId.toIntOrNull() ?: -1)) },
        )
        return AniListMapper.toRelationList(media, zoneProvider())
            .filter { it.relation == "RECOMMENDATION" }
            .map { it.media }
    }

    /** SEQUEL entries only; the strongest signal for the recommendation deck. */
    suspend fun sequels(externalId: String): List<ProviderMedia> {
        val id = externalId.toIntOrNull() ?: return emptyList()
        val media = mediaData(AniListQueries.SEQUELS, buildJsonObject { put("id", JsonPrimitive(id)) })
        return AniListMapper.toRelationList(media, zoneProvider())
            .filter { it.relation.equals("SEQUEL", ignoreCase = true) }
            .map { it.media }
    }

    /** Genre browse used by the recommendation deck. */
    suspend fun byGenre(genre: String, page: Int = 1): List<ProviderMedia> {
        if (genre.isBlank()) return emptyList()
        val variables = buildJsonObject {
            put("genre", JsonPrimitive(genre))
            put("page", JsonPrimitive(page.coerceAtLeast(1)))
            put("perPage", JsonPrimitive(perPage))
        }
        val result = page(AniListQueries.BY_GENRE, variables)
        return result.media.mapNotNull { AniListMapper.toProviderMedia(it, zoneProvider()) }
    }

    /**
     * Walks PREQUEL/SEQUEL relations and returns only the series entries in the
     * chain, ordered from the earliest season to the latest.
     *
     * Non-series entries (OVA/OAD/MOVIE) are traversed as bridges but not
     * imported as seasons. This matters for franchises such as Slime, where the
     * direct SEQUEL of season one is an OAD whose own sequel is season two.
     * The root's relations only expose direct neighbours, so neighbours are
     * fetched (bounded) to keep walking; results are cached for the process.
     */
    private suspend fun franchiseChain(root: AniListMedia): List<AniListMedia> {
        chainCache[root.id]?.let { return it }
        val collected = linkedMapOf<Int, AniListMedia>()
        if (root.format?.uppercase() in seriesFormats) collected[root.id] = root
        val visited = mutableSetOf(root.id)
        var fetches = 0
        val frontier = ArrayDeque<AniListMedia>().apply { add(root) }
        while (
            frontier.isNotEmpty() &&
            collected.size < MAX_CHAIN_SIZE &&
            visited.size <= MAX_CHAIN_NODES &&
            fetches < MAX_CHAIN_FETCHES
        ) {
            val current = frontier.removeFirst()
            val full = if (current.id == root.id) {
                root
            } else {
                fetches += 1
                try {
                    mediaById(current.id)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    continue
                }.takeIf { it.id > 0 } ?: continue
            }
            if (full.format?.uppercase() in seriesFormats) {
                collected[full.id] = full
            }
            full.relations?.edges.orEmpty().forEach { edge ->
                val node = edge.node ?: return@forEach
                val relation = edge.relationType?.uppercase()
                if (relation !in chainRelations) return@forEach
                if (node.isAdult == true) return@forEach
                if (node.id <= 0 || !visited.add(node.id)) return@forEach
                if (visited.size > MAX_CHAIN_NODES) return@forEach
                frontier += node
                if (node.format?.uppercase() in seriesFormats) {
                    collected[node.id] = node
                }
            }
        }
        val ordered = orderChain(collected.values.toList())
        if (chainCache.size > MAX_CACHED_CHAINS) chainCache.clear()
        ordered.forEach { chainCache[it.id] = ordered }
        return ordered
    }

    /**
     * Presents a multi-entry franchise under the earliest season's name and
     * artwork while keeping the id the user actually selected. That gives the
     * library one stable title ("Mushoku Tensei: Jobless Reincarnation") instead
     * of a later cour's romanised name, and makes external-title matching work.
     */
    private fun franchiseBase(
        selected: ProviderMedia,
        chain: List<AniListMedia>,
        zone: ZoneId,
    ): ProviderMedia {
        if (chain.size <= 1) return selected
        val root = chain.firstOrNull()
            ?.let { AniListMapper.toProviderMedia(it, zone) }
            ?: return selected
        val latestEnd = chain.asReversed()
            .asSequence()
            .mapNotNull { AniListMapper.fuzzyDate(it.endDate) }
            .firstOrNull()
        val titles = chain.flatMap { entry ->
            listOfNotNull(
                entry.title?.english,
                entry.title?.userPreferred,
                entry.title?.romaji,
                entry.title?.native,
            ) + entry.synonyms
        }.filter { it.isNotBlank() }.distinct()
        return selected.copy(
            title = root.title,
            originalTitle = root.originalTitle,
            alternateTitles = (titles - root.title).distinct(),
            synopsis = root.synopsis ?: selected.synopsis,
            posterUrl = root.posterUrl ?: selected.posterUrl,
            backdropUrl = root.backdropUrl ?: selected.backdropUrl,
            releaseDate = root.releaseDate ?: selected.releaseDate,
            endDate = latestEnd ?: selected.endDate,
            runtimeMinutes = root.runtimeMinutes ?: selected.runtimeMinutes,
            totalEpisodes = chain.sumOf { it.episodes ?: 0 }.takeIf { it > 0 } ?: selected.totalEpisodes,
            studio = root.studio ?: selected.studio,
            genres = chain.flatMap { it.genres }.distinct(),
            tags = chain.flatMap { entry -> entry.tags.mapNotNull { it.name } }.distinct(),
            score = root.score ?: selected.score,
            malId = root.malId ?: selected.malId,
        )
    }

    private fun orderChain(nodes: List<AniListMedia>): List<AniListMedia> =
        nodes.sortedWith(
            compareBy<AniListMedia> { AniListMapper.fuzzyDate(it.startDate) ?: LocalDate.MAX }
                .thenBy { it.startDate?.year ?: it.seasonYear ?: Int.MAX_VALUE }
                .thenBy { it.id },
        )

    /**
     * Fills missing episode titles from the secondary source (Jikan). Only
     * seasons that actually miss titles trigger a lookup, and failures are
     * swallowed: a missing title must never fail a details fetch.
     */
    private suspend fun enrichEpisodeTitles(
        seasons: List<ProviderSeasonBundle>,
        chain: List<AniListMedia>,
    ): List<ProviderSeasonBundle> {
        val source = episodeTitleSource ?: return seasons
        var lookupBudget = MAX_EPISODE_TITLE_LOOKUPS
        var sourceUsable = true
        return seasons.map { bundle ->
            if (!sourceUsable || lookupBudget <= 0) return@map bundle
            if (bundle.episodes.all { !it.title.isNullOrBlank() }) return@map bundle
            val entry = bundle.season.number?.minus(1)?.let(chain::getOrNull) ?: return@map bundle
            val malId = entry.idMal?.takeIf { it > 0 } ?: return@map bundle
            lookupBudget -= 1
            val titles = withTimeoutOrNull(EPISODE_TITLE_TIMEOUT_MS) {
                try {
                    source(malId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    emptyMap()
                }
            }
            if (titles.isNullOrEmpty()) {
                // Jikan is optional and frequently unreachable. Do not spend the
                // same timeout again for every remaining season.
                sourceUsable = false
                return@map bundle
            }
            bundle.copy(
                episodes = bundle.episodes.map { episode ->
                    val number = episode.number ?: return@map episode
                    val title = episode.title?.takeIf { it.isNotBlank() }
                        ?: titles[number]?.takeIf { it.isNotBlank() }
                    if (title == null) episode else episode.copy(title = title)
                },
            )
        }
    }

    private companion object {
        const val MAX_CHAIN_SIZE = 16
        const val MAX_CHAIN_FETCHES = 8
        const val MAX_CHAIN_NODES = 32
        const val MAX_EPISODE_TITLE_LOOKUPS = 2
        const val EPISODE_TITLE_TIMEOUT_MS = 1_500L
        const val MAX_CACHED_CHAINS = 64
    }
}
