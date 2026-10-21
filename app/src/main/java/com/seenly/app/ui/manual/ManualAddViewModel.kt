package com.seenly.app.ui.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.model.ManualMediaInput
import com.seenly.app.domain.model.MediaType
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManualAddUiState(
    val saving: Boolean = false,
    val savedId: Long? = null,
    val error: AppError? = null,
)

class ManualAddViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ManualAddUiState())
    val state = _state.asStateFlow()

    fun save(
        title: String,
        type: MediaType,
        originalTitle: String?,
        releaseDate: LocalDate?,
        totalSeasons: Int?,
        totalEpisodes: Int?,
        runtimeMinutes: Int?,
        synopsis: String?,
        posterUrl: String?,
        genres: List<String>,
    ) {
        if (_state.value.saving) return
        _state.value = ManualAddUiState(saving = true)
        viewModelScope.launch {
            val input = ManualMediaInput(
                title = title.trim(),
                type = type,
                originalTitle = originalTitle?.trim()?.takeIf { it.isNotEmpty() },
                releaseDate = releaseDate,
                totalSeasons = totalSeasons,
                totalEpisodes = totalEpisodes,
                runtimeMinutes = runtimeMinutes,
                synopsis = synopsis?.trim()?.takeIf { it.isNotEmpty() },
                posterUrl = posterUrl?.trim()?.takeIf { it.isNotEmpty() },
                genres = genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            )
            when (val result = container.libraryRepository.addManual(input)) {
                is Outcome.Success -> _state.value = ManualAddUiState(savedId = result.value)
                is Outcome.Failure -> _state.update {
                    it.copy(saving = false, error = result.error)
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
