package com.seenly.app.data.repository

import com.seenly.app.core.common.AppClock
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.core.network.toAppError
import com.seenly.app.core.time.IsoDates.toLocalDateOrNull
import com.seenly.app.data.remote.anilist.AniListProvider
import com.seenly.app.data.remote.cinemeta.CinemetaProvider
import com.seenly.app.domain.model.MediaCategory
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.model.SmartList
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.providers.MetadataProvider
import com.seenly.app.domain.providers.Paged
import com.seenly.app.domain.providers.ProviderInfo
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import com.seenly.app.domain.repository.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.Locale

/**
 * Provider-facing catalog operations. All provider failures are converted into
 * [AppError]s; no screen ever receives a raw HTTP exception. Imports are
 * idempotent through the unique (provider, externalId) index.
 *
 * Keyless providers are routed by category (anime stays on AniList/Jikan;
 * movies and series go to Cinemeta/TVmaze) and results are deduplicated across
 * sources by normalized title + year, preferring the higher-priority provider.
 */
class CatalogRepositoryImpl(
    private val db: SeenlyDatabase,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
    providers: List<MetadataProvider>,
    private val enricher: MetadataEnricher? = null,
    /** App-lifetime scope for non-blocking metadata enrichment after an import. */
    private val backgroundScope: CoroutineScope? = null,
) : CatalogRepository {

    private val mediaDao = db.mediaDao()
    private val progressDao = db.progressDao()
    private val listDao = db.listDao()

    private val providers: Map<ProviderId, MetadataProvider> = providers.associateBy { it.id }

    override fun providerInfos(): List<ProviderInfo> = PROVIDER_ORDER.mapNotNull { id ->
        providers[id]?.let { provider ->
            ProviderInfo(
                id = provider.id,
                displayName = provider.displayName,
                requiresCredential = provider.requiresCredential,
                configured = provider.isConfigured(),
                attributionKey = provider.attributionKey,
            )
        }
    }

    override suspend fun search(
        query: String,
        provider: ProviderId?,
        type: MediaType?,
        page: Int,
    ): Outcome<Paged<ProviderMedia>> = withContext(dispatchers.io) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Outcome.Success(Paged(emptyList(), page, false))
        call {
            if (provider != null) {
                val source = providers[provider]
                    ?: return@call Outcome.Failure(AppError.Invalid("provider_unavailable"))
                if (!source.isConfigured()) {
                    return@call Outcome.Failure(AppError.Auth("provider_not_configured"))
                }
                return@call Outcome.Success(source.search(q, type, page.coerceAtLeast(1)))
            }
            val sources = routedProviders(type)
            if (sources.isEmpty()) {
                return@call Outcome.Failure(AppError.Invalid("provider_unavailable"))
            }
            val result = queryInParallel(sources, page.coerceAtLeast(1)) { source, requestedPage ->
                source.search(q, type, requestedPage)
            }
            if (!result.anySuccess) {
                return@call Outcome.Failure(AppError.Unknown(null))
            }
            Outcome.Success(
                Paged(
                    items = dedupe(result.items),
                    page = page.coerceAtLeast(1),
                    hasMore = result.hasMore,
                ),
            )
        }
    }

    override suspend fun trending(
        provider: ProviderId,
        type: MediaType?,
        page: Int,
    ): Outcome<Paged<ProviderMedia>> = withContext(dispatchers.io) {
        call {
            val source = providers[provider]
                ?: return@call Outcome.Failure(AppError.Invalid("provider_unavailable"))
            if (!source.isConfigured()) {
                return@call Outcome.Failure(AppError.Auth("provider_not_configured"))
            }
            Outcome.Success(source.trending(type, page.coerceAtLeast(1)))
        }
    }

    override suspend fun trendingAll(type: MediaType?, page: Int): Outcome<Paged<ProviderMedia>> =
        withContext(dispatchers.io) {
            call {
                val sources = routedProviders(type)
                if (sources.isEmpty()) {
                    return@call Outcome.Failure(AppError.Invalid("provider_unavailable"))
                }
                val result = queryInParallel(sources, page.coerceAtLeast(1)) { source, requestedPage ->
                    source.trending(type, requestedPage)
                }
                if (!result.anySuccess) {
                    return@call Outcome.Failure(AppError.Unknown(null))
                }
                Outcome.Success(
                    Paged(dedupe(result.items), page.coerceAtLeast(1), result.hasMore),
                )
            }
        }

    override suspend fun upcoming(
        provider: ProviderId,
        from: LocalDate,
        to: LocalDate,
    ): Outcome<List<ProviderMedia>> = withContext(dispatchers.io) {
        call {
            val source = providers[provider]
                ?: return@call Outcome.Failure(AppError.Invalid("provider_unavailable"))
            if (!source.isConfigured()) {
                return@call Outcome.Failure(AppError.Auth("provider_not_configured"))
            }
            Outcome.Success(source.upcoming(from, to))
        }
    }

    override suspend fun seasonal(
        provider: ProviderId,
        year: Int,
        season: String,
        page: Int,
    ): Outcome<Paged<ProviderMedia>> = withContext(dispatchers.io) {
        call {
            val source = providers[provider]
                ?: return@call Outcome.Failure(AppError.Invalid("provider_unavailable"))
            if (!source.isConfigured()) {
                return@call Outcome.Failure(AppError.Auth("provider_not_configured"))
            }
            val result = source.seasonal(year, season, page.coerceAtLeast(1))
            if (result != null) {
                Outcome.Success(result)
            } else if (provider == ProviderId.ANILIST) {
                // Jikan is a secondary fallback when AniList is unavailable.
                val fallback = providers[ProviderId.JIKAN]
                val fallbackResult = fallback?.seasonal(year, season, page)
                if (fallbackResult != null) Outcome.Success(fallbackResult)
                else Outcome.Failure(AppError.Invalid("unsupported"))
            } else {
                Outcome.Failure(AppError.Invalid("unsupported"))
            }
        }
    }

    override suspend fun details(
        provider: ProviderId,
        externalId: String,
    ): Outcome<ProviderMediaDetails> = withContext(dispatchers.io) {
        call {
            val source = providers[provider]
                ?: return@call Outcome.Failure(AppError.Invalid("provider_unavailable"))
            if (!source.isConfigured()) {
                return@call Outcome.Failure(AppError.Auth("provider_not_configured"))
            }
            val details = withTimeoutOrNull(PREVIEW_DETAILS_TIMEOUT_MS) {
                source.details(externalId)
            } ?: return@call Outcome.Failure(AppError.Unavailable)
            Outcome.Success(previewDetails(details))
        }
    }

    override suspend fun importTitle(
        provider: ProviderId,
        externalId: String,
        linkToMediaId: Long?,
    ): Outcome<Long> = withContext(dispatchers.io) {
        call {
            val source = providers[provider]
                ?: return@call Outcome.Failure(AppError.Invalid("provider_unavailable"))
            if (!source.isConfigured()) {
                return@call Outcome.Failure(AppError.Auth("provider_not_configured"))
            }
            // Persist provider metadata immediately. Ratings, episode titles and
            // episode dates are best-effort and must not keep the Add button
            // spinning (Jikan can be unreachable for long periods).
            val details = withTimeoutOrNull(IMPORT_DETAILS_TIMEOUT_MS) {
                source.details(externalId)
            } ?: return@call Outcome.Failure(AppError.Unavailable)
            val mediaId = ProviderImport.persistDetails(
                db = db,
                details = details,
                now = clock.nowMillis(),
                linkToMediaId = linkToMediaId,
            )
            launchEnrichment(mediaId, details)
            Outcome.Success(mediaId)
        }
    }

    override suspend fun refresh(mediaId: Long): Outcome<Unit> = withContext(dispatchers.io) {
        val links = mediaDao.providerIdsFor(mediaId)
        if (links.isEmpty()) {
            return@withContext Outcome.Failure(AppError.Invalid("no_provider_link"))
        }
        var lastError: AppError? = null
        for (link in links.sortedBy { providerOrder(ProviderId.fromStorage(it.provider)) }) {
            val id = ProviderId.fromStorage(link.provider) ?: continue
            val source = providers[id] ?: continue
            if (!source.isConfigured()) {
                lastError = AppError.Auth("provider_not_configured")
                continue
            }
            try {
                val details = withTimeoutOrNull(IMPORT_DETAILS_TIMEOUT_MS) {
                    source.details(link.externalId)
                } ?: run {
                    lastError = AppError.Unavailable
                    continue
                }
                ProviderImport.persistDetails(
                    db = db,
                    details = details,
                    now = clock.nowMillis(),
                    linkToMediaId = mediaId,
                )
                launchEnrichment(mediaId, details)
                return@withContext Outcome.Success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                lastError = failure.toAppError()
            }
        }
        Outcome.Failure(lastError ?: AppError.Invalid("no_provider_link"))
    }

    override fun observeLibraryMatch(provider: ProviderId, externalId: String): Flow<Long?> =
        mediaDao.observeLocalMediaId(provider.name, externalId).flowOn(dispatchers.io)

    override suspend fun providerRecommendations(mediaId: Long): Outcome<List<ProviderMedia>> =
        withContext(dispatchers.io) {
            val links = mediaDao.providerIdsFor(mediaId)
            if (links.isEmpty()) {
                return@withContext Outcome.Failure(AppError.Invalid("no_provider_link"))
            }
            var lastError: AppError? = null
            for (link in links) {
                val id = ProviderId.fromStorage(link.provider) ?: continue
                val source = providers[id] ?: continue
                if (!source.isConfigured()) {
                    lastError = AppError.Auth("provider_not_configured")
                    continue
                }
                try {
                    val recommendations = source.recommendations(link.externalId)
                    if (recommendations.isNotEmpty()) {
                        val filtered = recommendations
                            .distinctBy { it.provider to it.externalId }
                            .filter { candidate ->
                                mediaDao.findProviderId(
                                    candidate.provider.name,
                                    candidate.externalId,
                                ) == null
                            }
                        return@withContext Outcome.Success(filtered)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    lastError = failure.toAppError()
                }
            }
            if (lastError != null) Outcome.Failure(lastError!!) else Outcome.Success(emptyList())
        }

    override suspend fun findPotentialLocalMatch(media: ProviderMedia): Long? =
        withContext(dispatchers.io) {
            val existing = mediaDao.findByTitleAndType(media.title, media.type.name) ?: return@withContext null
            if (existing.locallyEdited) return@withContext null
            val localYear = existing.releaseDate.toLocalDateOrNull()?.year
            val remoteYear = media.releaseDate?.year
            if (localYear != null && remoteYear != null && localYear != remoteYear) {
                return@withContext null
            }
            existing.id
        }

    /**
     * Taste-driven candidates for the recommendation deck: sequels of titles the
     * user finished or rated highly, then popular titles in the genres they
     * like. Falls back to current provider trending when the taste profile is
     * empty (fresh install) or nothing matched. Library titles are never
     * suggested back.
     */
    override suspend fun recommendationCandidates(limit: Int): Outcome<List<ProviderMedia>> =
        withContext(dispatchers.io) {
            call {
                val profile = tasteProfile()
                Outcome.Success(collectCandidates(profile, limit))
            }
        }

    override suspend fun refreshAiringForWatchedTitles(limit: Int) = withContext(dispatchers.io) {
        val watching = progressDao.byStatus(WatchStatus.WATCHING.name).take(limit.coerceAtLeast(0))
        watching.forEach { progress ->
            try {
                refresh(progress.mediaId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Background refresh is best-effort; stale metadata is harmless.
            }
        }
    }

    // --- Recommendation deck internals ---

    private class TasteProfile(
        val genres: List<String>,
        val sequelSeeds: List<SequelSeed>,
        val prefersSeries: Boolean?,
        val hasAnime: Boolean,
        val hasGeneral: Boolean,
        val excludeLinks: Set<Pair<ProviderId, String>>,
        val excludeKeys: Set<String>,
    )

    private data class SequelSeed(
        val provider: ProviderId,
        val externalId: String,
        val title: String,
        val year: Int?,
        val hasEpisodes: Boolean,
    )

    private suspend fun tasteProfile(): TasteProfile {
        val media = mediaDao.getAll()
        val completed = progressDao.byStatus(WatchStatus.COMPLETED.name).map { it.mediaId }.toSet()
        val watching = progressDao.byStatus(WatchStatus.WATCHING.name).map { it.mediaId }.toSet()
        val favorites = listDao.findSmartList(SmartList.FAVORITES.name)
            ?.let { listDao.orderedMediaIds(it.id).toSet() }
            .orEmpty()

        val genreWeights = mutableMapOf<String, Double>()
        val signalIds = linkedSetOf<Long>()
        media.forEach { row ->
            val isFavorite = row.id in favorites
            val isCompleted = row.id in completed
            val isWatching = row.id in watching
            val rating = row.personalRating
            if (!isFavorite && !isCompleted && !isWatching &&
                (rating == null || rating < TASTE_RATING)
            ) {
                return@forEach
            }
            signalIds += row.id
            var weight = 0.0
            if (isFavorite) weight += FAVORITE_BOOST
            if (isCompleted) weight += COMPLETED_BOOST
            if (isWatching) weight += WATCHING_BOOST
            if (rating != null && rating >= TASTE_RATING) weight += rating - 5.0
            mediaDao.genresForMedia(row.id).forEach { genre ->
                if (genre.isNotBlank()) genreWeights[genre] = (genreWeights[genre] ?: 0.0) + weight
            }
        }

        val genres = genreWeights.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(MAX_TASTE_GENRES)

        val sequelSeeds = media
            .filter { row -> row.id in signalIds }
            .sortedByDescending { it.lastModifiedAt }
            .mapNotNull { row ->
                val type = MediaType.fromStorage(row.type) ?: return@mapNotNull null
                // A franchise now stores every cour as an additional provider
                // link. Only the primary identity gets a sequel lookup, otherwise
                // one five-season import would fan out five identical requests.
                val link = mediaDao.providerIdsFor(row.id).firstOrNull { candidate ->
                    val provider = ProviderId.fromStorage(candidate.provider)
                    provider == ProviderId.ANILIST ||
                        provider == ProviderId.CINEMETA ||
                        provider == ProviderId.TVMAZE
                } ?: return@mapNotNull null
                val provider = ProviderId.fromStorage(link.provider) ?: return@mapNotNull null
                SequelSeed(
                    provider = provider,
                    externalId = link.externalId,
                    title = row.title,
                    year = row.releaseDate.toLocalDateOrNull()?.year,
                    hasEpisodes = type.hasEpisodes,
                )
            }
            .distinctBy { it.provider to it.externalId }
            .take(MAX_SEQUEL_SEEDS)

        val types = media.mapNotNull { MediaType.fromStorage(it.type) }
        val hasAnime = types.any { it.category == MediaCategory.ANIME }
        val hasGeneral = types.any { it.category != MediaCategory.ANIME }
        val seriesCount = types.count { it.hasEpisodes }
        val prefersSeries = when {
            types.isEmpty() -> null
            seriesCount * 2 >= types.size -> true
            else -> false
        }

        val excludeLinks = mutableSetOf<Pair<ProviderId, String>>()
        media.forEach { row ->
            mediaDao.providerIdsFor(row.id).forEach { link ->
                ProviderId.fromStorage(link.provider)?.let { excludeLinks += it to link.externalId }
            }
        }
        val excludeKeys = media.map { row ->
            libraryKey(
                title = row.title,
                year = row.releaseDate.toLocalDateOrNull()?.year,
                hasEpisodes = MediaType.fromStorage(row.type)?.hasEpisodes == true,
            )
        }.toSet()

        return TasteProfile(
            genres = genres,
            sequelSeeds = sequelSeeds,
            prefersSeries = prefersSeries,
            hasAnime = hasAnime,
            hasGeneral = hasGeneral,
            excludeLinks = excludeLinks,
            excludeKeys = excludeKeys,
        )
    }

    private suspend fun collectCandidates(profile: TasteProfile, limit: Int): List<ProviderMedia> {
        val anilist = providers[ProviderId.ANILIST] as? AniListProvider
        val cinemeta = providers[ProviderId.CINEMETA] as? CinemetaProvider
        val collected = mutableListOf<ProviderMedia>()
        val sequelKeys = mutableSetOf<Pair<ProviderId, String>>()

        // Provider requests are independent and each has its own rate limiter.
        // Run them together so a slow source cannot add seconds per genre.
        val sequelResults = coroutineScope {
            profile.sequelSeeds.map { seed ->
                async { sequelsForSeed(seed, anilist) }
            }.awaitAll()
        }
        sequelResults.flatten().forEach { sequel ->
            sequelKeys += sequel.provider to sequel.externalId
            collected += sequel
        }

        val genreQueries = buildList<suspend () -> List<ProviderMedia>> {
            profile.genres.forEach { genre ->
                if (profile.hasAnime && anilist != null) {
                    add { guardList { anilist.byGenre(genre) } }
                }
                if (profile.hasGeneral && cinemeta != null) {
                    if (profile.prefersSeries != false) {
                        add { guardList { cinemeta.byGenre(genre, isSeries = true) } }
                    }
                    if (profile.prefersSeries != true) {
                        add { guardList { cinemeta.byGenre(genre, isSeries = false) } }
                    }
                }
            }
        }
        val genreResults = coroutineScope {
            genreQueries.map { query -> async { query() } }.awaitAll()
        }
        genreResults.flatten().forEach { collected += it }

        var candidates = filterCandidates(collected, profile)

        // Empty shelf (fresh install) or no taste match: show what is popular now.
        if (candidates.isEmpty()) {
            val fallbackType = when (profile.prefersSeries) {
                true -> MediaType.TV_SERIES
                false -> MediaType.MOVIE
                null -> null
            }
            val fallback = queryInParallel(
                sources = routedProviders(fallbackType),
                page = 1,
            ) { source, requestedPage ->
                source.trending(fallbackType, requestedPage)
            }.items
            candidates = filterCandidates(fallback, profile)
        }

        return candidates
            .sortedWith(
                compareByDescending<ProviderMedia> { it.provider to it.externalId in sequelKeys }
                    .thenByDescending { it.score ?: 0.0 }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
            .take(limit.coerceIn(1, MAX_CANDIDATES))
    }

    private fun filterCandidates(
        items: List<ProviderMedia>,
        profile: TasteProfile,
    ): List<ProviderMedia> = items
        .distinctBy { it.provider to it.externalId }
        .filterNot { it.provider to it.externalId in profile.excludeLinks }
        .filterNot { candidateKey(it) in profile.excludeKeys }
        .distinctBy { candidateKey(it) }

    /**
     * A general-content sequel is a later title whose normalized name extends
     * the watched title (e.g. "Dune" -> "Dune: Part Two"). Remakes with an
     * identical title are deliberately not treated as sequels.
     */
    private fun isSequelOf(candidate: ProviderMedia, seed: SequelSeed): Boolean {
        val seedYear = seed.year ?: return false
        val candidateYear = candidate.releaseDate?.year ?: return false
        if (candidateYear <= seedYear) return false
        val seedTitle = normalizedTitle(seed.title)
        if (seedTitle.isEmpty()) return false
        return normalizedTitle(candidate.title).startsWith("$seedTitle ")
    }

    private suspend fun <T> guardList(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        emptyList()
    }

    private suspend fun sequelsForSeed(
        seed: SequelSeed,
        anilist: AniListProvider?,
    ): List<ProviderMedia> = when (seed.provider) {
        ProviderId.ANILIST -> {
            if (anilist == null) emptyList()
            else guardList { anilist.sequels(seed.externalId) }
        }

        ProviderId.CINEMETA, ProviderId.TVMAZE -> {
            val source = providers[seed.provider] ?: return emptyList()
            val type = if (seed.hasEpisodes) MediaType.TV_SERIES else MediaType.MOVIE
            guardList {
                source.search(seed.title, type, 1).items
                    .filter { candidate -> isSequelOf(candidate, seed) }
            }
        }

        else -> emptyList()
    }

    private data class QueryBatch(
        val items: List<ProviderMedia>,
        val hasMore: Boolean,
        val anySuccess: Boolean,
    )

    /** Fans out a merged query; one slow provider cannot serialize the rest. */
    private suspend fun queryInParallel(
        sources: List<MetadataProvider>,
        page: Int,
        timeoutMillis: Long = MERGED_PROVIDER_TIMEOUT_MS,
        block: suspend (MetadataProvider, Int) -> Paged<ProviderMedia>,
    ): QueryBatch = coroutineScope {
        val results = sources.map { source ->
            async {
                try {
                    val result = withTimeoutOrNull(timeoutMillis) { block(source, page) }
                    if (result == null) {
                        QueryBatch(emptyList(), false, false)
                    } else {
                        QueryBatch(result.items, result.hasMore, true)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    QueryBatch(emptyList(), false, false)
                }
            }
        }.awaitAll()
        QueryBatch(
            items = results.flatMap { it.items },
            hasMore = results.any { it.hasMore },
            anySuccess = results.any { it.anySuccess },
        )
    }

    /**
     * Providers that can answer for the given category. AniList is the primary
     * anime source; Jikan is only a fallback when AniList is not configured.
     * Keeping Jikan out of merged browsing matters because it is optional and
     * can be unreachable without affecting the rest of the catalog.
     */
    private fun routedProviders(type: MediaType?): List<MetadataProvider> {
        val configured = providers.values.filter { it.isConfigured() }
        if (type == null) return configured.filter { it.id != ProviderId.JIKAN }
        return when (type.category) {
            MediaCategory.ANIME -> {
                val anilist = configured.filter { it.id == ProviderId.ANILIST }
                anilist.ifEmpty { configured.filter { it.id == ProviderId.JIKAN } }
            }

            else -> configured.filter { it.id == ProviderId.CINEMETA || it.id == ProviderId.TVMAZE }
        }
    }

    /**
     * Cross-provider dedupe: identical (normalized title, year, kind) rows keep
     * only the highest-priority provider's copy.
     */
    private fun dedupe(items: List<ProviderMedia>): List<ProviderMedia> =
        items
            .sortedBy { providerOrder(it.provider) }
            .distinctBy { candidateKey(it) }

    private fun candidateKey(media: ProviderMedia): String = libraryKey(
        title = media.title,
        year = media.releaseDate?.year,
        hasEpisodes = media.type.hasEpisodes,
    )

    private fun libraryKey(title: String, year: Int?, hasEpisodes: Boolean): String =
        "${normalizedTitle(title)}|${year ?: 0}|$hasEpisodes"

    private fun normalizedTitle(title: String): String =
        title.lowercase(Locale.ROOT).replace(NON_ALPHANUMERIC, " ").trim()

    /**
     * Runs a provider block and converts failures. Used instead of runCatching so
     * coroutine cancellation is never swallowed.
     */
    private suspend inline fun <T> call(block: () -> Outcome<T>): Outcome<T> = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Outcome.Failure(failure.toAppError())
    }

    /**
     * Previews are interactive, so they only get a short ratings budget. Full
     * episode/rating enrichment runs after import or refresh instead of blocking
     * the provider preview.
     */
    private suspend fun previewDetails(details: ProviderMediaDetails): ProviderMediaDetails {
        val source = enricher ?: return details
        val media = withTimeoutOrNull(PREVIEW_ENRICH_TIMEOUT_MS) {
            source.enrich(
                details.media,
                timeoutMillis = PREVIEW_ENRICH_TIMEOUT_MS,
                includeScrapedRatings = false,
            )
        } ?: details.media
        return details.copy(media = media)
    }

    /**
     * Fills episode titles, air dates and ratings in the app scope after the
     * import has already been persisted and returned to the UI.
     */
    private fun launchEnrichment(mediaId: Long, details: ProviderMediaDetails) {
        val scope = backgroundScope ?: return
        val source = enricher ?: return
        scope.launch {
            try {
                if (mediaDao.getById(mediaId) == null) return@launch
                val withEpisodes = withTimeoutOrNull(BACKGROUND_EPISODES_TIMEOUT_MS) {
                    source.enrichEpisodes(details)
                } ?: details
                if (mediaDao.getById(mediaId) == null) return@launch
                ProviderImport.persistDetails(
                    db = db,
                    details = withEpisodes,
                    now = clock.nowMillis(),
                    linkToMediaId = mediaId,
                )
                val enrichedMedia = withTimeoutOrNull(BACKGROUND_RATINGS_TIMEOUT_MS) {
                    source.enrich(withEpisodes.media, timeoutMillis = BACKGROUND_RATINGS_TIMEOUT_MS)
                } ?: return@launch
                if (enrichedMedia == withEpisodes.media) return@launch
                if (mediaDao.getById(mediaId) == null) return@launch
                ProviderImport.persistDetails(
                    db = db,
                    details = withEpisodes.copy(media = enrichedMedia),
                    now = clock.nowMillis(),
                    linkToMediaId = mediaId,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Best effort: the core import has already succeeded.
            }
        }
    }

    private fun providerOrder(id: ProviderId?): Int = when (id) {
        ProviderId.ANILIST -> 0
        ProviderId.CINEMETA -> 1
        ProviderId.TVMAZE -> 2
        ProviderId.JIKAN -> 3
        else -> 4
    }

    private companion object {
        val PROVIDER_ORDER = listOf(
            ProviderId.ANILIST,
            ProviderId.CINEMETA,
            ProviderId.JIKAN,
            ProviderId.TVMAZE,
        )
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        const val TASTE_RATING = 7.0
        const val FAVORITE_BOOST = 4.0
        const val COMPLETED_BOOST = 3.0
        const val WATCHING_BOOST = 1.5
        const val MAX_TASTE_GENRES = 3
        const val MAX_SEQUEL_SEEDS = 3
        const val MAX_CANDIDATES = 60
        const val MERGED_PROVIDER_TIMEOUT_MS = 10_000L
        const val PREVIEW_DETAILS_TIMEOUT_MS = 15_000L
        const val IMPORT_DETAILS_TIMEOUT_MS = 20_000L
        const val PREVIEW_ENRICH_TIMEOUT_MS = 1_500L
        const val BACKGROUND_EPISODES_TIMEOUT_MS = 8_000L
        const val BACKGROUND_RATINGS_TIMEOUT_MS = 14_000L
    }
}
