package com.seenly.app.domain.model

import java.time.LocalDate

/**
 * Lightweight settings model persisted in DataStore. Notification preferences are
 * local-only and every category can be disabled independently.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val defaultLibrarySort: LibrarySort = LibrarySort.RECENTLY_ADDED,
    val homeSections: List<HomeSectionKey> = HomeSectionKey.entries.toList(),
    val notifications: NotificationSettings = NotificationSettings(),
    /** Recommendation-deck candidates the user swiped away ("PROVIDER/externalId"). */
    val deckDismissedCandidates: Set<String> = emptySet(),
)

data class NotificationSettings(
    val upcomingEpisodes: Boolean = false,
    val progressReminders: Boolean = false,
    val weeklySummary: Boolean = false,
    /** Posts a local notification when a title is finished. */
    val seriesCompletion: Boolean = true,
    /** Local hour (0-23) for reminders and summaries. */
    val reminderHour: Int = 20,
    /** Days without activity before a "still watching?" reminder is posted. */
    val staleAfterDays: Int = 7,
) {
    /** True when a scheduled (periodic) category is enabled. */
    val anyEnabled: Boolean get() = upcomingEpisodes || progressReminders || weeklySummary

    companion object {
        fun sanitized(
            upcomingEpisodes: Boolean,
            progressReminders: Boolean,
            weeklySummary: Boolean,
            reminderHour: Int,
            staleAfterDays: Int,
            seriesCompletion: Boolean = true,
        ): NotificationSettings = NotificationSettings(
            upcomingEpisodes = upcomingEpisodes,
            progressReminders = progressReminders,
            weeklySummary = weeklySummary,
            seriesCompletion = seriesCompletion,
            reminderHour = reminderHour.coerceIn(0, 23),
            staleAfterDays = staleAfterDays.coerceIn(1, 90),
        )
    }
}

/** Local calendar period used for goals and summaries. */
enum class PeriodType { WEEK, MONTH, YEAR }

data class DatePeriod(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)
}
