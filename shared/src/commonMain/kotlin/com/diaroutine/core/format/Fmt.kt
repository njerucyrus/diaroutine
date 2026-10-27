package com.diaroutine.core.format

import com.diaroutine.core.time.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** English display formats. Kept in one place so localization has a single seam. */
object Fmt {
    private val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    fun time(time: LocalTime, use24Hour: Boolean): String = if (use24Hour) {
        "${time.hour.pad()}:${time.minute.pad()}"
    } else {
        val hour = (time.hour % 12).let { if (it == 0) 12 else it }
        "$hour:${time.minute.pad()} ${if (time.hour < 12) "AM" else "PM"}"
    }

    fun weekday(date: LocalDate): String = weekdays[date.dayOfWeek.ordinal]
    fun weekdayShort(date: LocalDate): String = weekday(date).take(3)
    fun month(date: LocalDate): String = months[date.month.ordinal]
    fun monthShort(date: LocalDate): String = month(date).take(3)

    /** "Tuesday, 27 October" */
    fun dateLong(date: LocalDate): String = "${weekday(date)}, ${date.day} ${month(date)}"

    /** "Tue, 27 Oct" */
    fun dateMedium(date: LocalDate): String = "${weekdayShort(date)}, ${date.day} ${monthShort(date)}"

    /** "27 Oct" */
    fun dateShort(date: LocalDate): String = "${date.day} ${monthShort(date)}"

    /** "21–27 Oct", or "29 Sep – 5 Oct" across months. */
    fun dateRange(from: LocalDate, to: LocalDate): String =
        if (from.month == to.month && from.year == to.year) "${from.day}–${to.day} ${monthShort(to)}"
        else "${dateShort(from)} – ${dateShort(to)}"

    fun relativeDate(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.plusDays(-1) -> "Yesterday"
        else -> dateMedium(date)
    }

    /** 12345 → "12,345" */
    fun grouped(value: Int): String {
        val groups = abs(value).toString().reversed().chunked(3).joinToString(",").reversed()
        return if (value < 0) "-$groups" else groups
    }

    /** 3200 → "3.2k", for tight spaces. */
    fun compact(value: Int): String {
        if (value < 1000) return value.toString()
        val tenths = (value / 100.0).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}k" else "${tenths / 10}.${tenths % 10}k"
    }

    fun percent(ratio: Float): String = "${(ratio * 100).roundToInt()}%"

    fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToInt()
        val sign = if (tenths < 0) "-" else ""
        return "$sign${abs(tenths) / 10}.${abs(tenths) % 10}"
    }

    private fun Int.pad(): String = toString().padStart(2, '0')
}
