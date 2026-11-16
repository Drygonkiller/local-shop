package com.quickbill.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentAmountTest {
    @Test
    fun `payments can exceed the item price limit while remaining exact`() {
        assertNull(parseNpr("100000000"))
        assertEquals(10_000_000_000L, parseNpr("100000000", maxPaisa = Long.MAX_VALUE))
        assertEquals(1_253L, parseNpr("१२.५३", maxPaisa = Long.MAX_VALUE))
    }

    @Test
    fun `payment input rejects values above Long without wrapping`() {
        assertEquals(Long.MAX_VALUE, parseNpr("92233720368547758.07", maxPaisa = Long.MAX_VALUE))
        assertNull(parseNpr("92233720368547758.08", maxPaisa = Long.MAX_VALUE))
    }
}
