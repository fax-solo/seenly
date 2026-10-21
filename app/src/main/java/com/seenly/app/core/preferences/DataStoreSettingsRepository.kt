package com.seenly.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.domain.model.AppLanguage
import com.seenly.app.domain.model.AppSettings
import com.seenly.app.domain.model.HomeSectionKey
import com.seenly.app.domain.model.LibrarySort
import com.seenly.app.domain.model.NotificationSettings
import com.seenly.app.domain.model.ThemeMode
import com.seenly.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seenly_settings",
)

/**
 * DataStore-backed preferences. Lightweight, structured settings only; anything
 * user-created (lists, goals, notes) lives in Room. Credentials never live here:
 * every metadata source is keyless.
 */
class DataStoreSettingsRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : SettingsRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val DEFAULT_SORT = stringPreferencesKey("default_library_sort")
        val HOME_SECTIONS = stringPreferencesKey("home_sections")
        val NOTIF_UPCOMING = booleanPreferencesKey("notif_upcoming_episodes")
        val NOTIF_PROGRESS = booleanPreferencesKey("notif_progress_reminders")
        val NOTIF_SUMMARY = booleanPreferencesKey("notif_weekly_summary")
        val NOTIF_COMPLETION = booleanPreferencesKey("notif_series_completion")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val STALE_DAYS = intPreferencesKey("stale_after_days")
        val DECK_DISMISSED = stringSetPreferencesKey("deck_dismissed_candidates")
    }

    private val store: DataStore<Preferences> get() = context.settingsDataStore

    override val settings: Flow<AppSettings> = store.data
        .map { prefs -> prefs.toSettings() }
        .flowOn(dispatchers.io)

    override suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings {
        val theme = ThemeMode.entries.firstOrNull { it.name == this[Keys.THEME] } ?: ThemeMode.SYSTEM
        val language = AppLanguage.entries.firstOrNull { it.tag == this[Keys.LANGUAGE] } ?: AppLanguage.SYSTEM
        val sort = LibrarySort.entries.firstOrNull { it.name == this[Keys.DEFAULT_SORT] }
            ?: LibrarySort.RECENTLY_ADDED
        val sections = parseSections(this[Keys.HOME_SECTIONS])
        val notifications = NotificationSettings.sanitized(
            upcomingEpisodes = this[Keys.NOTIF_UPCOMING] ?: false,
            progressReminders = this[Keys.NOTIF_PROGRESS] ?: false,
            weeklySummary = this[Keys.NOTIF_SUMMARY] ?: false,
            reminderHour = this[Keys.REMINDER_HOUR] ?: 20,
            staleAfterDays = this[Keys.STALE_DAYS] ?: 7,
            seriesCompletion = this[Keys.NOTIF_COMPLETION] ?: true,
        )
        return AppSettings(
            themeMode = theme,
            language = language,
            defaultLibrarySort = sort,
            homeSections = sections,
            notifications = notifications,
            deckDismissedCandidates = this[Keys.DECK_DISMISSED] ?: emptySet(),
        )
    }

    private fun parseSections(raw: String?): List<HomeSectionKey> {
        if (raw.isNullOrBlank()) return HomeSectionKey.entries.toList()
        val parsed = raw.split(",")
            .mapNotNull { name -> HomeSectionKey.entries.firstOrNull { it.name == name } }
            .distinct()
        return parsed.ifEmpty { HomeSectionKey.entries.toList() }
    }

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { prefs -> block(prefs) }
    }

    override suspend fun setTheme(mode: ThemeMode) = update { it[Keys.THEME] = mode.name }

    override suspend fun setLanguage(language: AppLanguage) = update { it[Keys.LANGUAGE] = language.tag }

    override suspend fun setDefaultLibrarySort(sort: LibrarySort) = update { it[Keys.DEFAULT_SORT] = sort.name }

    override suspend fun setHomeSections(sections: List<HomeSectionKey>) = update {
        it[Keys.HOME_SECTIONS] = sections.distinct().joinToString(",") { key -> key.name }
    }

    override suspend fun setNotifications(settings: NotificationSettings) = update { prefs ->
        prefs[Keys.NOTIF_UPCOMING] = settings.upcomingEpisodes
        prefs[Keys.NOTIF_PROGRESS] = settings.progressReminders
        prefs[Keys.NOTIF_SUMMARY] = settings.weeklySummary
        prefs[Keys.NOTIF_COMPLETION] = settings.seriesCompletion
        prefs[Keys.REMINDER_HOUR] = settings.reminderHour.coerceIn(0, 23)
        prefs[Keys.STALE_DAYS] = settings.staleAfterDays.coerceIn(1, 90)
    }

    override suspend fun setDeckDismissedCandidates(keys: Set<String>) = update { prefs ->
        val trimmed = keys.filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .take(MAX_DECK_DISMISSALS)
            .toSet()
        if (trimmed.isEmpty()) prefs.remove(Keys.DECK_DISMISSED) else prefs[Keys.DECK_DISMISSED] = trimmed
    }

    private companion object {
        /** Deck dismissals are a convenience cache; keep the store bounded. */
        const val MAX_DECK_DISMISSALS = 300
    }
}
