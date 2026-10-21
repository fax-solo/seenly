package com.seenly.app.core.network

import com.seenly.app.core.common.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs a provider HTTP call with rate limiting, retry/backoff, and consistent
 * error classification. Cancellation always propagates untouched.
 */
suspend fun <T> rateLimitedCall(
    limiter: RateLimiter,
    dispatchers: AppDispatchers,
    maxAttempts: Int = 3,
    block: suspend () -> T,
): T = withContext(dispatchers.io) {
    limiter.acquire()
    try {
        withRetry(maxAttempts = maxAttempts) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw e.toProviderException()
    } catch (e: SerializationException) {
        throw ProviderException.Parse(e.message, e)
    } catch (e: IOException) {
        throw ProviderException.Offline(e)
    } catch (e: Exception) {
        throw ProviderException.Unknown(e)
    }
}

fun HttpException.toProviderException(): ProviderException = when (code()) {
    401, 403 -> ProviderException.Unauthorized("HTTP ${code()}")
    404 -> ProviderException.Http(404, "not found")
    429 -> ProviderException.RateLimited(
        response()?.headers()?.get("Retry-After")?.toLongOrNull(),
    )
    in 500..599 -> ProviderException.Http(code(), "server error")
    else -> ProviderException.Http(code())
}
