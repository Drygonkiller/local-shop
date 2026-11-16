package com.quickbill.app.data

import androidx.room.Embedded
import androidx.room.Relation
import java.math.BigInteger

data class CreditSummary(val customer: Customer, val duePaisa: BigInteger, val lastActivityAtMillis: Long?)

data class CreditLedger(val customer: Customer, val duePaisa: BigInteger, val entries: List<CreditEntry>)

sealed interface CreditEntry {
    val createdAtMillis: Long
    val amountPaisa: Long

    data class BillEntry(val bill: Bill) : CreditEntry {
        override val createdAtMillis: Long get() = bill.createdAtMillis
        override val amountPaisa: Long get() = bill.totalPaisa
    }

    data class PaymentEntry(val payment: Payment) : CreditEntry {
        override val createdAtMillis: Long get() = payment.createdAtMillis
        override val amountPaisa: Long get() = payment.amountPaisa
    }
}

data class CustomerCreditRows(
    @Embedded val customer: Customer,
    @Relation(parentColumn = "id", entityColumn = "customerId") val bills: List<Bill>,
    @Relation(parentColumn = "id", entityColumn = "customerId") val payments: List<Payment>,
)

class InvalidCreditPaymentException : IllegalArgumentException("Payment must be positive and within the current balance")

internal fun validateCreditPayment(amountPaisa: Long, duePaisa: BigInteger) {
    if (amountPaisa <= 0 || amountPaisa.toBigInteger() > duePaisa) throw InvalidCreditPaymentException()
}

internal fun creditDue(bills: List<Bill>, payments: List<Payment>): BigInteger {
    val charged = bills.fold(BigInteger.ZERO) { total, bill -> total + bill.totalPaisa.toBigInteger() }
    return payments.fold(charged) { balance, payment -> balance - payment.amountPaisa.toBigInteger() }
}

internal fun summarizeCredit(rows: CustomerCreditRows): CreditSummary {
    val activityTimes = rows.bills.asSequence().map { it.createdAtMillis } +
        rows.payments.asSequence().map { it.createdAtMillis }
    return CreditSummary(rows.customer, creditDue(rows.bills, rows.payments), activityTimes.maxOrNull())
}

internal fun customerLedger(rows: CustomerCreditRows): CreditLedger {
    val entries = rows.bills.map { CreditEntry.BillEntry(it) } + rows.payments.map { CreditEntry.PaymentEntry(it) }
    val newestFirst = entries.sortedWith(
        compareByDescending<CreditEntry> { it.createdAtMillis }
            .thenByDescending { it is CreditEntry.PaymentEntry }
            .thenByDescending {
                when (it) {
                    is CreditEntry.BillEntry -> it.bill.billNumber
                    is CreditEntry.PaymentEntry -> it.payment.id
                }
            },
    )
    return CreditLedger(rows.customer, creditDue(rows.bills, rows.payments), newestFirst)
}

private val recentActivity = compareByDescending<CreditSummary> { it.lastActivityAtMillis }
    .thenByDescending { it.customer.id }
private val owedFirst = compareByDescending<CreditSummary> { it.duePaisa.signum() > 0 }

internal val khataCustomerOrder = owedFirst.thenByDescending { it.duePaisa }.then(recentActivity)
internal val creditPickerOrder = owedFirst.then(recentActivity)
