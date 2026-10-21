package com.seenly.app.core.common

import java.time.LocalDate
import java.time.ZoneId

/**
 * Time source abstraction. All timestamps are stored as epoch milliseconds (UTC)
 * and all calendar dates as ISO-8601 local dates in the device time zone at the
 * moment the event happened.
 */
interface AppClock {
    fun nowMillis(): Long
    fun zone(): ZoneId
    fun today(): LocalDate = LocalDate.now(zone())
}

class SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Fixed clock for tests. */
class FixedAppClock(
    private var now: Long,
    private var zone: ZoneId = ZoneId.of("UTC"),
) : AppClock {
    override fun nowMillis(): Long = now
    override fun zone(): ZoneId = zone
    fun advanceTo(millis: Long) { now = millis }
    fun setZone(z: ZoneId) { zone = z }
}
