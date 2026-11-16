package com.quickbill.app.data

import com.quickbill.app.calculator.CalculatorDraft
import com.quickbill.app.calculator.CalculatorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class CreditLedgerTest {
    private val customer = Customer(id = 1, name = "Maya", phone = "9800000000")

    @Test
    fun `a new customer has no entries and no balance`() {
        val rows = CustomerCreditRows(customer, emptyList(), emptyList())

        assertEquals(CreditSummary(customer, BigInteger.ZERO, null), summarizeCredit(rows))
        assertEquals(CreditLedger(customer, BigInteger.ZERO, BigInteger.ZERO, emptyList()), customerLedger(rows))
    }

    @Test
    fun `favourite calculator and express bills add to one customer balance`() {
        val favouriteTotal = totalPaisa(listOf(SaleLine(1, "Tea", 12_050, 2)))
        val calculatorTotal = totalPaisa(enter("120+250+80").toSaleLines())
        val expressTotal = totalPaisa(enter("300").toSaleLines())
        val bills = listOf(
            bill(number = 1, amount = favouriteTotal, createdAt = 100),
            bill(number = 2, amount = calculatorTotal, createdAt = 200),
            bill(number = 3, amount = expressTotal, createdAt = 300),
        )
        val ledger = customerLedger(CustomerCreditRows(customer, bills, emptyList()))

        assertEquals(99_100L.toBigInteger(), ledger.duePaisa)
        assertEquals(listOf(30_000L, 45_000L, 24_100L), ledger.entries.map { it.amountPaisa })
    }

    @Test
    fun `unpaid credit charges and partial payments calculate the exact balance`() {
        val first = bill(number = 2, amount = 12_105, createdAt = 100)
        val second = bill(number = 5, amount = 4_150, createdAt = 200)
        val payment = payment(id = 1, amount = 7_235, createdAt = 300)
        val rows = CustomerCreditRows(customer, listOf(first, second), listOf(payment))

        assertEquals(9_020L.toBigInteger(), creditDue(rows.bills, rows.payments))
        assertEquals(CreditSummary(customer, 9_020L.toBigInteger(), 300L), summarizeCredit(rows))
        assertEquals(
            listOf(CreditEntry.PaymentEntry(payment), CreditEntry.BillEntry(second), CreditEntry.BillEntry(first)),
            customerLedger(rows).entries,
        )
    }

    @Test
    fun `deleting a credit bill leaves due from the remaining bills and payments`() {
        val kept = bill(number = 4, amount = 12_105, createdAt = 100)
        val deleted = bill(number = 5, amount = 4_150, createdAt = 200)
        val received = payment(id = 1, amount = 7_235, createdAt = 300)
        val before = CustomerCreditRows(customer, listOf(kept, deleted), listOf(received))
        val after = before.copy(bills = before.bills - deleted)

        assertEquals(9_020L.toBigInteger(), customerLedger(before).duePaisa)
        val ledger = customerLedger(after)
        assertEquals(4_870L.toBigInteger(), ledger.duePaisa)
        assertEquals(BigInteger.ZERO, ledger.advancePaisa)
        assertEquals(listOf(CreditEntry.PaymentEntry(received), CreditEntry.BillEntry(kept)), ledger.entries)
    }

    @Test
    fun `payments above the remaining charges are an advance after bill deletion`() {
        val kept = bill(number = 4, amount = 9_501, createdAt = 100)
        val deleted = bill(number = 5, amount = 4_150, createdAt = 200)
        val received = payment(id = 1, amount = 12_000, createdAt = 300)
        val before = CustomerCreditRows(customer, listOf(kept, deleted), listOf(received))
        val after = before.copy(bills = before.bills - deleted)

        assertEquals(1_651L.toBigInteger(), customerLedger(before).duePaisa)
        val ledger = customerLedger(after)
        assertEquals(BigInteger.ZERO, ledger.duePaisa)
        assertEquals(2_499L.toBigInteger(), ledger.advancePaisa)
        assertEquals(BigInteger.ZERO, summarizeCredit(after).duePaisa)
    }

    @Test
    fun `payment is rejected when deleting the last bill leaves no due`() {
        val received = payment(id = 1, amount = 300, createdAt = 200)
        val before = CustomerCreditRows(customer, listOf(bill(number = 5, amount = 800, createdAt = 100)), listOf(received))
        validateCreditPayment(1, creditDue(before.bills, before.payments))

        val after = before.copy(bills = emptyList())
        val due = creditDue(after.bills, after.payments)
        assertEquals(BigInteger.ZERO, due)
        assertThrows(InvalidCreditPaymentException::class.java) { validateCreditPayment(1, due) }
    }

    @Test
    fun `a customer rename keeps the original name on the saved bill`() {
        val bill = bill(number = 2, amount = 100, createdAt = 100)
        val renamed = customer.copy(name = "Maya Devi", phone = null)
        val ledger = customerLedger(CustomerCreditRows(renamed, listOf(bill), emptyList()))

        assertEquals("Maya Devi", ledger.customer.name)
        assertEquals("Maya", (ledger.entries.single() as CreditEntry.BillEntry).bill.customerName)
    }

    @Test
    fun `payment validation accepts partial and full payments and rejects excess or non-positive amounts`() {
        val bill = bill(number = 3, amount = 800, createdAt = 100)
        val existing = payment(id = 1, amount = 300, createdAt = 200)
        val due = creditDue(listOf(bill), listOf(existing))

        validateCreditPayment(1, due)
        validateCreditPayment(500, due)
        for (invalid in listOf(-10L, 0L, 501L)) {
            assertThrows(InvalidCreditPaymentException::class.java) { validateCreditPayment(invalid, due) }
        }
        val fullPayment = payment(id = 2, amount = 500, createdAt = 300)
        val settled = creditDue(listOf(bill), listOf(existing, fullPayment))
        assertEquals(BigInteger.ZERO, settled)
        assertThrows(InvalidCreditPaymentException::class.java) { validateCreditPayment(1, settled) }
    }

    @Test
    fun `ledger sums across multiple Long-sized records without overflow`() {
        val bills = listOf(
            bill(number = 1, amount = Long.MAX_VALUE, createdAt = 100),
            bill(number = 2, amount = Long.MAX_VALUE, createdAt = 200),
        )
        val due = creditDue(bills, listOf(payment(id = 1, amount = Long.MAX_VALUE - 1, createdAt = 300)))

        assertEquals(BigInteger("9223372036854775808"), due)
        validateCreditPayment(Long.MAX_VALUE, due)
    }

    @Test
    fun `khata orders amounts owed largest first while the picker orders by activity`() {
        val olderWithHigherBalance = CreditSummary(customer, 2_000L.toBigInteger(), 100L)
        val recentWithLowerBalance = CreditSummary(customer.copy(id = 2), 100L.toBigInteger(), 200L)
        val settled = summarizeCredit(
            CustomerCreditRows(
                customer.copy(id = 3),
                listOf(bill(number = 3, amount = 300, createdAt = 100).copy(customerId = 3)),
                listOf(payment(id = 1, amount = 300, createdAt = 300).copy(customerId = 3)),
            ),
        )
        val newlyCreated = summarizeCredit(CustomerCreditRows(customer.copy(id = 4), emptyList(), emptyList()))
        val rows = listOf(settled, newlyCreated, recentWithLowerBalance, olderWithHigherBalance)

        assertEquals(listOf(1L, 2L, 3L, 4L), rows.sortedWith(khataCustomerOrder).map { it.customer.id })
        assertEquals(listOf(2L, 1L, 3L, 4L), rows.sortedWith(creditPickerOrder).map { it.customer.id })
    }

    private fun bill(number: Long, amount: Long, createdAt: Long): Bill = Bill(
        billNumber = number,
        shopName = "Test shop",
        shopPan = "123456789",
        createdAtMillis = createdAt,
        totalPaisa = amount,
        customerId = customer.id,
        customerName = customer.name,
    )

    private fun payment(id: Long, amount: Long, createdAt: Long): Payment =
        Payment(id, customer.id, amount, createdAt)

    private fun enter(keys: String): CalculatorDraft = keys.fold(CalculatorDraft()) { draft, key ->
        draft.press(if (key == '+') CalculatorKey.Plus else CalculatorKey.Digit(key))
    }
}
