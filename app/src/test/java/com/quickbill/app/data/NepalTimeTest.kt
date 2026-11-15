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

    private fun millis(instant: String): Long = Instant.parse(instant).toEpochMilli()
}
