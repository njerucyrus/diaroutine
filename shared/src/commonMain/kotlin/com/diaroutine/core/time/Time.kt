package com.diaroutine.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

const val MINUTES_PER_DAY = 24 * 60

val LocalTime.minuteOfDay: Int get() = hour * 60 + minute

fun timeOfMinute(minuteOfDay: Int): LocalTime {
    val m = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    return LocalTime(m / 60, m % 60)
}

val LocalDate.epochDay: Long get() = toEpochDays()

fun dateOfEpochDay(epochDay: Long): LocalDate = LocalDate.fromEpochDays(epochDay)

fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

fun LocalDate.startIn(zone: TimeZone): Instant = atStartOfDayIn(zone)

fun LocalDate.at(time: LocalTime): LocalDateTime = LocalDateTime(this, time)

fun LocalDateTime.instantIn(zone: TimeZone): Instant = toInstant(zone)

fun Instant.local(zone: TimeZone): LocalDateTime = toLocalDateTime(zone)

/** Every date from this one through [end], inclusive; empty when [end] is earlier. */
fun LocalDate.through(end: LocalDate): List<LocalDate> =
    generateSequence(this) { it.plusDays(1) }.takeWhile { it <= end }.toList()

/** Emits the current instant immediately and then every [period], for state that depends on the time of day. */
fun Clock.ticker(period: Duration): Flow<Instant> = flow {
    while (true) {
        emit(now())
        delay(period)
    }
}
