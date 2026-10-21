package com.seenly.app.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.MediaList
import com.seenly.app.domain.model.SmartList
import com.seenly.app.domain.model.WatchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListDetailUiState(
    val loading: Boolean = true,
    val list: MediaList? = null,
    val items: List<LibraryEntry> = emptyList(),
    val available: List<LibraryEntry> = emptyList(),
    val error: AppError? = null,
    val bulkCompleting: Boolean = false,
    /** Number of titles the last bulk action completed, or null when idle. */
    val bulkCompletedCount: Int? = null,
) {
    val isPlanList: Boolean
        get() = list?.smartFilter == SmartList.PLAN_TO_WATCH
}

private data class BulkState(
    val completing: Boolean = false,
    val completedCount: Int? = null,
)

class ListDetailViewModel(
    private val container: AppContainer,
    private val listId: Long,
) : ViewModel() {

    private val error = MutableStateFlow<AppError?>(null)
    private val bulk = MutableStateFlow(BulkState())

    val state = combine(
        container.listsRepository.observeLists(),
        container.listsRepository.observeListItems(listId),
        container.libraryRepository.observeLibrary(),
        error,
        bulk,
    ) { lists, items, library, currentError, bulkState ->
        val ids = items.map { it.media.id }.toSet()
        val list = lists.firstOrNull { it.id == listId }
        ListDetailUiState(
            loading = false,
            list = list,
            items = items,
            available = library.filter { entry -> entry.media.id !in ids },
            error = currentError,
            bulkCompleting = bulkState.completing,
            bulkCompletedCount = bulkState.completedCount,
        )
    }.catch { failure ->
        emit(ListDetailUiState(loading = false, error = AppError.Unknown(failure)))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListDetailUiState(),
    )

    fun add(entry: LibraryEntry) {
        act { container.listsRepository.addToList(listId, entry.media.id) }
    }

    fun remove(entry: LibraryEntry) {
        viewModelScope.launch {
            container.listsRepository.removeFromList(listId, entry.media.id)
        }
    }

    /** Marks every title currently in this list as completed. */
    fun markAllCompleted() {
        if (bulk.value.completing) return
        val mediaIds = state.value.items.map { it.media.id }
        if (mediaIds.isEmpty()) return
        viewModelScope.launch {
            bulk.value = BulkState(completing = true)
            when (val result = container.watchRepository.markAllCompleted(mediaIds)) {
                is Outcome.Success -> bulk.value = BulkState(completedCount = result.value)
                is Outcome.Failure -> {
                    error.value = result.error
                    bulk.value = BulkState()
                }
            }
        }
    }

    /** Marks one list row as completed without touching the rest of the list. */
    fun markCompleted(entry: LibraryEntry) {
        if (entry.status == WatchStatus.COMPLETED) return
        viewModelScope.launch {
            when (val result = container.watchRepository.markAllCompleted(listOf(entry.media.id))) {
                is Outcome.Failure -> error.value = result.error
                is Outcome.Success -> Unit
            }
        }
    }

    /** Lets a title move from Plan to Watch to Watching (or any other status) in place. */
    fun setStatus(entry: LibraryEntry, status: WatchStatus) {
        if (entry.status == status) return
        viewModelScope.launch {
            when (val result = container.watchRepository.setStatus(entry.media.id, status)) {
                is Outcome.Failure -> error.value = result.error
                is Outcome.Success -> Unit
            }
        }
    }

    fun dismissBulkResult() {
        bulk.update { it.copy(completedCount = null) }
    }

    fun move(entry: LibraryEntry, offset: Int) {
        viewModelScope.launch {
            val current = state.value.items.map { it.media.id }.toMutableList()
            val index = current.indexOf(entry.media.id)
            val target = index + offset
            if (index < 0 || target !in current.indices) return@launch
            val moved = current.removeAt(index)
            current.add(target, moved)
            container.listsRepository.reorder(listId, current)
        }
    }

    fun dismissError() {
        error.value = null
    }

    private fun act(block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            when (val result = block()) {
                is Outcome.Failure -> error.value = result.error
                is Outcome.Success -> Unit
            }
        }
    }
}
