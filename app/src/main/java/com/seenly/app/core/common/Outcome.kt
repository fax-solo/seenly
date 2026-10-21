package com.seenly.app.core.common

/**
 * A small result type used by repositories for recoverable operations.
 * UI maps [AppError] to localized messages; nothing crashes on network issues.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

sealed interface AppError {
    /** No network connectivity. */
    data object Offline : AppError

    /** Provider rate limit hit; optional retry-after hint in seconds. */
    data class RateLimited(val retryAfterSeconds: Long? = null) : AppError

    /** Missing or invalid provider credential (for keyed providers). */
    data class Auth(val message: String? = null) : AppError

    /** HTTP error that is not otherwise classified. */
    data class Http(val code: Int) : AppError

    /** Response could not be parsed or was structurally invalid. */
    data class Data(val message: String? = null) : AppError

    /** Invalid user input or invalid state for the requested operation. */
    data class Invalid(val message: String? = null) : AppError

    /** Provider temporarily unavailable. */
    data object Unavailable : AppError

    data class Unknown(val cause: Throwable? = null) : AppError
}
