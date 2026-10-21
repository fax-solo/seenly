package com.seenly.app.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Simple sliding-window rate limiter. Providers must stay inside their published
 * limits; this class serializes calls and spaces them out when necessary.
 *
 * Fully injectable clock/sleeper so behaviour is unit-testable with virtual time.
 */
class RateLimiter(
    private val maxPerMinute: Int,
    private val minIntervalMillis: Long = 0,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    suspend fun acquire() {
        while (true) {
            val waitMillis = mutex.withLock {
                val now = clock()
                while (timestamps.isNotEmpty() && now - timestamps.first() >= 60_000) {
                    timestamps.removeFirst()
                }
                val windowWait = if (timestamps.size >= maxPerMinute) {
                    (timestamps.first() + 60_000) - now
                } else {
                    0L
                }
                val spacingWait = timestamps.lastOrNull()?.let { last ->
                    (last + minIntervalMillis) - now
                } ?: 0L
                val wait = maxOf(windowWait, spacingWait, 0L)
                if (wait <= 0L) {
                    timestamps.addLast(now)
                    return
                }
                wait
            }
            sleeper(waitMillis + 5)
        }
    }
}

/**
 * Retries transient failures with exponential backoff. Rate limits always wait
 * for the provider-supplied retry window (capped), server errors back off
 * exponentially, and non-recoverable errors (auth/parse/404) fail fast.
 */
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    baseDelayMillis: Long = 600,
    maxDelayMillis: Long = 20_000,
    sleeper: suspend (Long) -> Unit = { delay(it) },
    block: suspend (attempt: Int) -> T,
): T {
    var attempt = 0
    while (true) {
        attempt += 1
        try {
            return block(attempt)
        } catch (e: ProviderException) {
            val delayMillis = when {
                e is ProviderException.RateLimited ->
                    e.retryAfterSeconds?.let { (it * 1000).coerceAtMost(maxDelayMillis) }
                        ?: (baseDelayMillis * attempt)
                e is ProviderException.Http && e.code >= 500 -> baseDelayMillis * (1L shl (attempt - 1))
                e is ProviderException.Unavailable -> baseDelayMillis * (1L shl (attempt - 1))
                e is ProviderException.Offline -> {
                    // One quick retry for flaky connections, then give up.
                    if (attempt >= 2) throw e
                    baseDelayMillis
                }
                else -> throw e
            }
            if (attempt >= maxAttempts) throw e
            sleeper(delayMillis.coerceAtMost(maxDelayMillis))
        }
    }
}
