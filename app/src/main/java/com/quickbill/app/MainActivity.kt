package com.quickbill.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quickbill.app.data.SavedBill
import com.quickbill.app.printing.BillText
import com.quickbill.app.printing.PrintDocument
import com.quickbill.app.printing.printDocument
import com.quickbill.app.ui.QuickBillApp
import com.quickbill.app.ui.QuickBillTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<QuickBillViewModel>()
    private var androidPrintOpening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.events.collect(::handleBillEvent) }
                while (true) {
                    val share = viewModel.checkoutShares.first { it.isNotEmpty() }.first()
                    if (viewModel.claimCheckoutShare(share) && shareCheckout(share)) break
                }
            }
        }

        setContent {
            QuickBillTheme {
                QuickBillApp(
                    state = viewModel.state.collectAsStateWithLifecycle().value,
                    history = viewModel.history,
                    dailySales = viewModel.dailySales,
                    customers = viewModel.customers,
                    creditCustomers = viewModel.creditCustomers,
                    observeCustomerLedger = viewModel::observeCustomerLedger,
                    thermalPrinting = viewModel.thermalPrinting,
                    onAddFavorite = viewModel::addFavourite,
                    onEditFavourite = viewModel::editFavourite,
                    onDeleteFavourite = viewModel::deleteFavourite,
                    onAddToBill = viewModel::addToBill,
                    onChangeQuantity = viewModel::changeQuantity,
                    onClearBill = viewModel::clearCurrentBill,
                    onPrintBill = viewModel::printCurrentBill,
                    onSaveAndShareBillPdf = viewModel::saveAndShareBillPdf,
                    onCalculatorKey = viewModel::pressCalculatorKey,
                    onPrintCalculatorBill = viewModel::printCalculatorBill,
                    onSaveShop = viewModel::saveShop,
                    onReprint = viewModel::reprint,
                    onPrintReceiptPreview = { bill, fromCheckout ->
                        printUsingAndroid(PrintDocument.Receipt(bill), bill.takeIf { fromCheckout })
                    },
                    loadSavedBill = viewModel::loadSavedBill,
                    billNavigation = viewModel.billNavigation.collectAsStateWithLifecycle().value,
                    onBillNavigationHandled = viewModel::consumeBillNavigation,
                    isDeletingBills = viewModel.isDeletingBills.collectAsStateWithLifecycle().value,
                    onDeleteBill = viewModel::deleteBill,
                    onClearHistory = viewModel::clearHistory,
                    onShareSavedBill = viewModel::shareSavedBill,
                    onShareSavedBillPdf = viewModel::shareSavedBillPdf,
                    onShareDraftBill = viewModel::shareDraftBill,
                    onPrintReport = viewModel::printReport,
                    onSetPhoto = viewModel::setFavouritePhoto,
                    onAddCustomer = viewModel::addCustomer,
                    onEditCustomer = viewModel::editCustomer,
                    onAddPayment = viewModel::addPayment,
                    onShareLedger = viewModel::shareLedger,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        androidPrintOpening = false
        viewModel.thermalPrinting.refreshPermission()
        viewModel.checkSystemCheckout()
    }

    private fun handleBillEvent(event: BillEvent) {
        when (event) {
            is BillEvent.Print -> {
                val receipt = event.document as? PrintDocument.Receipt
                val checkout = receipt?.bill?.takeIf { event.fromCheckout }
                val model = viewModel
                val onSent: (() -> Unit)? = checkout?.let { bill -> { model.checkoutPrintSent(bill) } }
                if (!model.thermalPrinting.printDocument(event.document, onSent)) {
                    if (receipt != null && !receipt.isTest) {
                        model.openReceiptPreview(receipt.bill.bill.billNumber, event.fromCheckout)
                    } else printUsingAndroid(event.document)
                }
            }
            is BillEvent.Share -> {
                if (!openShareSheet(textShareIntent(event.text), event.title)) showMessage(R.string.share_unavailable)
            }
            is BillEvent.SharePdf -> {
                if (!openShareSheet(pdfShareIntent(event.uri), R.string.share_pdf_title)) showMessage(R.string.share_unavailable)
            }
            is BillEvent.Message -> showMessage(event.message)
        }
    }

    private fun printUsingAndroid(document: PrintDocument, checkout: SavedBill? = null) {
        if (androidPrintOpening) return
        val job = printDocument(this, document)
        if (job == null) {
            showMessage(if (document is PrintDocument.Report) R.string.report_print_unavailable else R.string.print_unavailable)
            return
        }
        androidPrintOpening = true
        if (checkout != null) viewModel.trackSystemCheckout(checkout, job)
    }

    private fun showMessage(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun shareCheckout(share: CheckoutShare): Boolean {
        val uri = share.pdfUri
        if (uri != null && openShareSheet(pdfShareIntent(uri), R.string.share_pdf_title)) return true

        val text = try {
            BillText.saved(share.bill)
        } catch (failure: Exception) {
            Log.w("QuickBill", "Could not format the saved bill for sharing", failure)
            return false
        }
        return openShareSheet(textShareIntent(text), R.string.share_bill_title)
    }

    private fun textShareIntent(text: String): Intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)

    private fun pdfShareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .apply {
            clipData = ClipData.newRawUri(getString(R.string.share_pdf_title), uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun openShareSheet(send: Intent, @StringRes title: Int): Boolean = try {
        startActivity(Intent.createChooser(send, getString(title)))
        true
    } catch (failure: RuntimeException) {
        Log.w("QuickBill", "Could not open the share sheet", failure)
        false
    }
}
