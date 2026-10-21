package com.seenly.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.seenly.app.AppContainer
import com.seenly.app.MainActivity
import com.seenly.app.R
import com.seenly.app.SeenlyApp
import com.seenly.app.core.time.IsoDates.toIso
import com.seenly.app.domain.model.AppLanguage
import com.seenly.app.domain.model.NotificationSettings
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Local-only notifications scheduled with WorkManager. No push service, no
 * account and no network is needed to render reminders. Android may delay exact
 * delivery (battery optimizations); the app never claims otherwise.
 */
object NotificationScheduler {

    const val CHANNEL_UPCOMING = "upcoming_episodes"
    const val CHANNEL_REMINDERS = "progress_reminders"
    const val CHANNEL_SUMMARY = "weekly_summary"
    const val CHANNEL_COMPLETION = "series_completion"

    private const val UNIQUE_NOTIFICATION_WORK = "seenly_daily_notifications"
    private const val UNIQUE_REFRESH_WORK = "seenly_metadata_refresh"
    private const val NOTIFICATION_ID_UPCOMING = 1001
    private const val NOTIFICATION_ID_REMINDER = 1002
    private const val NOTIFICATION_ID_SUMMARY = 1003
    private const val NOTIFICATION_ID_COMPLETION = 1004

    fun createChannels(context: Context, language: AppLanguage = AppLanguage.SYSTEM) {
        val localized = context.forAppLanguage(language)
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_UPCOMING, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(localized.getString(R.string.channel_upcoming))
                .setDescription(localized.getString(R.string.channel_upcoming_description))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_REMINDERS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(localized.getString(R.string.channel_reminders))
                .setDescription(localized.getString(R.string.channel_reminders_description))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_SUMMARY, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(localized.getString(R.string.channel_summary))
                .setDescription(localized.getString(R.string.channel_summary_description))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_COMPLETION, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(localized.getString(R.string.channel_completion))
                .setDescription(localized.getString(R.string.channel_completion_description))
                .build(),
        )
    }

    /**
     * Immediate confirmation that a title was finished. Unlike the periodic
     * categories this is event-driven; [seriesCompletionEnabled] is checked by
     * the caller (the settings flow owns the switch).
     */
    @SuppressLint("MissingPermission") // canPostNotifications() checks POST_NOTIFICATIONS.
    fun postSeriesCompleted(context: Context, title: String, language: AppLanguage = AppLanguage.SYSTEM) {
        if (!canPostNotifications(context)) return
        val localized = context.forAppLanguage(language)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_COMPLETION,
            basicNotification(
                context = localized,
                channel = CHANNEL_COMPLETION,
                title = localized.getString(R.string.notification_completion_title),
                body = localized.getString(R.string.notification_completion_body, title),
            ),
        )
    }

    /** Schedules or cancels work to match the persisted notification settings. */
    fun schedule(context: Context, settings: NotificationSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.anyEnabled) {
            workManager.cancelUniqueWork(UNIQUE_NOTIFICATION_WORK)
            return
        }
        val delay = initialDelayMillis(settings.reminderHour)
        val request = PeriodicWorkRequestBuilder<SeenlyNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_NOTIFICATION_WORK)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NOTIFICATION_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Metadata refresh runs with network constraints; the rest of the app stays offline. */
    fun scheduleMetadataRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<MetadataRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(UNIQUE_REFRESH_WORK)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_REFRESH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    @SuppressLint("MissingPermission") // canPostNotifications() checks POST_NOTIFICATIONS.
    internal suspend fun deliver(container: AppContainer) {
        val context = container.appContext
        if (!canPostNotifications(context)) {
            return
        }

        val settings = container.settingsRepository.current()
        val notifications = settings.notifications
        // Application resources are not localised by AppCompat on API < 33.
        // Build a configuration context so background notifications honour the
        // in-app language as well.
        val textContext = context.forAppLanguage(settings.language)
        val manager = NotificationManagerCompat.from(context)
        val today = container.clock.today()

        if (notifications.upcomingEpisodes) {
            val upcoming = runCatching {
                container.statisticsRepository.observeUpcomingEpisodes(days = 2, limit = 12)
                    .first()
                    .filter { it.airDate == today || it.airDate == today.plusDays(1) }
            }.getOrDefault(emptyList())
            if (upcoming.isNotEmpty()) {
                val titles = upcoming.take(3).joinToString(" · ") { item ->
                    val label = textContext.getString(
                        R.string.notification_episode_label,
                        item.mediaTitle,
                        item.episodeNumber ?: 0,
                    )
                    label
                }
                val body = if (upcoming.size > 3) {
                    textContext.getString(R.string.notification_upcoming_more, titles, upcoming.size - 3)
                } else {
                    titles
                }
                manager.notify(
                    NOTIFICATION_ID_UPCOMING,
                    basicNotification(
                        context = textContext,
                        channel = CHANNEL_UPCOMING,
                        title = textContext.getString(R.string.notification_upcoming_title),
                        body = body,
                    ),
                )
            } else {
                manager.cancel(NOTIFICATION_ID_UPCOMING)
            }
        } else {
            manager.cancel(NOTIFICATION_ID_UPCOMING)
        }

        if (notifications.progressReminders) {
            val staleBefore = container.clock.nowMillis() -
                notifications.staleAfterDays.toLong() * 24 * 60 * 60 * 1000
            val stale = runCatching {
                container.statisticsRepository.observeContinueWatching(limit = 20)
                    .first()
                    .filter { (it.entry.lastActivityAt ?: Long.MAX_VALUE) < staleBefore }
                    .take(3)
            }.getOrDefault(emptyList())
            if (stale.isNotEmpty()) {
                val body = stale.joinToString(" · ") { item ->
                    val next = item.nextEpisode?.number
                    if (next != null) {
                        textContext.getString(R.string.notification_reminder_episode, item.entry.media.title, next)
                    } else {
                        item.entry.media.title
                    }
                }
                manager.notify(
                    NOTIFICATION_ID_REMINDER,
                    basicNotification(
                        context = textContext,
                        channel = CHANNEL_REMINDERS,
                        title = textContext.getString(R.string.notification_reminder_title),
                        body = body,
                    ),
                )
            } else {
                manager.cancel(NOTIFICATION_ID_REMINDER)
            }
        } else {
            manager.cancel(NOTIFICATION_ID_REMINDER)
        }

        if (notifications.weeklySummary && today.dayOfWeek == DayOfWeek.MONDAY) {
            val snapshot = runCatching {
                container.statisticsRepository.observeStatistics().first()
            }.getOrNull()
            val goals = runCatching {
                container.statisticsRepository.observeGoalProgress().first()
            }.getOrDefault(emptyList())
            if (snapshot != null && snapshot.hasData) {
                val body = textContext.getString(
                    R.string.notification_summary_body,
                    snapshot.totalViewings,
                    (snapshot.estimatedHours * 10).roundToInt() / 10.0,
                    goals.count { it.current >= it.goal.target },
                    goals.size,
                )
                manager.notify(
                    NOTIFICATION_ID_SUMMARY,
                    basicNotification(
                        context = textContext,
                        channel = CHANNEL_SUMMARY,
                        title = textContext.getString(R.string.notification_summary_title),
                        body = body,
                    ),
                )
            }
        } else if (!notifications.weeklySummary) {
            manager.cancel(NOTIFICATION_ID_SUMMARY)
        }
    }

    private fun basicNotification(
        context: Context,
        channel: String,
        title: String,
        body: String,
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            channel.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun Context.forAppLanguage(language: AppLanguage): Context {
        if (language == AppLanguage.SYSTEM) return this
        val locale = Locale.forLanguageTag(language.tag)
        val configuration = Configuration(resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return createConfigurationContext(configuration)
    }

    private fun initialDelayMillis(reminderHour: Int): Long {
        val now = ZonedDateTime.now()
        var target = now.withHour(reminderHour.coerceIn(0, 23))
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(0)
    }
}

class SeenlyNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SeenlyApp ?: return Result.success()
        return try {
            NotificationScheduler.deliver(app.container)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class MetadataRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SeenlyApp ?: return Result.success()
        return try {
            app.container.catalogRepository.refreshAiringForWatchedTitles(limit = 5)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
