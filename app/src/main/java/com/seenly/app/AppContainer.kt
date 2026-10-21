package com.seenly.app

import android.app.Application
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.seenly.app.core.common.AppClock
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.common.DefaultAppDispatchers
import com.seenly.app.core.common.SystemAppClock
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.core.network.anilistRetrofit
import com.seenly.app.core.network.cinemetaRetrofit
import com.seenly.app.core.network.defaultOkHttpClient
import com.seenly.app.core.network.jikanRetrofit
import com.seenly.app.core.network.tvmazeRetrofit
import com.seenly.app.core.preferences.DataStoreSettingsRepository
import com.seenly.app.data.backup.BackupRepositoryImpl
import com.seenly.app.data.remote.anilist.AniListApi
import com.seenly.app.data.remote.anilist.AniListProvider
import com.seenly.app.data.remote.cinemeta.CinemetaApi
import com.seenly.app.data.remote.cinemeta.CinemetaProvider
import com.seenly.app.data.remote.jikan.JikanApi
import com.seenly.app.data.remote.jikan.JikanProvider
import com.seenly.app.data.remote.ratings.MyAnimeListClient
import com.seenly.app.data.remote.ratings.RottenTomatoesClient
import com.seenly.app.data.remote.tvmaze.TvmazeApi
import com.seenly.app.data.remote.tvmaze.TvmazeProvider
import com.seenly.app.data.repository.CatalogRepositoryImpl
import com.seenly.app.data.repository.GoalsRepositoryImpl
import com.seenly.app.data.repository.LibraryRepositoryImpl
import com.seenly.app.data.repository.ListsRepositoryImpl
import com.seenly.app.data.repository.MetadataEnricher
import com.seenly.app.data.repository.StatisticsRepositoryImpl
import com.seenly.app.data.repository.WatchRepositoryImpl
import com.seenly.app.domain.repository.BackupRepository
import com.seenly.app.domain.repository.CatalogRepository
import com.seenly.app.domain.repository.GoalsRepository
import com.seenly.app.domain.repository.LibraryRepository
import com.seenly.app.domain.repository.ListsRepository
import com.seenly.app.domain.repository.SettingsRepository
import com.seenly.app.domain.repository.StatisticsRepository
import com.seenly.app.domain.repository.WatchRepository
import com.seenly.app.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manual dependency container. One instance per process, owned by [SeenlyApp].
 * No service locator framework and no reflection-based injection.
 */
class AppContainer(private val application: Application) {

    val appContext: android.content.Context get() = application.applicationContext

    private val started = AtomicBoolean(false)

    val dispatchers: AppDispatchers = DefaultAppDispatchers()
    val clock: AppClock = SystemAppClock()

    val database: SeenlyDatabase by lazy { SeenlyDatabase.build(application) }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(application, dispatchers)
    }

    private val okHttpClient by lazy { defaultOkHttpClient() }

    /** Ratings scrapes must fail fast; they never run on an import's critical path. */
    private val ratingScrapeClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private val jikanProvider by lazy {
        JikanProvider(
            api = jikanRetrofit(okHttpClient).create(JikanApi::class.java),
            dispatchers = dispatchers,
        )
    }

    private val anilistProvider by lazy {
        AniListProvider(
            api = anilistRetrofit(okHttpClient).create(AniListApi::class.java),
            dispatchers = dispatchers,
            zoneProvider = { clock.zone() },
            // AniList never publishes episode titles; Jikan fills them in.
            episodeTitleSource = { malId -> jikanProvider.episodeTitles(malId) },
        )
    }

    private val cinemetaProvider by lazy {
        CinemetaProvider(
            api = cinemetaRetrofit(okHttpClient).create(CinemetaApi::class.java),
            dispatchers = dispatchers,
        )
    }

    private val tvmazeProvider by lazy {
        TvmazeProvider(
            api = tvmazeRetrofit(okHttpClient).create(TvmazeApi::class.java),
            dispatchers = dispatchers,
        )
    }

    private val metadataEnricher by lazy {
        MetadataEnricher(
            cinemeta = cinemetaProvider,
            jikan = jikanProvider,
            myAnimeList = MyAnimeListClient(ratingScrapeClient, dispatchers),
            rottenTomatoes = RottenTomatoesClient(ratingScrapeClient, dispatchers),
            clock = clock,
        )
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepositoryImpl(database, clock, dispatchers)
    }

    val watchRepository: WatchRepository by lazy {
        WatchRepositoryImpl(database, clock, dispatchers)
    }

    val listsRepository: ListsRepository by lazy {
        ListsRepositoryImpl(database, clock, dispatchers)
    }

    val goalsRepository: GoalsRepository by lazy {
        GoalsRepositoryImpl(database, clock, dispatchers)
    }

    val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepositoryImpl(database, clock, dispatchers)
    }

    val catalogRepository: CatalogRepository by lazy {
        CatalogRepositoryImpl(
            db = database,
            clock = clock,
            dispatchers = dispatchers,
            providers = listOf(anilistProvider, cinemetaProvider, jikanProvider, tvmazeProvider),
            enricher = metadataEnricher,
            backgroundScope = scope,
        )
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepositoryImpl(
            context = application,
            db = database,
            clock = clock,
            dispatchers = dispatchers,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    val imageLoader: ImageLoader by lazy {
        val cacheDir = application.cacheDir.resolve("image_cache").toOkioPath()
        ImageLoader.Builder(application)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build(),
            )
            .crossfade(true)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Starts long-lived observers exactly once. Kept deliberately small: channel
     * localization, built-in lists, completion notifications and cache housekeeping.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        NotificationScheduler.createChannels(application)
        NotificationScheduler.scheduleMetadataRefresh(application)

        scope.launch {
            runCatching { listsRepository.ensureBuiltinLists() }
        }

        scope.launch {
            settingsRepository.settings.collect { settings ->
                NotificationScheduler.createChannels(application, settings.language)
            }
        }

        // Immediate local notification whenever a title is finished.
        scope.launch {
            watchRepository.completions.collect { notice ->
                val settings = runCatching { settingsRepository.current() }.getOrNull() ?: return@collect
                if (settings.notifications.seriesCompletion) {
                    runCatching {
                        NotificationScheduler.postSeriesCompleted(
                            context = application,
                            title = notice.title,
                            language = settings.language,
                        )
                    }
                }
            }
        }

        scope.launch {
            val cutoff = clock.nowMillis() - 7L * 24 * 60 * 60 * 1000
            runCatching { database.mediaDao().deleteCacheOlderThan(cutoff) }
        }

        scope.launch {
            runCatching {
                val current = settingsRepository.current()
                NotificationScheduler.schedule(application, current.notifications)
            }
        }
    }
}
