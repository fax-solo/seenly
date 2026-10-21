package com.seenly.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.model.MediaCategory
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DiscoverUiState(
    val category: MediaCategory? = null,
    val query: String = "",
    val results: List<ProviderMedia> = emptyList(),
    val seasonal: List<ProviderMedia> = emptyList(),
    val isSearch: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 1,
    val error: AppError? = null,
    val selected: ProviderMedia? = null,
    val selectedDetails: ProviderMediaDetails? = null,
    val selectedLoading: Boolean = false,
    val selectedLocalId: Long? = null,
    val importInProgress: Boolean = false,
    val importError: AppError? = null,
    val linkCandidate: ProviderMedia? = null,
    val linkCandidateLocalId: Long? = null,
    val importedMediaId: Long? = null,
)

class DiscoverViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state = _state.asStateFlow()

    private var mode: Mode = Mode.TRENDING
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        loadTrending(reset = true)
        loadSeasonal()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) {
            loadTrending(reset = true)
        } else {
            mode = Mode.SEARCH
            loadPage(page = 1, reset = true)
        }
    }

    /** Re-runs the current query or trending feed from the first page. */
    fun refresh() = search()

    fun selectCategory(category: MediaCategory?) {
        _state.update { it.copy(category = category) }
        if (_state.value.query.isBlank()) loadTrending(reset = true) else search()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return
        loadPage(page = current.page + 1, reset = false)
    }

    fun openDetails(item: ProviderMedia) {
        _state.update {
            it.copy(
                selected = item,
                selectedDetails = null,
                selectedLoading = true,
                selectedLocalId = null,
                importError = null,
            )
        }
        viewModelScope.launch {
            val localId = runCatching {
                container.catalogRepository
                    .observeLibraryMatch(item.provider, item.externalId)
                    .first()
            }.getOrNull()
            _state.update { it.copy(selectedLocalId = localId) }
            when (val result = container.catalogRepository.details(item.provider, item.externalId)) {
                is Outcome.Success -> _state.update {
                    it.copy(selectedDetails = result.value, selectedLoading = false)
                }
                is Outcome.Failure -> _state.update {
                    it.copy(selectedLoading = false, importError = result.error)
                }
            }
        }
    }

    fun closeDetails() {
        _state.update {
            it.copy(
                selected = null,
                selectedDetails = null,
                selectedLoading = false,
                selectedLocalId = null,
                importError = null,
                linkCandidate = null,
                linkCandidateLocalId = null,
            )
        }
    }

    fun requestImport() {
        val current = _state.value
        val item = current.selected ?: return
        if (current.selectedLocalId != null) {
            _state.update { it.copy(importedMediaId = current.selectedLocalId) }
            return
        }
        viewModelScope.launch {
            val existing = runCatching {
                container.catalogRepository
                    .observeLibraryMatch(item.provider, item.externalId)
                    .first()
            }.getOrNull()
            if (existing != null) {
                _state.update { it.copy(selectedLocalId = existing, importedMediaId = existing) }
                return@launch
            }
            val match = container.catalogRepository.findPotentialLocalMatch(item)
            if (match != null) {
                _state.update { it.copy(linkCandidate = item, linkCandidateLocalId = match) }
            } else {
                import(item, linkToMediaId = null)
            }
        }
    }

    fun confirmLink(link: Boolean) {
        val current = _state.value
        val item = current.linkCandidate ?: return
        val localId = current.linkCandidateLocalId
        _state.update { it.copy(linkCandidate = null, linkCandidateLocalId = null) }
        import(item, linkToMediaId = if (link) localId else null)
    }

    fun dismissLinkDialog() {
        _state.update { it.copy(linkCandidate = null, linkCandidateLocalId = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun dismissImportError() {
        _state.update { it.copy(importError = null) }
    }

    fun consumeImported() {
        _state.update { it.copy(importedMediaId = null) }
    }

    private fun import(item: ProviderMedia, linkToMediaId: Long?) {
        _state.update { it.copy(importInProgress = true, importError = null) }
        viewModelScope.launch {
            when (val result = container.catalogRepository.importTitle(
                provider = item.provider,
                externalId = item.externalId,
                linkToMediaId = linkToMediaId,
            )) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        importInProgress = false,
                        selectedLocalId = result.value,
                        importedMediaId = result.value,
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(importInProgress = false, importError = result.error)
                }
            }
        }
    }

    private fun loadTrending(reset: Boolean) {
        mode = Mode.TRENDING
        loadPage(page = 1, reset = reset)
    }

    private fun loadPage(page: Int, reset: Boolean) {
        val snapshot = _state.value
        val requestMode = mode
        val generation = ++loadGeneration
        // A filter change must immediately invalidate an older response,
        // otherwise a slow request can overwrite the newly selected feed.
        loadJob?.cancel()
        _state.update {
            it.copy(
                loading = reset,
                loadingMore = !reset,
                error = null,
                results = if (reset) emptyList() else it.results,
                hasMore = if (reset) false else it.hasMore,
                isSearch = requestMode == Mode.SEARCH,
                page = if (reset) 1 else it.page,
            )
        }
        loadJob = viewModelScope.launch {
            val category = snapshot.category
            val providerType = typeForCategory(category)
            val result = if (requestMode == Mode.SEARCH) {
                container.catalogRepository.search(
                    query = snapshot.query.trim(),
                    provider = null,
                    type = providerType,
                    page = page,
                )
            } else {
                container.catalogRepository.trendingAll(type = providerType, page = page)
            }
            if (generation != loadGeneration) return@launch

            _state.update { current ->
                if (generation != loadGeneration) {
                    current
                } else {
                    when (result) {
                        is Outcome.Success -> {
                            val filtered = result.value.items
                                .distinctBy { it.provider to it.externalId }
                                .filter { candidate ->
                                    category == null || candidate.type.category == category
                                }
                            current.copy(
                                loading = false,
                                loadingMore = false,
                                results = if (reset) filtered else current.results + filtered,
                                hasMore = result.value.hasMore,
                                page = page,
                                error = null,
                            )
                        }
                        is Outcome.Failure -> current.copy(
                            loading = false,
                            loadingMore = false,
                            error = result.error,
                        )
                    }
                }
            }
        }
    }

    private fun loadSeasonal() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val season = when (today.monthValue) {
                in 1..3 -> "WINTER"
                in 4..6 -> "SPRING"
                in 7..9 -> "SUMMER"
                else -> "FALL"
            }
            when (val result = container.catalogRepository.seasonal(
                provider = ProviderId.ANILIST,
                year = today.year,
                season = season,
                page = 1,
            )) {
                is Outcome.Success -> _state.update { it.copy(seasonal = result.value.items) }
                is Outcome.Failure -> Unit
            }
        }
    }

    private fun typeForCategory(category: MediaCategory?): MediaType? = when (category) {
        null -> null
        MediaCategory.ANIME -> MediaType.ANIME_SERIES
        MediaCategory.MOVIES -> MediaType.MOVIE
        MediaCategory.SERIES -> MediaType.TV_SERIES
        MediaCategory.CARTOONS -> MediaType.CARTOON_SERIES
    }

    private enum class Mode { TRENDING, SEARCH }
}
