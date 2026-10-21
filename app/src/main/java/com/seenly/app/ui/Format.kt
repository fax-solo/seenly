package com.seenly.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Formatting follows the app's *configuration* locale, not the process-wide
 * default. AppCompat per-app locales change resources without necessarily
 * changing `Locale.getDefault()`, which would otherwise leave dates and numbers
 * in the system language after an in-app language switch.
 */
@Composable
private fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun LocalDate.formatMedium(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale()))

@Composable
fun LocalDate.formatShort(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(appLocale()))

@Composable
fun YearMonth.formatMonthYear(): String {
    val monthName = month.getDisplayName(TextStyle.FULL, appLocale())
    return "$monthName $year"
}

@Composable
fun formatRating(value: Double?): String? {
    if (value == null) return null
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(appLocale(), "%.1f", value)
    }
}

@Composable
fun formatHours(minutes: Long): String =
    String.format(appLocale(), "%.1f", minutes / 60.0)

@Composable
fun formatHours(minutes: Int): String = formatHours(minutes.toLong())

@Composable
fun formatNumber(value: Int): String =
    NumberFormat.getIntegerInstance(appLocale()).format(value)

@Composable
fun formatNumber(value: Long): String =
    NumberFormat.getIntegerInstance(appLocale()).format(value)

@Composable
fun LocalDate.weekdayNameShort(): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale())
