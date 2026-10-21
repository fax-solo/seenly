package com.seenly.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.repository.BackupSummary
import com.seenly.app.domain.repository.ImportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val working: Boolean = false,
    val summary: BackupSummary? = null,
    val imported: Boolean = false,
    val error: AppError? = null,
)

class BackupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()

    fun export(uri: Uri) {
        if (_state.value.working) return
        _state.update { BackupUiState(working = true) }
        viewModelScope.launch {
            when (val result = container.backupRepository.export(uri)) {
                is Outcome.Success -> _state.value = BackupUiState(summary = result.value)
                is Outcome.Failure -> _state.value = BackupUiState(error = result.error)
            }
        }
    }

    fun import(uri: Uri, mode: ImportMode) {
        if (_state.value.working) return
        _state.update { BackupUiState(working = true) }
        viewModelScope.launch {
            when (val result = container.backupRepository.import(uri, mode)) {
                is Outcome.Success -> _state.value = BackupUiState(
                    summary = result.value,
                    imported = true,
                )
                is Outcome.Failure -> _state.value = BackupUiState(error = result.error)
            }
        }
    }

    fun dismiss() {
        _state.value = BackupUiState()
    }
}
