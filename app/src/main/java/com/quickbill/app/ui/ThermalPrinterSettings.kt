package com.quickbill.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.data.ShopDetails
import com.quickbill.app.printing.ThermalPrinterState
import com.quickbill.app.printing.ThermalPrinting

@Composable
internal fun ThermalPrinterSettings(
    printing: ThermalPrinting,
    state: ThermalPrinterState,
    shop: ShopDetails?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val paired = state.paired
    val canChange = !state.listing && !state.printing && !state.askingPermission
    val button = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    val openBluetoothSettings = {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            // Some Android devices do not expose a Bluetooth settings screen.
        }
    }
    if (paired != null) {
        AlertDialog(
            onDismissRequest = printing::dismissPicker,
            title = { Text(stringResource(R.string.thermal_choose)) },
            text = {
                if (paired.isEmpty()) {
                    Text(stringResource(R.string.thermal_pair_first))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(paired, key = { it.address }) { printer ->
                            OutlinedButton(
                                onClick = { printing.selectPrinter(printer) },
                                modifier = button,
                            ) {
                                Column {
                                    Text(printer.name, style = MaterialTheme.typography.titleSmall)
                                    if (printer.name != printer.address) {
                                        Text(printer.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (paired.isEmpty()) {
                    TextButton(onClick = {
                        printing.dismissPicker()
                        openBluetoothSettings()
                    }) { PrinterLabel(R.string.thermal_bluetooth_settings) }
                }
            },
            dismissButton = { CancelButton(printing::dismissPicker) },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!state.listing && !state.askingPermission) onDismiss() },
        title = { Text(stringResource(R.string.thermal_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val selected = state.selected
                Text(selected?.name ?: stringResource(R.string.thermal_system_printing))
                if (!state.hasPermission) {
                    Text(
                        text = stringResource(R.string.thermal_permission_setup),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { printing.printTestReceipt(shop) },
                    enabled = canChange,
                    modifier = button,
                ) {
                    PrinterLabel(if (state.printing) R.string.thermal_sending else R.string.thermal_print_test)
                }
                if (state.hasPermission) {
                    OutlinedButton(onClick = printing::choosePrinter, enabled = canChange, modifier = button) {
                        PrinterLabel(R.string.thermal_choose)
                    }
                }
                if (selected != null) {
                    OutlinedButton(onClick = printing::useSystemPrinting, enabled = canChange, modifier = button) {
                        PrinterLabel(R.string.thermal_use_system)
                    }
                }
                TextButton(onClick = openBluetoothSettings, modifier = button) {
                    PrinterLabel(R.string.thermal_bluetooth_settings)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.listing && !state.askingPermission) {
                PrinterLabel(R.string.thermal_done)
            }
        },
    )
}

@Composable
internal fun ThermalPrinterPrompts(printing: ThermalPrinting, state: ThermalPrinterState) {
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        printing.permissionResult(it)
    }
    if (state.showRationale) {
        AlertDialog(
            onDismissRequest = { printing.permissionResult(false) },
            title = { Text(stringResource(R.string.thermal_permission_title)) },
            text = { Text(stringResource(R.string.thermal_permission_reason)) },
            confirmButton = {
                Button(onClick = {
                    printing.launchPermissionRequest()
                    try {
                        requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } catch (_: IllegalStateException) {
                        printing.permissionResult(false)
                    } catch (_: SecurityException) {
                        printing.permissionResult(false)
                    }
                }) { PrinterLabel(R.string.thermal_allow) }
            },
            dismissButton = { CancelButton(onClick = { printing.permissionResult(false) }) },
        )
    } else {
        state.notice?.let { notice ->
            AlertDialog(
                onDismissRequest = printing::dismissNotice,
                title = {
                    Text(stringResource(if (notice.isError) R.string.thermal_failed else R.string.thermal_title))
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(notice.message))
                        if (notice.billSaved) Text(stringResource(R.string.thermal_saved_in_history))
                    }
                },
                confirmButton = {
                    TextButton(onClick = printing::dismissNotice) { PrinterLabel(R.string.thermal_done) }
                },
            )
        }
    }
}

@Composable
private fun PrinterLabel(stringId: Int) {
    Text(stringResource(stringId), textAlign = TextAlign.Center)
}
