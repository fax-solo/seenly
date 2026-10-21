package com.seenly.app.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.model.MediaList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListsUiState(
    val loading: Boolean = true,
    val lists: List<MediaList> = emptyList(),
    val error: AppError? = null,
)

class ListsViewModel(private val container: AppContainer) : ViewModel() {

    private val _error = MutableStateFlow<AppError?>(null)
    val error = _error.asStateFlow()

    val state = container.listsRepository.observeLists()
        .map { lists ->
            ListsUiState(
                loading = false,
                lists = lists.sortedWith(
                    compareBy<MediaList> { it.sortOrder }.thenBy { it.name.lowercase() },
                ),
            )
        }
        .catch { failure ->
            emit(ListsUiState(loading = false, error = AppError.Unknown(failure)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ListsUiState(),
        )

    fun createList(name: String, description: String?) {
        viewModelScope.launch {
            when (val result = container.listsRepository.createList(name, description)) {
                is Outcome.Failure -> _error.value = result.error
                is Outcome.Success -> Unit
            }
        }
    }

    fun updateList(listId: Long, name: String, description: String?) {
        viewModelScope.launch {
            when (val result = container.listsRepository.updateList(listId, name, description)) {
                is Outcome.Failure -> _error.value = result.error
                is Outcome.Success -> Unit
            }
        }
    }

    fun deleteList(listId: Long) {
        viewModelScope.launch {
            when (val result = container.listsRepository.deleteList(listId)) {
                is Outcome.Failure -> _error.value = result.error
                is Outcome.Success -> Unit
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private fun setError(error: AppError?) {
        _error.update { error }
    }
}
