package com.quickbill.app.ui

import com.quickbill.app.data.Bill
import com.quickbill.app.data.FavouriteItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BillingUiLogicTest {
    @Test
    fun `normalizes English and Nepali bill number digits`() {
        assertEquals("1204", billNumberQuery(" Bill #१२-0४ "))
        assertEquals("", billNumberQuery("Bill #"))
    }

    @Test
    fun `finds exact or partial bill numbers without changing history order`() {
        val history = listOf(bill(214), bill(121), bill(31), bill(12))

        assertEquals(listOf(121L), filterHistory(history, "१२1").map { it.billNumber })
        assertEquals(listOf(121L, 12L), filterHistory(history, " #१२ ").map { it.billNumber })
        assertEquals(emptyList<Bill>(), filterHistory(history, "88"))
        assertSame(history, filterHistory(history, ""))
    }

    @Test
    fun `matches favourite names across both label languages in supplied order`() {
        val favourites = listOf(
            favourite(8, "Milk tea"),
            favourite(1, "चिया"),
            favourite(5, "TEA biscuits"),
            favourite(3, "Rice"),
        )

        assertEquals(listOf(8L, 5L), filterFavourites(favourites, " TeA ").map { it.id })
        assertEquals(listOf(1L), filterFavourites(favourites, "चि").map { it.id })
        assertEquals(emptyList<FavouriteItem>(), filterFavourites(favourites, "coffee"))
        assertSame(favourites, filterFavourites(favourites, " "))
    }

    @Test
    fun `subtracts exact paisa and returns zero for exact payment`() {
        assertEquals(115L, calculateChange(3_335, 3_450))
        assertEquals(0L, calculateChange(3_335, 3_335))
        assertEquals(Long.MAX_VALUE - 1, calculateChange(1, Long.MAX_VALUE))
    }

    @Test
    fun `returns no change for missing or insufficient cash`() {
        assertNull(calculateChange(3_335, null))
        assertNull(calculateChange(3_335, 3_334))
        assertNull(calculateChange(0, -1))
    }

    private fun bill(number: Long) = Bill(
        billNumber = number,
        shopName = "Test shop",
        shopPan = "123456789",
        createdAtMillis = 0,
        totalPaisa = 0,
    )

    private fun favourite(id: Long, name: String) =
        FavouriteItem(id = id, name = name, unitPricePaisa = 0)
}
