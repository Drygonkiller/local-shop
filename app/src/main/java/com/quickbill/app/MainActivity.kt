package com.quickbill.app

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
import com.quickbill.app.printing.printBill
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
                            val handled = viewModel.thermalPrinting.printBill(event.bill)
                            if (!handled && !printBill(this@MainActivity, event.bill)) {
                                showMessage(R.string.print_unavailable)
                            }
                        }
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
                    onSetPhoto = viewModel::setFavouritePhoto,
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
}
