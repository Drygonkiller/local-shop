package com.quickbill.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NprWordsTest {
    @Test
    fun `formats the whole and fractional receipt examples`() {
        assertEquals("Four hundred fifty rupees only", formatNprInWords(45_000))
        assertEquals(
            "One lakh twenty-five thousand five hundred rupees and seventy-five paisa only",
            formatNprInWords(12_550_075),
        )
    }

    @Test
    fun `uses Nepal grouping at powers of ten`() {
        assertEquals("One hundred rupees only", formatNprInWords(10_000))
        assertEquals("One thousand rupees only", formatNprInWords(100_000))
        assertEquals("One lakh rupees only", formatNprInWords(10_000_000))
        assertEquals("One crore rupees only", formatNprInWords(1_000_000_000))
        assertEquals("One arab rupees only", formatNprInWords(100_000_000_000))
        assertEquals("One kharab rupees only", formatNprInWords(10_000_000_000_000))
    }

    @Test
    fun `includes paisa only when there is a remainder`() {
        assertEquals("Zero rupees only", formatNprInWords(0))
        assertEquals("Zero rupees and one paisa only", formatNprInWords(1))
        assertEquals("One rupee only", formatNprInWords(100))
        assertEquals("One rupee and ninety-nine paisa only", formatNprInWords(199))
    }

    @Test
    fun `retains every group at the largest supported amount`() {
        assertEquals(
            "Nine lakh twenty-two thousand three hundred thirty-seven kharab twenty arab " +
                "thirty-six crore eighty-five lakh forty-seven thousand seven hundred fifty-eight " +
                "rupees and seven paisa only",
            formatNprInWords(Long.MAX_VALUE),
        )
    }
}
