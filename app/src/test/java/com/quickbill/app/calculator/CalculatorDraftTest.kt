package com.quickbill.app.calculator

import com.quickbill.app.data.MAX_PRICE_PAISA
import com.quickbill.app.data.SaleLine
import com.quickbill.app.data.totalPaisa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorDraftTest {
    @Test
    fun `calculates a multi-item total and keeps each amount as a bill line`() {
        val draft = enter("120+250+80")

        assertEquals(listOf(12_000L, 25_000L), draft.committedAmounts)
        assertEquals("80", draft.entry)
        assertEquals(45_000L, draft.totalPaisa)
        assertEquals(
            listOf(12_000L, 25_000L, 8_000L).map { SaleLine(null, "Item / सामान", it, 1) },
            draft.toSaleLines(),
        )
        assertEquals(draft.totalPaisa, totalPaisa(draft.toSaleLines()))
    }

    @Test
    fun `one entered amount becomes an express total`() {
        val draft = enter("120")

        assertTrue(draft.canPrint)
        assertEquals(12_000L, draft.totalPaisa)
        assertEquals(listOf(SaleLine(null, "Total / जम्मा", 12_000L, 1)), draft.toSaleLines())
    }

    @Test
    fun `fractional rupees add exactly in paisa`() {
        val draft = enter("0.10+.20+0.01")

        assertEquals(31L, draft.totalPaisa)
        assertEquals(listOf(10L, 20L, 1L), draft.toSaleLines().map { it.unitPricePaisa })
    }

    @Test
    fun `empty entries and repeated plus do not become bill lines`() {
        assertFalse(enter("+++").canPrint)
        assertFalse(enter(".+").canPrint)
        val draft = enter("++120++.")

        assertTrue(draft.canPrint)
        assertEquals(12_000L, draft.totalPaisa)
        assertEquals(listOf(SaleLine(null, "Total / जम्मा", 12_000L, 1)), draft.toSaleLines())
    }

    @Test
    fun `explicit zero still counts as an entered amount`() {
        val draft = enter("0+0")

        assertTrue(draft.canPrint)
        assertEquals(0L, draft.totalPaisa)
        assertEquals(List(2) { SaleLine(null, "Item / सामान", 0L, 1) }, draft.toSaleLines())
    }

    @Test
    fun `backspace reopens a committed amount and clear resets all input`() {
        val draft = enter("1.25+2")
            .press(CalculatorKey.Backspace)
            .press(CalculatorKey.Backspace)
        assertEquals(emptyList<Long>(), draft.committedAmounts)
        assertEquals("1.25", draft.entry)
        assertEquals(125L, draft.totalPaisa)

        val edited = draft.press(CalculatorKey.Backspace).press(CalculatorKey.Digit('9'))
        assertEquals(129L, edited.totalPaisa)

        val cleared = edited.press(CalculatorKey.Clear)
        assertFalse(cleared.canPrint)
        assertEquals(0L, cleared.totalPaisa)
        assertTrue(cleared.toSaleLines().isEmpty())
    }

    @Test
    fun `amounts stay within the supported price and precision`() {
        val draft = enter("999999999.999.")

        assertEquals("99999999.99", draft.entry)
        assertEquals(MAX_PRICE_PAISA, draft.totalPaisa)
    }

    private fun enter(keys: String): CalculatorDraft = keys.fold(CalculatorDraft()) { draft, key ->
        draft.press(
            when (key) {
                '+' -> CalculatorKey.Plus
                '.' -> CalculatorKey.Decimal
                else -> CalculatorKey.Digit(key)
            }
        )
    }
}
