package com.quickbill.app.printing

import com.quickbill.app.data.SaleLine
import com.quickbill.app.data.SavedBill
import com.quickbill.app.data.ShopDetails
import com.quickbill.app.data.formatNepalBillDateTime
import com.quickbill.app.data.formatNpr
import com.quickbill.app.data.totalPaisa

internal object BillText {
    const val Total = "Total / जम्मा"
    fun pan(pan: String) = "PAN / प्यान $pan"
    fun number(billNumber: Long) = "Bill no. / बिल नं. $billNumber"
    fun date(value: String) = "Date / मिति $value"

    fun quantityAndUnitPrice(quantity: Int, unitPricePaisa: Long) =
        "$quantity × ${formatNpr(unitPricePaisa)}"

    fun saved(receipt: SavedBill): String = buildString {
        val bill = receipt.bill
        appendHeader(bill.shopName, bill.shopPan, number(bill.billNumber), bill.createdAtMillis)
        for (line in receipt.lines) {
            appendItem(line.name, line.quantity, line.unitPricePaisa, line.totalPaisa)
        }
        append("$Total  ${formatNpr(bill.totalPaisa)}")
    }

    fun draft(shop: ShopDetails, createdAtMillis: Long, lines: List<SaleLine>): String = buildString {
        appendLine("Draft / मस्यौदा")
        appendHeader(shop.name, shop.pan, "Bill no. / बिल नं. —", createdAtMillis)
        for (line in lines) {
            appendItem(line.name, line.quantity, line.unitPricePaisa, line.totalPaisa)
        }
        append("$Total  ${formatNpr(totalPaisa(lines))}")
    }

    private fun StringBuilder.appendHeader(shopName: String, shopPan: String, number: String, millis: Long) {
        appendLine(shopName)
        appendLine(pan(shopPan))
        appendLine(number)
        appendLine(date(formatNepalBillDateTime(millis)))
        appendLine()
    }

    private fun StringBuilder.appendItem(name: String, quantity: Int, unitPrice: Long, amount: Long) {
        appendLine(name)
        appendLine("${quantityAndUnitPrice(quantity, unitPrice)}  ${formatNpr(amount)}")
        appendLine()
    }
}
