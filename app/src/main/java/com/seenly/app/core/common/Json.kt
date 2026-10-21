package com.seenly.app.core.common

import kotlinx.serialization.json.Json

/**
 * Single JSON configuration used across the app (persistence, backups, cache).
 * Lenient on input so newer/older payloads do not break the app; strict on
 * output for stable files.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}
