package com.quickbill.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class QuickBillRepository(private val database: QuickBillDatabase) {
    private val dao = database.dao()

    val favourites = dao.observeFavourites()
    val shop = dao.observeShop()
    val history = dao.observeBills()
    private val customerSummaries = dao.observeCustomerCredit().map { rows -> rows.map(::summarizeCredit) }
    val customers: Flow<List<CreditSummary>> = customerSummaries
        .map { it.sortedWith(khataCustomerOrder) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
    val creditCustomers: Flow<List<CreditSummary>> = customerSummaries
        .map { it.sortedWith(creditPickerOrder) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeCustomerLedger(id: Long): Flow<CreditLedger?> = dao.observeCustomerCredit(id)
        .map { rows -> rows?.let(::customerLedger) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    suspend fun addCustomer(name: String, phone: String?): Customer {
        val customer = customerDetails(name, phone)
        return customer.copy(id = dao.insertCustomer(customer))
    }

    suspend fun updateCustomer(id: Long, name: String, phone: String?) {
        val customer = customerDetails(name, phone)
        check(dao.updateCustomer(id, customer.name, customer.phone) == 1) { "Customer is missing" }
    }

    suspend fun addPayment(customerId: Long, amountPaisa: Long): Payment = database.withTransaction {
        val rows = checkNotNull(dao.getCustomerCredit(customerId)) { "Customer is missing" }
        validateCreditPayment(amountPaisa, creditDue(rows.bills, rows.payments))
        val payment = Payment(customerId = customerId, amountPaisa = amountPaisa, createdAtMillis = System.currentTimeMillis())
        payment.copy(id = dao.insertPayment(payment))
    }

    fun observeDailySales(day: NepalDay): Flow<DailySales> =
        dao.observeDailyBills(day.startMillis, day.endMillis)
            .map { summarizeDailySales(day, it) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    suspend fun getShop(): ShopDetails? = dao.getShop()

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

    suspend fun saveBill(lines: List<SaleLine>, customerId: Long? = null): SavedBill = database.withTransaction {
        require(lines.isNotEmpty()) { "Bill must have at least one line" }
        val shopDetails = checkNotNull(dao.getShop()) { "Shop details are required" }
        val customer = customerId?.let { checkNotNull(dao.getCustomer(it)) { "Customer is missing" } }
        val header = Bill(
            shopName = shopDetails.name,
            shopPan = shopDetails.pan,
            createdAtMillis = System.currentTimeMillis(),
            totalPaisa = totalPaisa(lines),
            customerId = customer?.id,
            customerName = customer?.name,
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

    suspend fun deleteBill(billNumber: Long) {
        check(dao.deleteBill(billNumber) == 1) { "Bill is missing" }
    }

    suspend fun clearHistory() = dao.clearHistory()

    private fun validateFavouriteFields(name: String, unitPricePaisa: Long) {
        require(name.isNotBlank()) { "Item name is required" }
        require(unitPricePaisa in 0..MAX_PRICE_PAISA) { "Price is out of range" }
    }

    private fun customerDetails(name: String, phone: String?): Customer {
        require(name.isNotBlank()) { "Customer name is required" }
        return Customer(name = name.trim(), phone = phone?.trim()?.takeIf { it.isNotEmpty() })
    }
}
