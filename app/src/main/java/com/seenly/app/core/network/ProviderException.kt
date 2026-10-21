package com.seenly.app.core.network

import com.seenly.app.core.common.AppError
import java.io.IOException

/** Recoverable provider failures. Mapped to [AppError] at the repository boundary. */
sealed class ProviderException(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause) {

    class Offline(cause: Throwable? = null) : ProviderException(null, cause)

    class RateLimited(val retryAfterSeconds: Long? = null) : ProviderException("rate limited")

    class Unauthorized(message: String? = null) : ProviderException(message)

    class Http(val code: Int, message: String? = null) : ProviderException(message) {
        val isNotFound: Boolean get() = code == 404
    }

    class Parse(message: String? = null, cause: Throwable? = null) : ProviderException(message, cause)

    class Unavailable(message: String? = null) : ProviderException(message)

    class Unknown(cause: Throwable? = null) : ProviderException(null, cause)
}

fun Throwable.toProviderException(): ProviderException = when (this) {
    is ProviderException -> this
    is IOException -> {
        // Distinguish "no network" from other IO failures conservatively:
        // both are recoverable and shown as offline-style errors.
        ProviderException.Offline(this)
    }
    else -> ProviderException.Unknown(this)
}

fun Throwable.toAppError(): AppError = when (val e = toProviderException()) {
    is ProviderException.Offline -> AppError.Offline
    is ProviderException.RateLimited -> AppError.RateLimited(e.retryAfterSeconds)
    is ProviderException.Unauthorized -> AppError.Auth(e.message)
    is ProviderException.Http -> when {
        e.isNotFound -> AppError.Data("not_found")
        e.code == 401 || e.code == 403 -> AppError.Auth(null)
        e.code == 429 -> AppError.RateLimited(null)
        e.code >= 500 -> AppError.Unavailable
        else -> AppError.Http(e.code)
    }
    is ProviderException.Parse -> AppError.Data(e.message)
    is ProviderException.Unavailable -> AppError.Unavailable
    is ProviderException.Unknown -> AppError.Unknown(e.cause)
}

fun Throwable.toAppErrorMessageOrNull(): String? = (toAppError() as? AppError.Data)?.message
