package com.quickbill.app.calculator

import com.quickbill.app.data.SaleLine
import com.quickbill.app.data.parseNpr

sealed interface CalculatorKey {
    data class Digit(val value: Char) : CalculatorKey {
        init {
            require(value in '0'..'9')
        }
    }
    data object Decimal : CalculatorKey
    data object Plus : CalculatorKey
    data object Backspace : CalculatorKey
    data object Clear : CalculatorKey
}

@ConsistentCopyVisibility
data class CalculatorDraft private constructor(
    private val committed: List<Amount>,
    private val input: String,
) {
    constructor() : this(emptyList(), "")

    val entry: String get() = if (input.startsWith('.')) "0$input" else input
    val committedAmounts: List<Long> get() = committed.map { it.paisa }
    private val entryAmount = parseNpr(input)
    private val committedTotal = committed.sumOf { it.paisa }
    val totalPaisa: Long get() = committedTotal + (entryAmount ?: 0)
    val canPrint: Boolean get() = committed.isNotEmpty() || entryAmount != null

    fun press(key: CalculatorKey): CalculatorDraft = when (key) {
        is CalculatorKey.Digit -> appendDigit(key.value)
        CalculatorKey.Decimal -> if ('.' in input) this else copy(input = "$input.")
        CalculatorKey.Plus -> if (entryAmount == null) this else CalculatorDraft(
            committed = committed + Amount(input, entryAmount),
            input = "",
        )
        CalculatorKey.Backspace -> backspace()
        CalculatorKey.Clear -> CalculatorDraft()
    }

    fun toSaleLines(): List<SaleLine> {
        val amounts = committedAmounts + listOfNotNull(entryAmount)
        val name = if (amounts.size == 1) "Total / जम्मा" else "Item / सामान"
        return amounts.map { amount ->
            SaleLine(favouriteId = null, name = name, unitPricePaisa = amount, quantity = 1)
        }
    }

    private fun appendDigit(digit: Char): CalculatorDraft {
        val next = if (input == "0") digit.toString() else "$input$digit"
        val amount = parseNpr(next) ?: return this
        if (amount > Long.MAX_VALUE - committedTotal) return this
        return copy(input = next)
    }

    private fun backspace(): CalculatorDraft {
        if (input.isNotEmpty()) return copy(input = input.dropLast(1))
        val previous = committed.lastOrNull() ?: return this
        return CalculatorDraft(committed.dropLast(1), previous.input)
    }

    private data class Amount(val input: String, val paisa: Long)
}
