package com.seenly.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

/**
 * Application entry point. Owns the manual dependency container.
 * Deliberately minimal: no analytics, no crash reporting, no third-party SDKs.
 */
class SeenlyApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader
}
