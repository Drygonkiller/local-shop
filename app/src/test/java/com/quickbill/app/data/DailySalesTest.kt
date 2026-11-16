package com.quickbill.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class DailySalesTest {
    private val day = currentNepalDay(1_784_000_000_000)

    @Test
    fun `empty day has no bills and zero totals`() {
        val report = summarizeDailySales(day, emptyList())

        assertEquals(day, report.day)
        assertEquals(0, report.billCount)
        assertEquals(BigInteger.ZERO, report.totalPaisa)
        assertEquals(0L, report.averagePaisa)
        assertTrue(report.topItems.isEmpty())
    }

    @Test
    fun `credit sales stay in the totals and have a separate Nepal-day subtotal`() {
        val older = sale(1, day.startMillis - 1, 5_000L)
        val cash = sale(2, day.startMillis, 1_250L)
        val credit = sale(3, day.endMillis - 1, 2_001L)
        val report = summarizeDailySales(
            day,
            listOf(
                older.copy(bill = older.bill.copy(customerId = 7, customerName = "Customer")),
                cash,
                credit.copy(bill = credit.bill.copy(customerId = 7, customerName = "Customer")),
            ),
        )

        assertEquals(2, report.billCount)
        assertEquals(3_251L.toBigInteger(), report.totalPaisa)
        assertEquals(1_626L, report.averagePaisa)
        assertEquals(1, report.udharoBillCount)
        assertEquals(2_001L.toBigInteger(), report.udharoTotalPaisa)
    }

    @Test
    fun `yesterday and tomorrow are excluded from the day's report`() {
        val yesterday = sale(1, day.startMillis - 1, 500L, line(1, "Yesterday's item", 500))
        val today = sale(2, day.startMillis, 200L, line(2, "Today's item", 200))
        val tomorrow = sale(3, day.endMillis, 700L, line(3, "Tomorrow's item", 700))

        val report = summarizeDailySales(day, listOf(tomorrow, today, yesterday))

        assertEquals(listOf(2L), report.bills.map { it.billNumber })
        assertEquals(200L.toBigInteger(), report.totalPaisa)
        assertEquals(listOf(2L), report.topItems.map { it.favouriteId })
    }

    @Test
    fun `an express-only day sums bill amounts and has no top items`() {
        val first = sale(1, day.startMillis, 12_550L, line(null, "Total / जम्मा", 12_550))
        val second = sale(2, day.endMillis - 1, 2_051L, line(null, "Total / जम्मा", 2_051))

        val report = summarizeDailySales(day, listOf(first, second))

        assertEquals(2, report.billCount)
        assertEquals(14_601L.toBigInteger(), report.totalPaisa)
        assertEquals(7_301L, report.averagePaisa)
        assertTrue(report.topItems.isEmpty())
    }

    @Test
    fun `all modes count toward sales but only favourite snapshots are ranked`() {
        val first = sale(1, day.startMillis, 1_200L,
            line(10, "Old name", 200, 2), line(20, "Deleted favourite", 75, 4), line(null, "Item", 500))
        val second = sale(2, day.startMillis + 10, 1_050L,
            line(10, "New name", 1_000), line(30, "Milk", 50))
        val calculator = sale(3, day.startMillis + 20, 375L, line(null, "Item", 125), line(null, "Item", 250))
        val express = sale(4, day.startMillis + 30, 400L, line(null, "Total", 400))

        val report = summarizeDailySales(day, listOf(calculator, first, express, second))

        assertEquals(listOf(4L, 3L, 2L, 1L), report.bills.map { it.billNumber })
        assertEquals(4, report.billCount)
        assertEquals(3_025L.toBigInteger(), report.totalPaisa)
        assertEquals(756L, report.averagePaisa)
        assertEquals(
            listOf(
                TopFavourite(10, "New name", 1_400L.toBigInteger()),
                TopFavourite(20, "Deleted favourite", 300L.toBigInteger()),
                TopFavourite(30, "Milk", 50L.toBigInteger()),
            ),
            report.topItems,
        )
    }

    @Test
    fun `rank only five favourites by revenue and break ties by favourite id`() {
        val bill = sale(1, day.startMillis, 1_210L,
            line(9, "High unit price", 200), line(8, "Multi-quantity", 100, 3),
            line(7, "Only one", 240), line(6, "Tied item", 120, 2),
            line(5, "Same name", 20, 5), line(4, "Same name", 65, 2))

        val report = summarizeDailySales(day, listOf(bill))

        assertEquals(listOf(8L, 6L, 7L, 9L, 4L), report.topItems.map { it.favouriteId })
    }

    @Test
    fun `same timestamp orders by bill number and uses the newest snapshot name`() {
        val first = sale(1, day.startMillis, 100L, line(10, "Old name", 100))
        val second = sale(2, day.startMillis, 100L, line(10, "New name", 100))

        val report = summarizeDailySales(day, listOf(first, second))

        assertEquals(listOf(2L, 1L), report.bills.map { it.billNumber })
        assertEquals("New name", report.topItems.single().name)
    }

    @Test
    fun `average is rounded to the nearest paisa with midpoint rounding up`() {
        val one = sale(1, day.startMillis, 1L)
        val two = sale(2, day.startMillis, 2L)
        val three = sale(3, day.startMillis, 0L)

        assertEquals(2L, summarizeDailySales(day, listOf(one, two)).averagePaisa)
        assertEquals(1L, summarizeDailySales(day, listOf(one, two, three)).averagePaisa)
    }

    @Test
    fun `sales and ranked totals may exceed a single bill's storage range`() {
        val first = sale(1, day.startMillis, Long.MAX_VALUE, line(10, "Large sale", Long.MAX_VALUE))
        val second = sale(2, day.startMillis, Long.MAX_VALUE, line(10, "Large sale", Long.MAX_VALUE))

        val report = summarizeDailySales(day, listOf(first, second))

        assertEquals(BigInteger("18446744073709551614"), report.totalPaisa)
        assertEquals(report.totalPaisa, report.topItems.single().totalPaisa)
        assertEquals(Long.MAX_VALUE, report.averagePaisa)
        assertEquals("Rs. 184,467,440,737,095,516.14", formatNpr(report.totalPaisa))
    }

    private fun sale(number: Long, time: Long, total: Long, vararg lines: BillLine): DailySalesBill = DailySalesBill(
        bill = Bill(number, "Test shop", "123456789", time, total),
        lines = lines.mapIndexed { index, line -> line.copy(billNumber = number, position = index) },
    )

    private fun line(id: Long?, name: String, price: Long, quantity: Int = 1): BillLine =
        BillLine(0, 0, id, name, price, quantity)
}
