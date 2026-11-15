package com.quickbill.app.data

import androidx.room.Embedded
import androidx.room.Relation
import java.math.BigInteger
import java.math.RoundingMode

data class DailySales(
    val day: NepalDay,
    val bills: List<Bill>,
    val totalPaisa: BigInteger,
    val averagePaisa: Long,
    val topItems: List<TopFavourite>,
) {
    val billCount: Int get() = bills.size
}

data class TopFavourite(val favouriteId: Long, val name: String, val totalPaisa: BigInteger)

data class DailySalesBill(
    @Embedded val bill: Bill,
    @Relation(parentColumn = "billNumber", entityColumn = "billNumber") val lines: List<BillLine>,
)

internal fun summarizeDailySales(day: NepalDay, sales: List<DailySalesBill>): DailySales {
    val ordered = sales.filter { it.bill.createdAtMillis in day }.sortedWith(
        compareByDescending<DailySalesBill> { it.bill.createdAtMillis }.thenByDescending { it.bill.billNumber },
    )
    val totalsByFavourite = mutableMapOf<Long, TopFavourite>()
    var total = BigInteger.ZERO

    for ((bill, lines) in ordered) {
        total += bill.totalPaisa.toBigInteger()
        for (line in lines.sortedBy { it.position }) {
            val id = line.favouriteId ?: continue
            val lineTotal = line.totalPaisa.toBigInteger()
            val previous = totalsByFavourite[id]
            // The first snapshot encountered supplies the latest sold name, even for deleted items.
            totalsByFavourite[id] = previous?.copy(totalPaisa = previous.totalPaisa + lineTotal)
                ?: TopFavourite(id, line.name, lineTotal)
        }
    }

    val average = if (ordered.isEmpty()) 0L else total.toBigDecimal()
        .divide(ordered.size.toBigDecimal(), 0, RoundingMode.HALF_UP)
        .longValueExact()
    val topItems = totalsByFavourite.values
        .sortedWith(compareByDescending<TopFavourite> { it.totalPaisa }.thenBy { it.favouriteId })
        .take(5)
    return DailySales(day, ordered.map { it.bill }, total, average, topItems)
}
