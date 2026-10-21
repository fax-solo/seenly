package com.seenly.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.BuildConfig
import com.seenly.app.domain.model.AppLanguage
import com.seenly.app.domain.model.AppSettings
import com.seenly.app.domain.model.HomeSectionKey
import com.seenly.app.domain.model.LibrarySort
import com.seenly.app.domain.model.NotificationSettings
import com.seenly.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
    val versionName: String = BuildConfig.VERSION_NAME,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val state = container.settingsRepository.settings
        .map { settings -> SettingsUiState(settings = settings, loaded = true) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setTheme(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { container.settingsRepository.setLanguage(language) }
    }

    fun setDefaultSort(sort: LibrarySort) {
        viewModelScope.launch { container.settingsRepository.setDefaultLibrarySort(sort) }
    }

    fun setNotifications(settings: NotificationSettings) {
        viewModelScope.launch { container.settingsRepository.setNotifications(settings) }
    }

    fun setHomeSection(section: HomeSectionKey, enabled: Boolean) {
        val current = state.value.settings.homeSections
        val next = if (enabled) {
            HomeSectionKey.entries.filter { it == section || it in current }
        } else {
            current.filterNot { it == section }
        }
        viewModelScope.launch { container.settingsRepository.setHomeSections(next) }
    }
}
