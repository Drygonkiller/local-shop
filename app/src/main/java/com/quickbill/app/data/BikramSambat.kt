package com.quickbill.app.data

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

private const val FIRST_BS_YEAR = 2080
private const val LAST_CONFIRMED_BS_YEAR = 2083
private const val MILLIS_PER_DAY = 86_400_000L
private val utc = TimeZone.getTimeZone("UTC")

// Nepal Rastra Bank gives 1 Baisakh 2080 as 14 April 2023.
// https://www.nrb.org.np/contents/uploads/2023/03/Holiday-list-2080-Nepali.pdf
private val anchorUtcMillis = utcCivilMidnight(2023, Calendar.APRIL, 14)

// Month lengths from Kathmandu Metropolitan City's calendar, in Baisakh-to-Chaitra order.
// https://kathmandu.gov.np/en/calendar
// Pinned transcription:
// https://github.com/KDE/kdeplasma-addons/blob/1445c417500c2a206691d0710005f64dc8efdaee/plasmacalendarplugins/alternatecalendar/provider/nepalicalendar.cpp
// Official Panchanga confirmation through 2083. Mark the 2084–2090 values provisional.
private val monthLengths = arrayOf(
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30), // 2080
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2081
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2082
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2083
    intArrayOf(31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30), // 2084
    intArrayOf(31, 32, 31, 32, 30, 31, 30, 30, 29, 30, 30, 30), // 2085
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30), // 2086
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 30, 30, 30), // 2087
    intArrayOf(30, 31, 32, 32, 30, 31, 30, 30, 29, 30, 30, 30), // 2088
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30), // 2089
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30), // 2090
)

private val monthNames = listOf(
    "वैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज", "कात्तिक", "मंसिर", "पुस", "माघ", "फागुन", "चैत",
)

internal data class BikramSambatDate(val year: Int, val monthName: String, val day: Int) {
    val isProvisional: Boolean get() = year > LAST_CONFIRMED_BS_YEAR
}

internal fun bikramSambatDate(civilDate: Calendar): BikramSambatDate? {
    val adYear = civilDate.get(Calendar.YEAR)
    if (civilDate.get(Calendar.ERA) != GregorianCalendar.AD || adYear !in 2023..2034) return null

    // Place the Kathmandu civil date on the UTC calendar purely to count whole civil days.
    val civilUtcMillis = utcCivilMidnight(adYear, civilDate.get(Calendar.MONTH), civilDate.get(Calendar.DAY_OF_MONTH))
    var remainingDays = (civilUtcMillis - anchorUtcMillis) / MILLIS_PER_DAY
    if (remainingDays < 0) return null

    for ((yearIndex, months) in monthLengths.withIndex()) {
        for ((monthIndex, length) in months.withIndex()) {
            if (remainingDays < length) {
                return BikramSambatDate(FIRST_BS_YEAR + yearIndex, monthNames[monthIndex], remainingDays.toInt() + 1)
            }
            remainingDays -= length
        }
    }
    return null
}

private fun utcCivilMidnight(year: Int, month: Int, day: Int): Long = GregorianCalendar(utc, Locale.ENGLISH).apply {
    clear()
    set(year, month, day)
}.timeInMillis
