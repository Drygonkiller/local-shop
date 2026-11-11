package com.quickbill.app.printing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import com.quickbill.app.R
import com.quickbill.app.data.Bill
import com.quickbill.app.data.BillLine
import com.quickbill.app.data.SavedBill
import com.quickbill.app.data.ShopDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ThermalPrinterState(
    val selected: ThermalPrinter?,
    val hasPermission: Boolean,
    val paired: List<ThermalPrinter>? = null,
    val listing: Boolean = false,
    val printing: Boolean = false,
    val askingPermission: Boolean = false,
    val showRationale: Boolean = false,
    val notice: ThermalPrintNotice? = null,
)

internal data class ThermalPrintNotice(
    @param:StringRes val message: Int,
    val billSaved: Boolean = false,
    val isError: Boolean = true,
)

/** Coordinates printer selection and permission requests on the main thread. */
internal class ThermalPrinting(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("thermal_printer", Context.MODE_PRIVATE)
    private val transport = BluetoothThermalPrinter(appContext)
    private val mutableState = MutableStateFlow(
        ThermalPrinterState(
            selected = preferences.getString("address", null)?.let { address ->
                ThermalPrinter(address, preferences.getString("name", null) ?: address)
            },
            hasPermission = hasConnectPermission(),
        ),
    )
    val state = mutableState.asStateFlow()
    val busy: Boolean get() = state.value.printing || state.value.askingPermission
    private var awaitingPermission: ReceiptRequest? = null
    private var testAfterSelection: ReceiptRequest? = null
    private var activePrints = 0

    fun refreshPermission() {
        mutableState.update { it.copy(hasPermission = hasConnectPermission()) }
    }

    /** Returns false when the system print sheet should open. */
    fun printBill(bill: SavedBill): Boolean {
        val printer = state.value.selected ?: return false
        printOrRequestPermission(ReceiptRequest(bill, printer, isTest = false))
        return true
    }

    fun printTestReceipt(shop: ShopDetails?) {
        if (busy || state.value.listing) return
        val line = BillLine(
            billNumber = 0,
            position = 0,
            favouriteId = null,
            name = appContext.getString(R.string.thermal_test_item),
            unitPricePaisa = 12_000,
            quantity = 1,
        )
        val bill = Bill(
            shopName = shop?.name ?: appContext.getString(R.string.app_name),
            shopPan = shop?.pan ?: "—",
            createdAtMillis = System.currentTimeMillis(),
            totalPaisa = line.totalPaisa,
        )
        printOrRequestPermission(ReceiptRequest(SavedBill(bill, listOf(line)), state.value.selected, isTest = true))
    }

    fun choosePrinter() {
        refreshPermission()
        if (!busy && state.value.hasPermission) loadPairedPrinters(test = null)
    }

    fun selectPrinter(printer: ThermalPrinter) {
        if (state.value.paired?.contains(printer) != true) return
        val test = testAfterSelection
        preferences.edit().putString("address", printer.address).putString("name", printer.name).apply()
        mutableState.update { it.copy(selected = printer) }
        dismissPicker()
        if (test != null) printOrRequestPermission(test.copy(printer = printer))
    }

    fun useSystemPrinting() {
        preferences.edit().clear().apply()
        mutableState.update { it.copy(selected = null) }
    }

    fun dismissPicker() {
        testAfterSelection = null
        mutableState.update { it.copy(paired = null) }
    }

    fun dismissNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    fun launchPermissionRequest() {
        mutableState.update { it.copy(showRationale = false) }
    }

    fun permissionResult(granted: Boolean) {
        val pending = awaitingPermission
        awaitingPermission = null
        val hasPermission = hasConnectPermission()
        mutableState.update {
            it.copy(hasPermission = hasPermission, askingPermission = false, showRationale = false)
        }
        if (pending == null) return
        if (granted && hasPermission) start(pending)
        else showFailure(ThermalPrintFailure.PermissionRequired, billSaved = !pending.isTest)
    }

    private fun printOrRequestPermission(request: ReceiptRequest) {
        refreshPermission()
        if (state.value.hasPermission) {
            start(request)
        } else if (awaitingPermission == null) {
            awaitingPermission = request
            mutableState.update { it.copy(askingPermission = true, showRationale = true) }
        } else {
            showFailure(ThermalPrintFailure.PermissionRequired, billSaved = !request.isTest)
        }
    }

    private fun start(request: ReceiptRequest) {
        val printer = request.printer
        if (printer == null) {
            loadPairedPrinters(test = request)
            return
        }

        activePrints++
        mutableState.update { it.copy(printing = true) }
        scope.launch {
            try {
                val failure = transport.print(printer) { output ->
                    writeThermalReceipt(output, request.bill, request.isTest)
                }
                if (failure != null) {
                    refreshPermission()
                    showFailure(failure, billSaved = !request.isTest)
                } else if (request.isTest) {
                    mutableState.update {
                        it.copy(notice = ThermalPrintNotice(R.string.thermal_test_sent, isError = false))
                    }
                }
            } finally {
                activePrints--
                mutableState.update { it.copy(printing = activePrints != 0) }
            }
        }
    }

    private fun loadPairedPrinters(test: ReceiptRequest?) {
        if (state.value.listing) return
        mutableState.update { it.copy(listing = true) }
        scope.launch {
            try {
                when (val result = transport.pairedPrinters()) {
                    is PairedPrintersResult.Available -> {
                        testAfterSelection = test
                        mutableState.update { it.copy(paired = result.printers) }
                    }
                    is PairedPrintersResult.Unavailable -> {
                        refreshPermission()
                        showFailure(result.failure, billSaved = false)
                    }
                }
            } finally {
                mutableState.update { it.copy(listing = false) }
            }
        }
    }

    private fun showFailure(failure: ThermalPrintFailure, billSaved: Boolean) {
        val message = when (failure) {
            ThermalPrintFailure.PermissionRequired -> R.string.thermal_permission_denied
            ThermalPrintFailure.BluetoothUnavailable -> R.string.thermal_bluetooth_unavailable
            ThermalPrintFailure.BluetoothOff -> R.string.thermal_bluetooth_off
            ThermalPrintFailure.NotPaired -> R.string.thermal_not_paired
            ThermalPrintFailure.ConnectionFailed, ThermalPrintFailure.TimedOut -> R.string.thermal_connection_failed
        }
        mutableState.update { it.copy(notice = ThermalPrintNotice(message, billSaved)) }
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private data class ReceiptRequest(val bill: SavedBill, val printer: ThermalPrinter?, val isTest: Boolean)
}
