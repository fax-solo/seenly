package com.seenly.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seenly.app.R
import com.seenly.app.core.common.AppError

/**
 * Maps technical errors to localized text. Every known error code has a specific
 * message; unknown codes fall back to a generic explanation instead of leaking
 * raw exception text to the user.
 */
@Composable
fun appErrorText(error: AppError?): String? {
    if (error == null) return null
    return when (error) {
        is AppError.Offline -> stringResource(R.string.error_offline)
        is AppError.RateLimited -> {
            val seconds = error.retryAfterSeconds
            if (seconds != null && seconds > 0) {
                stringResource(R.string.error_rate_limited_seconds, seconds)
            } else {
                stringResource(R.string.error_rate_limited)
            }
        }
        is AppError.Auth -> {
            if (error.message == "provider_not_configured") {
                stringResource(R.string.error_provider_not_configured)
            } else {
                stringResource(R.string.error_auth)
            }
        }
        is AppError.Http -> stringResource(R.string.error_http, error.code)
        is AppError.Data -> dataErrorText(error.message)
        is AppError.Invalid -> codeErrorText(error.message)
        is AppError.Unavailable -> stringResource(R.string.error_unavailable)
        is AppError.Unknown -> stringResource(R.string.error_generic)
    }
}

@Composable
fun codeErrorText(code: String?): String = when (code) {
    "empty_title", "empty_list_name" -> stringResource(R.string.error_empty_value)
    "duplicate_title" -> stringResource(R.string.error_duplicate_title)
    "duplicate_list" -> stringResource(R.string.error_duplicate_list)
    "invalid_rating" -> stringResource(R.string.error_invalid_rating)
    "invalid_episode_count", "invalid_value" -> stringResource(R.string.error_invalid_value)
    "invalid_goal_target" -> stringResource(R.string.error_invalid_goal)
    "title_not_found" -> stringResource(R.string.error_title_not_found)
    "episode_not_found", "season_not_found" -> stringResource(R.string.error_episode_not_found)
    "list_not_found" -> stringResource(R.string.error_list_not_found)
    "builtin_list" -> stringResource(R.string.error_builtin_list)
    "list_name_too_long" -> stringResource(R.string.error_list_name_too_long)
    "no_provider_link" -> stringResource(R.string.error_no_provider_link)
    "provider_unavailable" -> stringResource(R.string.error_provider_unavailable)
    "provider_not_configured" -> stringResource(R.string.error_provider_not_configured)
    "unsupported" -> stringResource(R.string.error_unsupported)
    "not_a_movie" -> stringResource(R.string.error_generic)
    "episode_required" -> stringResource(R.string.error_generic)
    "no_episodes" -> stringResource(R.string.empty_episodes)
    "event_not_found" -> stringResource(R.string.error_generic)
    "backup_too_large" -> stringResource(R.string.error_backup_too_large)
    "unsupported_backup_version" -> stringResource(R.string.error_backup_version)
    "invalid_backup" -> stringResource(R.string.error_backup_invalid)
    "import_failed" -> stringResource(R.string.error_backup_import)
    "export_failed" -> stringResource(R.string.error_backup_export)
    "cannot_open" -> stringResource(R.string.error_cannot_open)
    "not_found" -> stringResource(R.string.error_not_found)
    else -> stringResource(R.string.error_generic)
}

@Composable
private fun dataErrorText(code: String?): String = when (code) {
    "not_found" -> stringResource(R.string.error_not_found)
    "invalid_backup" -> stringResource(R.string.error_backup_invalid)
    "backup_too_large" -> stringResource(R.string.error_backup_too_large)
    "import_failed" -> stringResource(R.string.error_backup_import)
    "export_failed" -> stringResource(R.string.error_backup_export)
    "cannot_open" -> stringResource(R.string.error_cannot_open)
    else -> stringResource(R.string.error_generic_data)
}
