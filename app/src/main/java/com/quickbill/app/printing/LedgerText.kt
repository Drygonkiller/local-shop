package com.quickbill.app.printing

import com.quickbill.app.data.CreditEntry
import com.quickbill.app.data.CreditLedger
import com.quickbill.app.data.formatNepalDualDateTime
import com.quickbill.app.data.formatNpr

internal object LedgerText {
    fun reminder(shopName: String, ledger: CreditLedger): String = buildString {
        val hasBalance = ledger.duePaisa.signum() > 0
        appendLine(shopName)
        appendLine(if (hasBalance) "Udharo reminder / उधारो भुक्तानीको सम्झना" else "Udharo statement / उधारो विवरण")
        appendLine("Customer / ग्राहक: ${ledger.customer.name}")
        append("Balance due / बाँकी रकम: ${formatNpr(ledger.duePaisa)}")

        if (hasBalance) {
            appendLine()
            append("Please pay the remaining balance. / कृपया बाँकी रकम तिर्नुहोस्।")
        }

        if (ledger.entries.isNotEmpty()) {
            appendLine()
            appendLine()
            append("Recent entries / पछिल्ला कारोबार")
            for (entry in ledger.entries.take(RECENT_ENTRY_LIMIT)) {
                val label = when (entry) {
                    is CreditEntry.BillEntry -> "Udharo bill / उधारो बिल #${entry.bill.billNumber}"
                    is CreditEntry.PaymentEntry -> "Payment / भुक्तानी"
                }
                appendLine()
                append("$label · ${formatNepalDualDateTime(entry.createdAtMillis)} · ${formatNpr(entry.amountPaisa)}")
            }
        }
    }

    private const val RECENT_ENTRY_LIMIT = 10
}
