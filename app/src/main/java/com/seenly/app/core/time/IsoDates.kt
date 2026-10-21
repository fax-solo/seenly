package com.seenly.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** ISO-8601 helpers used consistently for date storage. */
object IsoDates {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun LocalDate.toIso(): String = format(dateFormatter)

    fun String?.toLocalDateOrNull(): LocalDate? {
        if (this.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(this, dateFormatter) }.getOrNull()
    }

    fun LocalDate?.toIsoOrNull(): String? = this?.toIso()

    fun millisToLocalDate(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun localDateToStartOfDayMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun localDateToEndOfDayMillis(date: LocalDate, zone: ZoneId): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun localDateTime(millis: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
}
