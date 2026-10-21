package com.seenly.app.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seenly.app.TestSupport
import com.seenly.app.domain.model.AppLanguage
import com.seenly.app.domain.model.HomeSectionKey
import com.seenly.app.domain.model.LibrarySort
import com.seenly.app.domain.model.NotificationSettings
import com.seenly.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreSettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        repository = DataStoreSettingsRepository(context, TestSupport.dispatchers)
        repository.setTheme(ThemeMode.SYSTEM)
        repository.setLanguage(AppLanguage.SYSTEM)
        repository.setDefaultLibrarySort(LibrarySort.RECENTLY_ADDED)
        repository.setHomeSections(HomeSectionKey.entries)
        repository.setNotifications(NotificationSettings())
        repository.setDeckDismissedCandidates(emptySet())
    }

    @Test
    fun `defaults are explicit and persisted values survive a new repository instance`() = runBlocking {
        var settings = repository.current()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AppLanguage.SYSTEM, settings.language)
        assertEquals(LibrarySort.RECENTLY_ADDED, settings.defaultLibrarySort)
        assertEquals(HomeSectionKey.entries, settings.homeSections)
        assertTrue(settings.deckDismissedCandidates.isEmpty())
        assertTrue(settings.notifications.seriesCompletion)

        repository.setTheme(ThemeMode.DARK)
        repository.setLanguage(AppLanguage.ARABIC)
        repository.setDefaultLibrarySort(LibrarySort.TITLE_ASC)
        repository.setHomeSections(listOf(HomeSectionKey.QUICK_STATS, HomeSectionKey.WATCHLIST))
        repository.setNotifications(
            NotificationSettings(
                upcomingEpisodes = true,
                progressReminders = true,
                weeklySummary = true,
                seriesCompletion = false,
                reminderHour = 7,
                staleAfterDays = 14,
            ),
        )
        repository.setDeckDismissedCandidates(setOf("ANILIST/1", "CINEMETA/series/tt123"))

        val reopened = DataStoreSettingsRepository(context, TestSupport.dispatchers)
        settings = reopened.current()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AppLanguage.ARABIC, settings.language)
        assertEquals(LibrarySort.TITLE_ASC, settings.defaultLibrarySort)
        assertEquals(listOf(HomeSectionKey.QUICK_STATS, HomeSectionKey.WATCHLIST), settings.homeSections)
        assertEquals(true, settings.notifications.upcomingEpisodes)
        assertEquals(true, settings.notifications.progressReminders)
        assertEquals(true, settings.notifications.weeklySummary)
        assertFalse(settings.notifications.seriesCompletion)
        assertEquals(7, settings.notifications.reminderHour)
        assertEquals(14, settings.notifications.staleAfterDays)
        assertEquals(setOf("ANILIST/1", "CINEMETA/series/tt123"), settings.deckDismissedCandidates)

        repository.setDeckDismissedCandidates(emptySet())
        assertTrue(repository.settings.first().deckDismissedCandidates.isEmpty())
    }
}
