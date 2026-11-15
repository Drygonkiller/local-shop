package com.quickbill.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NepalTimeTest {
    @Test
    fun `today begins at Nepal midnight and stops before the next Nepal midnight`() {
        val start = millis("2026-11-14T18:15:00Z")
        val end = millis("2026-11-15T18:15:00Z")
        val day = currentNepalDay(millis("2026-11-15T04:00:00Z"))

        assertEquals(NepalDay(start, end), day)
        assertFalse(start - 1 in day)
        assertTrue(start in day)
        assertTrue(end - 1 in day)
        assertFalse(end in day)
        assertEquals(currentNepalDay(start - 1).endMillis, start)
        assertEquals(day, currentNepalDay(end - 1))
        assertEquals(end, currentNepalDay(end).startMillis)
    }

    @Test
    fun `report date and bill times match Nepal local time on either side of midnight`() {
        val midnight = millis("2026-11-14T18:15:00Z")
        val day = currentNepalDay(midnight)

        assertEquals("15 Nov 2026", formatNepalReportDate(day))
        assertEquals("14 Nov 2026, 23:59", formatNepalBillDateTime(midnight - 1))
        assertEquals("15 Nov 2026, 00:00", formatNepalBillDateTime(midnight))
        assertEquals("12:45", formatNepalBillTime(millis("2026-11-15T07:00:00Z")))
    }

    @Test
    fun `dual dates follow the published Nepali new-year and month boundaries`() {
        assertEquals("1 वैशाख 2082 BS (14 Apr 2025)", dualDate("2025-04-14T06:00:00Z"))
        assertEquals("30 चैत 2082 BS (13 Apr 2026)", dualDate("2026-04-13T06:00:00Z"))
        assertEquals("1 वैशाख 2083 BS (14 Apr 2026)", dualDate("2026-04-14T06:00:00Z"))
        assertEquals("29 कात्तिक 2083 BS (15 Nov 2026)", dualDate("2026-11-15T06:00:00Z"))
        assertEquals("30 कात्तिक 2083 BS (16 Nov 2026)", dualDate("2026-11-16T06:00:00Z"))
        assertEquals("1 मंसिर 2083 BS (17 Nov 2026)", dualDate("2026-11-17T06:00:00Z"))
    }

    @Test
    fun `dual dates roll at Kathmandu midnight and include Kathmandu time`() {
        val midnight = millis("2026-11-14T18:15:00Z")

        assertEquals("28 कात्तिक 2083 BS (14 Nov 2026), 23:59", formatNepalDualDateTime(midnight - 1))
        assertEquals("29 कात्तिक 2083 BS (15 Nov 2026), 00:00", formatNepalDualDateTime(midnight))
        assertEquals(
            "29 कात्तिक 2083 BS (15 Nov 2026), 20:13",
            formatNepalDualDateTime(millis("2026-11-15T14:28:00Z")),
        )
    }

    @Test
    fun `only confirmed years omit the provisional label`() {
        assertEquals("30 चैत 2083 BS (13 Apr 2027)", dualDate("2027-04-13T06:00:00Z"))
        assertEquals(
            "1 वैशाख 2084 BS (14 Apr 2027) · Provisional / अनुमानित",
            dualDate("2027-04-14T06:00:00Z"),
        )
        assertEquals(
            "1 वैशाख 2084 BS (14 Apr 2027), 11:45 · Provisional / अनुमानित",
            formatNepalDualDateTime(millis("2027-04-14T06:00:00Z")),
        )
    }

    @Test
    fun `table starts at the 2080 anchor and includes the final day of 2090`() {
        val first = millis("2023-04-13T18:15:00Z")
        val afterLast = millis("2034-04-13T18:15:00Z")

        assertEquals("13 Apr 2023", formatNepalDualDate(first - 1))
        assertEquals("1 वैशाख 2080 BS (14 Apr 2023)", formatNepalDualDate(first))
        assertEquals("30 चैत 2090 BS (13 Apr 2034) · Provisional / अनुमानित", formatNepalDualDate(afterLast - 1))
        assertEquals("14 Apr 2034", formatNepalDualDate(afterLast))
        assertEquals("14 Apr 2034, 00:00", formatNepalDualDateTime(afterLast))
    }

    private fun dualDate(instant: String): String = formatNepalDualDate(millis(instant))

    private fun millis(instant: String): Long = Instant.parse(instant).toEpochMilli()
}
