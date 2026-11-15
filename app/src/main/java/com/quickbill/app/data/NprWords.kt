package com.quickbill.app.data

private val unitsAndTeens = listOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
    "eighteen", "nineteen",
)
private val tens = listOf(
    "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
)
private val scales = listOf(
    100_000_000_000L to "kharab",
    1_000_000_000L to "arab",
    10_000_000L to "crore",
    100_000L to "lakh",
    1_000L to "thousand",
    100L to "hundred",
)

fun formatNprInWords(totalPaisa: Long): String {
    require(totalPaisa >= 0) { "Amount must be nonnegative" }
    val rupees = totalPaisa / 100
    val paisa = totalPaisa % 100
    return buildString {
        append(numberInWords(rupees))
        append(if (rupees == 1L) " rupee" else " rupees")
        if (paisa != 0L) {
            append(" and ")
            append(numberInWords(paisa))
            append(" paisa")
        }
        append(" only")
    }.replaceFirstChar { it.uppercaseChar() }
}

private fun numberInWords(value: Long): String {
    if (value < 20) return unitsAndTeens[value.toInt()]
    if (value < 100) {
        val tensWord = tens[(value / 10).toInt() - 2]
        val units = (value % 10).toInt()
        return if (units == 0) tensWord else "$tensWord-${unitsAndTeens[units]}"
    }

    val (divisor, scale) = scales.first { value >= it.first }
    val group = "${numberInWords(value / divisor)} $scale"
    val remainder = value % divisor
    return if (remainder == 0L) group else "$group ${numberInWords(remainder)}"
}
