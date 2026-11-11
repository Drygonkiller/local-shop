package com.quickbill.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "favourites")
data class FavouriteItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unitPricePaisa: Long,
    val timesUsed: Long = 0,
    val photoUri: String? = null,
)

@Entity(tableName = "shop_details")
data class ShopDetails(
    val name: String,
    val pan: String,
    @PrimaryKey val id: Int = 1,
)

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val billNumber: Long = 0,
    val shopName: String,
    val shopPan: String,
    val createdAtMillis: Long,
    val totalPaisa: Long,
)

@Entity(
    tableName = "bill_lines",
    primaryKeys = ["billNumber", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["billNumber"],
            childColumns = ["billNumber"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BillLine(
    val billNumber: Long,
    val position: Int,
    val favouriteId: Long?,
    val name: String,
    val unitPricePaisa: Long,
    val quantity: Int,
) {
    val totalPaisa: Long get() = unitPricePaisa * quantity
}

data class SavedBill(val bill: Bill, val lines: List<BillLine>)

data class SaleLine(
    val favouriteId: Long?,
    val name: String,
    val unitPricePaisa: Long,
    val quantity: Int,
) {
    init {
        require(name.isNotBlank())
        require(unitPricePaisa in 0..MAX_PRICE_PAISA)
        require(quantity in 1..MAX_QUANTITY)
    }

    val totalPaisa: Long get() = unitPricePaisa * quantity
}

fun totalPaisa(lines: List<SaleLine>): Long = lines.fold(0L) { total, line ->
    require(total <= Long.MAX_VALUE - line.totalPaisa) { "Bill total is too large" }
    total + line.totalPaisa
}
