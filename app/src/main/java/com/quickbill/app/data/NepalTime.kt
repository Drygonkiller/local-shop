package com.quickbill.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

data class NepalDay(val startMillis: Long, val endMillis: Long) {
    operator fun contains(millis: Long): Boolean = millis >= startMillis && millis < endMillis
}

private val nepalTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kathmandu")

fun currentNepalDay(nowMillis: Long = System.currentTimeMillis()): NepalDay {
    val calendar = GregorianCalendar(nepalTimeZone, Locale.ENGLISH).apply {
        timeInMillis = nowMillis
        setStartOfDay()
    }
    val startMillis = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    calendar.setStartOfDay()
    return NepalDay(startMillis, calendar.timeInMillis)
}

fun formatNepalReportDate(day: NepalDay): String = formatNepalTime("d MMM yyyy", day.startMillis)

fun formatNepalBillDateTime(millis: Long): String = formatNepalTime("d MMM yyyy, HH:mm", millis)

fun formatNepalBillTime(millis: Long): String = formatNepalTime("HH:mm", millis)

fun formatNepalDualDate(millis: Long): String = formatNepalDualDate(millis, withTime = false)

fun formatNepalDualDateTime(millis: Long): String = formatNepalDualDate(millis, withTime = true)

private fun formatNepalDualDate(millis: Long, withTime: Boolean): String {
    val civilDate = GregorianCalendar(nepalTimeZone, Locale.ENGLISH).apply { timeInMillis = millis }
    val bs = bikramSambatDate(civilDate)
    val ad = formatNepalTime("d MMM yyyy", millis)
    return buildString {
        if (bs == null) append(ad) else append("${bs.day} ${bs.monthName} ${bs.year} BS ($ad)")
        if (withTime) append(", ${formatNepalBillTime(millis)}")
        if (bs?.isProvisional == true) append(" · Provisional / अनुमानित")
    }
}

private fun formatNepalTime(pattern: String, millis: Long): String =
    SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = nepalTimeZone }.format(Date(millis))

private fun Calendar.setStartOfDay() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}
