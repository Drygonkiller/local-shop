package com.quickbill.app.printing

import com.quickbill.app.data.Bill
import com.quickbill.app.data.BillLine
import com.quickbill.app.data.SaleLine
import com.quickbill.app.data.SavedBill
import com.quickbill.app.data.ShopDetails
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillTextTest {
    @Test
    fun `sharing a saved bill uses both calendar dates and spells out the total`() {
        val timestamp = Instant.parse("2026-11-15T14:28:00Z").toEpochMilli()
        val bill = Bill(7, "Test shop", "123456789", timestamp, 2_450L)
        val line = BillLine(7, 0, 1, "चिया", 1_225L, 2)

        assertEquals(
            """
            Test shop
            PAN / प्यान 123456789
            Bill no. / बिल नं. 7
            Date / मिति 29 कात्तिक 2083 BS (15 Nov 2026), 20:13

            चिया
            2 × Rs. 12.25  Rs. 24.50

            Total / जम्मा  Rs. 24.50
            In words / अक्षरमा: Twenty-four rupees and fifty paisa only
            """.trimIndent(),
            BillText.saved(SavedBill(bill, listOf(line))),
        )
    }

    @Test
    fun `sharing an udharo receipt names the customer on the saved bill`() {
        val bill = Bill(
            billNumber = 8,
            shopName = "Test shop",
            shopPan = "123456789",
            createdAtMillis = 0,
            totalPaisa = 2_450,
            customerId = 1,
            customerName = "माया श्रेष्ठ",
        )
        val line = BillLine(8, 0, 1, "चिया", 1_225L, 2)

        val text = BillText.saved(SavedBill(bill, listOf(line)))

        assertEquals("Udharo / उधारो: माया श्रेष्ठ", text.lines()[4])
        assertTrue(text.endsWith("In words / अक्षरमा: Twenty-four rupees and fifty paisa only"))
        assertFalse(BillText.saved(SavedBill(bill.copy(customerId = null), listOf(line))).contains("Udharo / उधारो"))
    }

    @Test
    fun `sharing a draft can fall back to the Gregorian date`() {
        val text = BillText.draft(
            ShopDetails("Test shop", "123456789"), 0L, listOf(SaleLine(1, "चिया", 2_000L, 1)),
        )
        assertEquals("Draft / मस्यौदा", text.lineSequence().first())
        assertEquals("Bill no. / बिल नं. —", text.lineSequence().elementAt(3))
        assertEquals("Date / मिति 1 Jan 1970, 05:30", text.lineSequence().elementAt(4))
        assertEquals(
            listOf("Total / जम्मा  Rs. 20", "In words / अक्षरमा: Twenty rupees only"),
            text.lines().takeLast(2),
        )
    }
}
