package com.quickbill.app.printing

import com.quickbill.app.data.Bill
import com.quickbill.app.data.BillLine
import com.quickbill.app.data.SaleLine
import com.quickbill.app.data.SavedBill
import com.quickbill.app.data.ShopDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class BillTextTest {
    @Test
    fun `sharing a saved bill uses receipt labels and Kathmandu time`() {
        val bill = Bill(7, "Test shop", "123456789", 0L, 2_450L)
        val line = BillLine(7, 0, 1, "चिया", 1_225L, 2)

        assertEquals(
            """
            Test shop
            PAN / प्यान 123456789
            Bill no. / बिल नं. 7
            Date / मिति 1 Jan 1970, 05:30

            चिया
            2 × Rs. 12.25  Rs. 24.50

            Total / जम्मा  Rs. 24.50
            """.trimIndent(),
            BillText.saved(SavedBill(bill, listOf(line))),
        )
    }

    @Test
    fun `sharing the current bill uses an unnumbered draft`() {
        val text = BillText.draft(
            ShopDetails("Test shop", "123456789"), 0L, listOf(SaleLine(1, "चिया", 2_000L, 1)),
        )
        assertEquals("Draft / मस्यौदा", text.lineSequence().first())
        assertEquals("Bill no. / बिल नं. —", text.lineSequence().elementAt(3))
        assertEquals("Total / जम्मा  Rs. 20", text.lineSequence().last())
    }
}
