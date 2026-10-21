package com.seenly.app.ui.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RecommendationDeckUiState(
    val loading: Boolean = true,
    val cards: List<ProviderMedia> = emptyList(),
    val busy: Boolean = false,
    val error: AppError? = null,
    val addedTitle: String? = null,
    val addedMediaId: Long? = null,
    /** Provider details opened from the artwork or title of a deck card. */
    val selectedCard: ProviderMedia? = null,
    val selectedDetails: ProviderMediaDetails? = null,
    val selectedLoading: Boolean = false,
    val selectedError: AppError? = null,
)

/** Stable identity of a provider candidate, also used for persisted dismissals. */
val ProviderMedia.deckKey: String
    get() = "${provider.name}/$externalId"

/**
 * Loads taste-driven candidates and owns the deck actions: a like imports the
 * title into the library, a dislike removes it and remembers the dismissal so
 * the same title is not suggested again.
 */
class RecommendationDeckViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(RecommendationDeckUiState())
    val state = _state.asStateFlow()

    /** Serializes dismissal writes so rapid swipes cannot overwrite each other. */
    private val dismissalMutex = Mutex()

    private var detailsJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val dismissed = runCatching {
                container.settingsRepository.current().deckDismissedCandidates
            }.getOrDefault(emptySet())
            when (val result = container.catalogRepository.recommendationCandidates(limit = DECK_CANDIDATES)) {
                is Outcome.Success -> _state.update { current ->
                    current.copy(
                        loading = false,
                        cards = result.value
                            .distinctBy { it.deckKey }
                            .filterNot { it.deckKey in dismissed },
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
    }

    fun like(card: ProviderMedia) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update {
                it.copy(busy = true, error = null, addedTitle = null, addedMediaId = null)
            }
            when (val result = container.catalogRepository.importTitle(card.provider, card.externalId)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        busy = false,
                        cards = it.cards.filterNot { candidate -> candidate.deckKey == card.deckKey },
                        addedTitle = card.title,
                        addedMediaId = result.value,
                        selectedCard = null,
                        selectedDetails = null,
                        selectedLoading = false,
                        selectedError = null,
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        busy = false,
                        error = result.error,
                        selectedError = if (it.selectedCard?.deckKey == card.deckKey) {
                            result.error
                        } else {
                            it.selectedError
                        },
                    )
                }
            }
        }
    }

    /** Loads provider details without importing the title. */
    fun openDetails(card: ProviderMedia) {
        detailsJob?.cancel()
        _state.update {
            it.copy(
                selectedCard = card,
                selectedDetails = null,
                selectedLoading = true,
                selectedError = null,
            )
        }
        detailsJob = viewModelScope.launch {
            when (val result = container.catalogRepository.details(card.provider, card.externalId)) {
                is Outcome.Success -> _state.update {
                    if (it.selectedCard?.deckKey != card.deckKey) it
                    else it.copy(
                        selectedDetails = result.value,
                        selectedLoading = false,
                        selectedError = null,
                    )
                }
                is Outcome.Failure -> _state.update {
                    if (it.selectedCard?.deckKey != card.deckKey) it
                    else it.copy(
                        selectedLoading = false,
                        selectedError = result.error,
                    )
                }
            }
        }
    }

    fun closeDetails() {
        detailsJob?.cancel()
        _state.update {
            it.copy(
                selectedCard = null,
                selectedDetails = null,
                selectedLoading = false,
                selectedError = null,
            )
        }
    }

    fun dismissSelectedError() {
        _state.update { it.copy(selectedError = null) }
    }

    fun dislike(card: ProviderMedia) {
        if (_state.value.busy) return
        _state.update {
            it.copy(cards = it.cards.filterNot { candidate -> candidate.deckKey == card.deckKey })
        }
        viewModelScope.launch {
            dismissalMutex.withLock {
                runCatching {
                    val dismissed = container.settingsRepository.current().deckDismissedCandidates
                    container.settingsRepository.setDeckDismissedCandidates(dismissed + card.deckKey)
                }
            }
        }
    }

    fun dismissAdded() {
        _state.update { it.copy(addedTitle = null, addedMediaId = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private companion object {
        const val DECK_CANDIDATES = 30
    }
}
