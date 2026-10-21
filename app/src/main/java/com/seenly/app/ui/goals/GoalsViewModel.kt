package com.seenly.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.model.GoalMetric
import com.seenly.app.domain.model.GoalProgress
import com.seenly.app.domain.model.GoalType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalsUiState(
    val loading: Boolean = true,
    val goals: List<GoalProgress> = emptyList(),
    val error: AppError? = null,
)

class GoalsViewModel(private val container: AppContainer) : ViewModel() {

    private val actionError = MutableStateFlow<AppError?>(null)

    val state = container.statisticsRepository.observeGoalProgress()
        .map { progress -> GoalsUiState(loading = false, goals = progress) }
        .catch { failure -> emit(GoalsUiState(loading = false, error = AppError.Unknown(failure))) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GoalsUiState(),
        )

    val error: MutableStateFlow<AppError?> = actionError

    fun saveGoal(type: GoalType, metric: GoalMetric, target: Int) {
        viewModelScope.launch {
            when (val result = container.goalsRepository.saveGoal(type, metric, target)) {
                is Outcome.Failure -> actionError.value = result.error
                is Outcome.Success -> Unit
            }
        }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch {
            container.goalsRepository.deleteGoal(goalId)
        }
    }

    fun dismissError() {
        actionError.value = null
    }
}
