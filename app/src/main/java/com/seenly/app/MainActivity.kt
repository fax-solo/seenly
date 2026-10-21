package com.seenly.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.domain.model.AppLanguage
import com.seenly.app.domain.model.AppSettings
import com.seenly.app.domain.model.ThemeMode
import com.seenly.app.ui.navigation.SeenlyRoot
import com.seenly.app.ui.theme.SeenlyTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SeenlyApp).container

        setContent {
            val settingsState by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val settings: AppSettings? = settingsState
            val darkTheme = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val language = settings?.language
            LaunchedEffect(language) {
                if (language == null) return@LaunchedEffect
                val locales = when (language) {
                    AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                    AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
                    AppLanguage.ARABIC -> LocaleListCompat.forLanguageTags("ar")
                }
                if (AppCompatDelegate.getApplicationLocales() != locales) {
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                }
            }

            SeenlyTheme(darkTheme = darkTheme) {
                SeenlyRoot(container = container)
            }
        }
    }
}
