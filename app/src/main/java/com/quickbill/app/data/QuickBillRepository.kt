package com.quickbill.app.data

import androidx.room.withTransaction

class QuickBillRepository(private val database: QuickBillDatabase) {
    private val dao = database.dao()

    val favourites = dao.observeFavourites()
    val shop = dao.observeShop()
    val history = dao.observeBills()

    suspend fun addFavourite(name: String, unitPricePaisa: Long): Long {
        validateFavouriteFields(name, unitPricePaisa)
        return dao.insertFavourite(FavouriteItem(name = name.trim(), unitPricePaisa = unitPricePaisa))
    }

    suspend fun updateFavourite(id: Long, name: String, unitPricePaisa: Long) {
        validateFavouriteFields(name, unitPricePaisa)
        check(dao.updateFavourite(id, name.trim(), unitPricePaisa) == 1) { "Favourite item is missing" }
    }

    suspend fun deleteFavourite(id: Long) {
        check(dao.deleteFavourite(id) == 1) { "Favourite item is missing" }
    }

    suspend fun setFavouritePhoto(id: Long, uri: String?) {
        check(dao.setFavouritePhoto(id, uri) == 1) { "Favourite item is missing" }
    }

    suspend fun saveShop(name: String, pan: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Shop name is required" }
        val normalizedPan = requireNotNull(normalizePan(pan)) { "PAN must have nine digits" }
        dao.saveShop(ShopDetails(name = trimmedName, pan = normalizedPan))
    }

    suspend fun saveBill(lines: List<SaleLine>): SavedBill = database.withTransaction {
        require(lines.isNotEmpty()) { "Bill must have at least one line" }
        val shopDetails = checkNotNull(dao.getShop()) { "Shop details are required" }
        val header = Bill(
            shopName = shopDetails.name,
            shopPan = shopDetails.pan,
            createdAtMillis = System.currentTimeMillis(),
            totalPaisa = totalPaisa(lines),
        )
        val billNumber = dao.insertBill(header)
        val billLines = lines.mapIndexed { position, line ->
            BillLine(
                billNumber = billNumber,
                position = position,
                favouriteId = line.favouriteId,
                name = line.name,
                unitPricePaisa = line.unitPricePaisa,
                quantity = line.quantity,
            )
        }
        dao.insertBillLines(billLines)
        for (line in billLines) {
            val favouriteId = line.favouriteId ?: continue
            // A deleted favourite can still have a snapshotted line in the active bill.
            dao.recordUse(favouriteId, line.quantity)
        }
        SavedBill(header.copy(billNumber = billNumber), billLines)
    }

    suspend fun getBill(billNumber: Long): SavedBill? = database.withTransaction {
        val bill = dao.getBill(billNumber) ?: return@withTransaction null
        SavedBill(bill, dao.getBillLines(billNumber))
    }

    private fun validateFavouriteFields(name: String, unitPricePaisa: Long) {
        require(name.isNotBlank()) { "Item name is required" }
        require(unitPricePaisa in 0..MAX_PRICE_PAISA) { "Price is out of range" }
    }
}
