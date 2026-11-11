package com.quickbill.app

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quickbill.app.calculator.CalculatorDraft
import com.quickbill.app.calculator.CalculatorKey
import com.quickbill.app.data.FavouriteItem
import com.quickbill.app.data.MAX_QUANTITY
import com.quickbill.app.data.SaleLine
import com.quickbill.app.data.SavedBill
import com.quickbill.app.data.ShopDetails
import com.quickbill.app.data.totalPaisa
import com.quickbill.app.printing.ThermalPrinting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuickBillUiState(
    val favourites: List<FavouriteItem> = emptyList(),
    val lines: List<SaleLine> = emptyList(),
    val totalPaisa: Long = 0,
    val calculator: CalculatorDraft = CalculatorDraft(),
    val shop: ShopDetails? = null,
    val isLoading: Boolean = true,
    val isSavingBill: Boolean = false,
)

sealed interface BillEvent {
    data class Print(val bill: SavedBill) : BillEvent
    data class Message(@param:StringRes val message: Int) : BillEvent
}

class QuickBillViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as QuickBillApplication).repository
    internal val thermalPrinting = ThermalPrinting(application, viewModelScope)
    private val lines = MutableStateFlow<List<SaleLine>>(emptyList())
    private val calculator = MutableStateFlow(CalculatorDraft())
    private val isSavingBill = MutableStateFlow(false)
    private val eventChannel = Channel<BillEvent>(Channel.BUFFERED)
    private var reprintJob: Job? = null

    val events = eventChannel.receiveAsFlow()
    val history = repository.history
    val state = combine(
        repository.favourites, lines, calculator, repository.shop, isSavingBill,
    ) { favourites, currentLines, currentCalculator, shop, saving ->
        QuickBillUiState(
            favourites = favourites,
            lines = currentLines,
            totalPaisa = totalPaisa(currentLines),
            calculator = currentCalculator,
            shop = shop,
            isLoading = false,
            isSavingBill = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickBillUiState())

    fun addFavourite(name: String, unitPricePaisa: Long, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = runAction(R.string.favourite_save_failed) {
                repository.addFavourite(name, unitPricePaisa)
            } != null
            onComplete(saved)
        }
    }

    fun editFavourite(id: Long, name: String, unitPricePaisa: Long, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = runAction(R.string.favourite_save_failed) {
                repository.updateFavourite(id, name, unitPricePaisa)
                lines.value = lines.value.map { line ->
                    if (line.favouriteId == id) {
                        line.copy(name = name.trim(), unitPricePaisa = unitPricePaisa)
                    } else {
                        line
                    }
                }
            } != null
            onComplete(saved)
        }
    }

    fun deleteFavourite(id: Long, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = runAction(R.string.favourite_delete_failed) {
                repository.deleteFavourite(id)
            } != null
            onComplete(deleted)
        }
    }

    fun saveShop(name: String, pan: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = runAction(R.string.shop_save_failed) { repository.saveShop(name, pan) } != null
            onComplete(saved)
        }
    }

    fun setFavouritePhoto(id: Long, uri: String?) {
        viewModelScope.launch {
            runAction(R.string.photo_save_failed) { repository.setFavouritePhoto(id, uri) }
        }
    }

    fun addToBill(item: FavouriteItem) {
        if (isSavingBill.value) return
        if (lines.value.any { it.favouriteId == item.id }) {
            changeQuantity(item.id, 1)
        } else {
            lines.value += SaleLine(item.id, item.name, item.unitPricePaisa, 1)
        }
    }

    fun changeQuantity(id: Long, delta: Int) {
        if (isSavingBill.value || (delta != -1 && delta != 1)) return
        lines.value = lines.value.mapNotNull { line ->
            if (line.favouriteId != id) return@mapNotNull line
            val newQuantity = line.quantity + delta
            when {
                newQuantity <= 0 -> null
                newQuantity > MAX_QUANTITY -> line
                else -> line.copy(quantity = newQuantity)
            }
        }
    }

    fun pressCalculatorKey(key: CalculatorKey) {
        if (!isSavingBill.value) calculator.value = calculator.value.press(key)
    }

    fun printCurrentBill() = saveAndPrint(lines.value) {
        lines.value = emptyList()
    }

    fun printCalculatorBill() = saveAndPrint(calculator.value.toSaleLines()) {
        calculator.value = CalculatorDraft()
    }

    private fun saveAndPrint(checkoutLines: List<SaleLine>, clearDraft: () -> Unit) {
        if (checkoutLines.isEmpty() || isSavingBill.value || thermalPrinting.busy) return
        isSavingBill.value = true
        viewModelScope.launch {
            try {
                val bill = runAction(R.string.bill_save_failed) {
                    repository.saveBill(checkoutLines)
                } ?: return@launch
                clearDraft()
                eventChannel.send(BillEvent.Print(bill))
            } finally {
                isSavingBill.value = false
            }
        }
    }

    fun reprint(billNumber: Long) {
        if (reprintJob?.isActive == true || isSavingBill.value || thermalPrinting.busy) return
        reprintJob = viewModelScope.launch {
            val bill = runAction(R.string.bill_open_failed) {
                checkNotNull(repository.getBill(billNumber))
            } ?: return@launch
            eventChannel.send(BillEvent.Print(bill))
        }
    }

    private suspend fun <T : Any> runAction(
        @StringRes message: Int,
        action: suspend () -> T,
    ): T? = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.e("QuickBill", "Local operation failed", failure)
        eventChannel.send(BillEvent.Message(message))
        null
    }
}
