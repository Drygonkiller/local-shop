package com.quickbill.app.printing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ThermalPrinter(val address: String, val name: String)

sealed interface PairedPrintersResult {
    data class Available(val printers: List<ThermalPrinter>) : PairedPrintersResult
    data class Unavailable(val failure: ThermalPrintFailure) : PairedPrintersResult
}

enum class ThermalPrintFailure {
    PermissionRequired,
    BluetoothUnavailable,
    BluetoothOff,
    NotPaired,
    ConnectionFailed,
    TimedOut,
}

class BluetoothThermalPrinter(context: Context) {
    private val appContext = context.applicationContext
    private val printMutex = Mutex()

    @SuppressLint("MissingPermission")
    suspend fun pairedPrinters(): PairedPrintersResult = withContext(Dispatchers.IO) {
        if (!hasConnectPermission()) {
            return@withContext PairedPrintersResult.Unavailable(ThermalPrintFailure.PermissionRequired)
        }
        try {
            val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return@withContext PairedPrintersResult.Unavailable(ThermalPrintFailure.BluetoothUnavailable)
            if (!adapter.isEnabled) {
                return@withContext PairedPrintersResult.Unavailable(ThermalPrintFailure.BluetoothOff)
            }
            val printers = adapter.bondedDevices
                .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
                .map { device ->
                    ThermalPrinter(
                        address = device.address,
                        name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                    )
                }
                .sortedWith(compareBy<ThermalPrinter> { it.name.lowercase(Locale.ROOT) }.thenBy { it.address })
            PairedPrintersResult.Available(printers)
        } catch (denied: SecurityException) {
            PairedPrintersResult.Unavailable(ThermalPrintFailure.PermissionRequired)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: RuntimeException) {
            Log.w(PRINT_TAG, "Could not read paired Bluetooth devices", error)
            PairedPrintersResult.Unavailable(ThermalPrintFailure.ConnectionFailed)
        }
    }

    /** Returns null after the receipt bytes are sent. The writer runs on an IO thread. */
    @SuppressLint("MissingPermission")
    suspend fun print(
        printer: ThermalPrinter,
        writeReceipt: (OutputStream) -> Unit,
    ): ThermalPrintFailure? = printMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!hasConnectPermission()) return@withContext ThermalPrintFailure.PermissionRequired
            try {
                val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
                    ?: return@withContext ThermalPrintFailure.BluetoothUnavailable
                if (!adapter.isEnabled) return@withContext ThermalPrintFailure.BluetoothOff
                val device = adapter.bondedDevices.firstOrNull { it.address == printer.address }
                    ?: return@withContext ThermalPrintFailure.NotPaired
                val socket = device.createRfcommSocketToServiceRecord(SERIAL_PORT_UUID)
                try {
                    if (!socket.runWithinTimeout(CONNECT_TIMEOUT_MILLIS) { socket.connect() }) {
                        return@withContext ThermalPrintFailure.TimedOut
                    }
                    val sent = socket.runWithinTimeout(WRITE_TIMEOUT_MILLIS) {
                        val output = socket.outputStream
                        writeReceipt(output)
                        output.flush()
                    }
                    if (sent) null else ThermalPrintFailure.TimedOut
                } finally {
                    socket.closeQuietly()
                }
            } catch (denied: SecurityException) {
                ThermalPrintFailure.PermissionRequired
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(PRINT_TAG, "Could not send Bluetooth receipt", error)
                if (hasConnectPermission()) ThermalPrintFailure.ConnectionFailed
                else ThermalPrintFailure.PermissionRequired
            }
        }
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

private const val PRINT_TAG = "QuickBillThermal"
private const val CONNECT_TIMEOUT_MILLIS = 8_000L
private const val WRITE_TIMEOUT_MILLIS = 30_000L
private val SERIAL_PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

private suspend fun BluetoothSocket.runWithinTimeout(
    timeoutMillis: Long,
    action: () -> Unit,
): Boolean = supervisorScope {
    val operation = async { action() }
    try {
        withTimeoutOrNull(timeoutMillis) { operation.await() } != null
    } finally {
        // Closing from this coroutine interrupts a blocking connect or write on the IO thread.
        if (!operation.isCompleted) {
            operation.cancel()
            closeQuietly()
        }
    }
}

private fun BluetoothSocket.closeQuietly() {
    try {
        close()
    } catch (error: IOException) {
        Log.w(PRINT_TAG, "Could not close Bluetooth printer socket", error)
    }
}
