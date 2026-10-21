package com.seenly.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.model.AppLanguage
import com.seenly.app.domain.model.HomeSectionKey
import com.seenly.app.domain.model.LibrarySort
import com.seenly.app.domain.model.NotificationSettings
import com.seenly.app.domain.model.ThemeMode
import com.seenly.app.notifications.NotificationScheduler
import com.seenly.app.ui.components.SectionHeader
import com.seenly.app.ui.components.SurfaceCard
import com.seenly.app.ui.seenlyViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenBackup: () -> Unit,
    viewModel: SettingsViewModel = seenlyViewModel { SettingsViewModel(container) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    var showSortDialog by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(settings.notifications) {
        if (state.loaded) {
            NotificationScheduler.schedule(context, settings.notifications)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_appearance)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                )
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                        )
                        Text(themeLabel(mode), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleSmall,
                )
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.language == language,
                            onClick = { viewModel.setLanguage(language) },
                        )
                        Text(languageLabel(language), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_library)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_default_sort),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = sortLabel(settings.defaultLibrarySort),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { showSortDialog = true }) {
                        Text(stringResource(R.string.change))
                    }
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_notifications)) {
                Text(
                    text = stringResource(R.string.settings_notifications_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NotificationToggle(
                    title = stringResource(R.string.settings_notify_upcoming),
                    checked = settings.notifications.upcomingEpisodes,
                    onCheckedChange = { checked ->
                        viewModel.setNotifications(
                            settings.notifications.copy(upcomingEpisodes = checked),
                        )
                        if (checked) requestNotificationPermissionIfNeeded()
                    },
                )
                NotificationToggle(
                    title = stringResource(R.string.settings_notify_progress),
                    checked = settings.notifications.progressReminders,
                    onCheckedChange = { checked ->
                        viewModel.setNotifications(
                            settings.notifications.copy(progressReminders = checked),
                        )
                        if (checked) requestNotificationPermissionIfNeeded()
                    },
                )
                NotificationToggle(
                    title = stringResource(R.string.settings_notify_weekly),
                    checked = settings.notifications.weeklySummary,
                    onCheckedChange = { checked ->
                        viewModel.setNotifications(
                            settings.notifications.copy(weeklySummary = checked),
                        )
                        if (checked) requestNotificationPermissionIfNeeded()
                    },
                )
                NotificationToggle(
                    title = stringResource(R.string.settings_notify_completion),
                    checked = settings.notifications.seriesCompletion,
                    onCheckedChange = { checked ->
                        viewModel.setNotifications(
                            settings.notifications.copy(seriesCompletion = checked),
                        )
                        if (checked) requestNotificationPermissionIfNeeded()
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.settings_reminder_hour,
                        formatHour(settings.notifications.reminderHour),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = settings.notifications.reminderHour.toFloat(),
                    onValueChange = { value ->
                        viewModel.setNotifications(
                            settings.notifications.copy(reminderHour = value.roundToInt().coerceIn(0, 23)),
                        )
                    },
                    valueRange = 0f..23f,
                    steps = 22,
                )
                Text(
                    text = stringResource(
                        R.string.settings_stale_days,
                        settings.notifications.staleAfterDays,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = settings.notifications.staleAfterDays.toFloat(),
                    onValueChange = { value ->
                        viewModel.setNotifications(
                            settings.notifications.copy(
                                staleAfterDays = value.roundToInt().coerceIn(3, 30),
                            ),
                        )
                    },
                    valueRange = 3f..30f,
                    steps = 26,
                )
                Text(
                    text = stringResource(R.string.settings_notification_permission_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_home_sections)) {
                HomeSectionKey.entries.forEach { section ->
                    NotificationToggle(
                        title = homeSectionLabel(section),
                        checked = section in settings.homeSections,
                        onCheckedChange = { enabled -> viewModel.setHomeSection(section, enabled) },
                    )
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_privacy)) {
                Text(
                    text = stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenBackup) {
                    Text(stringResource(R.string.settings_open_backup))
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_version, state.versionName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(stringResource(R.string.settings_default_sort)) },
            text = {
                Column {
                    LibrarySort.entries.forEach { sort ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.defaultLibrarySort == sort,
                                onClick = {
                                    viewModel.setDefaultSort(sort)
                                    showSortDialog = false
                                },
                            )
                            Text(sortLabel(sort), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
        SectionHeader(title = title, modifier = Modifier.padding(horizontal = 0.dp))
        SurfaceCard {
            Column { content() }
        }
    }
}

@Composable
private fun NotificationToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.ARABIC -> R.string.language_arabic
    },
)

@Composable
private fun sortLabel(sort: LibrarySort): String = stringResource(
    when (sort) {
        LibrarySort.RECENTLY_ADDED -> R.string.sort_recently_added
        LibrarySort.RECENTLY_WATCHED -> R.string.sort_recently_watched
        LibrarySort.TITLE_ASC -> R.string.sort_title_asc
        LibrarySort.TITLE_DESC -> R.string.sort_title_desc
        LibrarySort.RATING_DESC -> R.string.sort_rating_desc
        LibrarySort.RELEASE_DATE_DESC -> R.string.sort_release_desc
    },
)

@Composable
private fun homeSectionLabel(section: HomeSectionKey): String = stringResource(
    when (section) {
        HomeSectionKey.CONTINUE_WATCHING -> R.string.home_continue_watching
        HomeSectionKey.UPCOMING_EPISODES -> R.string.home_upcoming
        HomeSectionKey.RECENTLY_WATCHED -> R.string.home_recently_watched
        HomeSectionKey.WATCHLIST -> R.string.home_watchlist
        HomeSectionKey.RECOMMENDATIONS -> R.string.home_recommendations
        HomeSectionKey.QUICK_STATS -> R.string.home_quick_stats
        HomeSectionKey.ACTIVITY_CALENDAR -> R.string.home_activity_calendar
    },
)

private fun formatHour(hour: Int): String = "%02d:00".format(hour)
