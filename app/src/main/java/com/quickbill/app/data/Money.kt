package com.quickbill.app.data

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

const val MAX_PRICE_PAISA = 9_999_999_999L
const val MAX_QUANTITY = 9_999

private val amountPattern = Regex("""(?:[0-9]+(?:\.[0-9]{0,2})?|\.[0-9]{1,2})""")
private val panPattern = Regex("[0-9]{9}")

fun parseNpr(input: String): Long? {
    val amount = input.trim().withAsciiDigits()
    if (!amountPattern.matches(amount)) return null

    val rupees = amount.substringBefore('.').ifEmpty { "0" }.toLongOrNull() ?: return null
    if (rupees > MAX_PRICE_PAISA / 100) return null

    val paisa = amount.substringAfter('.', "").padEnd(2, '0').toLong()
    return (rupees * 100 + paisa).takeIf { it <= MAX_PRICE_PAISA }
}

fun formatNpr(paisa: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = if (paisa % 100 == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    return "Rs. ${formatter.format(BigDecimal.valueOf(paisa, 2))}"
}

fun normalizePan(input: String): String? = input.trim().withAsciiDigits().takeIf(panPattern::matches)

private fun String.withAsciiDigits(): String = buildString(length) {
    for (character in this@withAsciiDigits) {
        append(if (character in '०'..'९') '0' + (character - '०') else character)
    }
}
