package com.quickbill.app.printing

import com.quickbill.app.data.DailySales
import com.quickbill.app.data.SavedBill
import com.quickbill.app.data.ShopDetails

sealed interface PrintDocument {
    data class Receipt(val bill: SavedBill, val isTest: Boolean = false) : PrintDocument
    data class Report(val report: DailySales, val shop: ShopDetails?) : PrintDocument
}
