package com.quickbill.app.printing

import com.quickbill.app.data.Bill
import com.quickbill.app.data.CreditEntry
import com.quickbill.app.data.CreditLedger
import com.quickbill.app.data.Customer
import com.quickbill.app.data.Payment
import java.math.BigInteger
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerTextTest {
    private val customer = Customer(id = 1, name = "माया श्रेष्ठ")
    private val timestamp = Instant.parse("2026-11-15T14:28:00Z").toEpochMilli()

    @Test
    fun `open-balance reminder shows due and recent credit and payment in ledger order`() {
        val bill = Bill(4, "Test shop", "123456789", timestamp, 30_000, customer.id, customer.name)
        val payment = Payment(customerId = customer.id, amountPaisa = 12_500, createdAtMillis = timestamp + 60_000)
        val ledger = CreditLedger(
            customer = customer,
            duePaisa = BigInteger.valueOf(17_500),
            entries = listOf(CreditEntry.PaymentEntry(payment), CreditEntry.BillEntry(bill)),
        )

        assertEquals(
            """
            Test shop
            Udharo reminder / उधारो भुक्तानीको सम्झना
            Customer / ग्राहक: माया श्रेष्ठ
            Balance due / बाँकी रकम: Rs. 175
            Please pay the remaining balance. / कृपया बाँकी रकम तिर्नुहोस्।

            Recent entries / पछिल्ला कारोबार
            Payment / भुक्तानी · 29 कात्तिक 2083 BS (15 Nov 2026), 20:14 · Rs. 125
            Udharo bill / उधारो बिल #4 · 29 कात्तिक 2083 BS (15 Nov 2026), 20:13 · Rs. 300
            """.trimIndent(),
            LedgerText.reminder("Test shop", ledger),
        )
    }

    @Test
    fun `settled statement shows no request for payment`() {
        val ledger = CreditLedger(customer, BigInteger.ZERO, emptyList())

        assertEquals(
            """
            Test shop
            Udharo statement / उधारो विवरण
            Customer / ग्राहक: माया श्रेष्ठ
            Balance due / बाँकी रकम: Rs. 0
            """.trimIndent(),
            LedgerText.reminder("Test shop", ledger),
        )
    }
}
