package com.quickbill.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quickbill.app.printing.PrintDocument
import com.quickbill.app.printing.printDocument
import com.quickbill.app.ui.QuickBillApp
import com.quickbill.app.ui.QuickBillTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<QuickBillViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is BillEvent.Print -> {
                            val handled = viewModel.thermalPrinting.printDocument(event.document)
                            if (!handled && !printDocument(this@MainActivity, event.document)) {
                                showMessage(
                                    if (event.document is PrintDocument.Report) R.string.report_print_unavailable
                                    else R.string.print_unavailable,
                                )
                            }
                        }
                        is BillEvent.Share -> shareText(event.text, event.title)
                        is BillEvent.Message -> showMessage(event.message)
                    }
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
                    onPrintBill = viewModel::printCurrentBill,
                    onCalculatorKey = viewModel::pressCalculatorKey,
                    onPrintCalculatorBill = viewModel::printCalculatorBill,
                    onSaveShop = viewModel::saveShop,
                    onReprint = viewModel::reprint,
                    loadSavedBill = viewModel::loadSavedBill,
                    onShareSavedBill = viewModel::shareSavedBill,
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
        viewModel.thermalPrinting.refreshPermission()
    }

    private fun showMessage(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun shareText(text: String, title: Int) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        try {
            startActivity(Intent.createChooser(send, getString(title)))
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.share_unavailable)
        }
    }
}
