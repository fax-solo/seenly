package com.seenly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.model.ActivityItem
import com.seenly.app.domain.model.ContinueWatchingItem
import com.seenly.app.domain.model.HomeSectionKey
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.UpcomingEpisode
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.statistics.StatisticsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val sections: List<HomeSectionKey> = HomeSectionKey.entries.toList(),
    val stats: StatisticsSnapshot = StatisticsSnapshot(),
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val upcoming: List<UpcomingEpisode> = emptyList(),
    val recent: List<ActivityItem> = emptyList(),
    val watchlist: List<LibraryEntry> = emptyList(),
    val librarySize: Int = 0,
    val error: AppError? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        val feeds = combine(
            container.statisticsRepository.observeContinueWatching(12),
            container.statisticsRepository.observeUpcomingEpisodes(days = 60, limit = 40),
            container.statisticsRepository.observeRecentActivity(20),
        ) { continueWatching, upcoming, recent ->
            Triple(continueWatching, upcoming, recent)
        }

        viewModelScope.launch {
            combine(
                container.statisticsRepository.observeStatistics(),
                feeds,
                container.libraryRepository.observeLibrary(),
                container.settingsRepository.settings,
            ) { stats, feedData, library, settings ->
                HomeUiState(
                    loading = false,
                    sections = settings.homeSections,
                    stats = stats,
                    continueWatching = feedData.first,
                    upcoming = feedData.second.take(12),
                    recent = feedData.third.take(12),
                    watchlist = library.filter { it.status == WatchStatus.PLAN_TO_WATCH }
                        .sortedByDescending { it.media.addedAt }
                        .take(12),
                    librarySize = library.size,
                )
            }.catch { failure ->
                _state.update {
                    it.copy(loading = false, error = AppError.Unknown(failure))
                }
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    fun markNextEpisode(item: ContinueWatchingItem) {
        val episode = item.nextEpisode ?: return
        act { container.watchRepository.markEpisodeWatched(episode.id) }
    }

    fun markMovieWatched(mediaId: Long) {
        act { container.watchRepository.markMovieWatched(mediaId) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun act(block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            when (val result = block()) {
                is Outcome.Failure -> _state.update { it.copy(error = result.error) }
                is Outcome.Success -> Unit
            }
        }
    }
}
