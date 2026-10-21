package com.seenly.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.domain.model.GoalProgress
import com.seenly.app.domain.statistics.StatisticsSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val loading: Boolean = true,
    val snapshot: StatisticsSnapshot = StatisticsSnapshot(),
    val goals: List<GoalProgress> = emptyList(),
    val error: AppError? = null,
)

class StatsViewModel(container: AppContainer) : ViewModel() {

    val state = combine(
        container.statisticsRepository.observeStatistics(),
        container.statisticsRepository.observeGoalProgress(),
    ) { snapshot, goals ->
        StatsUiState(loading = false, snapshot = snapshot, goals = goals)
    }.catch { failure ->
        emit(StatsUiState(loading = false, error = AppError.Unknown(failure)))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )
}
