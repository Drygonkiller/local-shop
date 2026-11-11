package com.quickbill.app.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.quickbill.app.data.SavedBill
import java.io.OutputStream

private const val PRINT_WIDTH_DOTS = 384
private const val DOTS_PER_LAYOUT_UNIT = 2
private const val STRIP_ROWS = 64

internal fun writeThermalReceipt(output: OutputStream, bill: SavedBill, isTest: Boolean) {
    val layout = ReceiptLayout(
        savedBill = bill,
        paperWidth = PRINT_WIDTH_DOTS / DOTS_PER_LAYOUT_UNIT,
        paperHeight = null,
        cancellationSignal = null,
        isTest = isTest,
    )
    val strip = Bitmap.createBitmap(PRINT_WIDTH_DOTS, STRIP_ROWS, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(strip)
    val pixels = IntArray(PRINT_WIDTH_DOTS * STRIP_ROWS)
    var rowsUsed = 0

    fun flushStrip() {
        if (rowsUsed == 0) return
        strip.getPixels(pixels, 0, PRINT_WIDTH_DOTS, 0, 0, PRINT_WIDTH_DOTS, rowsUsed)
        output.write(encodeEscPosRaster(PRINT_WIDTH_DOTS, rowsUsed, pixels))
        rowsUsed = 0
        strip.eraseColor(Color.WHITE)
    }

    try {
        strip.eraseColor(Color.WHITE)
        output.write(byteArrayOf(0x1b, 0x40)) // ESC @ initializes the printer.
        layout.drawRoll { height, draw ->
            val sectionRows = height * DOTS_PER_LAYOUT_UNIT
            var drawnRows = 0
            while (drawnRows < sectionRows) {
                val rows = minOf(sectionRows - drawnRows, STRIP_ROWS - rowsUsed)
                val save = canvas.save()
                canvas.clipRect(0, rowsUsed, PRINT_WIDTH_DOTS, rowsUsed + rows)
                canvas.translate(0f, (rowsUsed - drawnRows).toFloat())
                canvas.scale(DOTS_PER_LAYOUT_UNIT.toFloat(), DOTS_PER_LAYOUT_UNIT.toFloat())
                draw(canvas)
                canvas.restoreToCount(save)
                drawnRows += rows
                rowsUsed += rows
                if (rowsUsed == STRIP_ROWS) flushStrip()
            }
        }
        flushStrip()
        output.write(byteArrayOf(0x0a, 0x0a, 0x0a))
    } finally {
        strip.recycle()
    }
}

/** GS v 0, normal density, with each row packed most-significant bit first. */
internal fun encodeEscPosRaster(width: Int, height: Int, pixels: IntArray): ByteArray {
    require(width > 0 && width % 8 == 0 && width / 8 <= 0xffff)
    require(height in 1..0xffff && width.toLong() * height <= pixels.size)
    val bytesPerRow = width / 8
    val command = ByteArray(8 + bytesPerRow * height)
    command[0] = 0x1d
    command[1] = 0x76
    command[2] = 0x30
    command[4] = bytesPerRow.toByte()
    command[5] = (bytesPerRow shr 8).toByte()
    command[6] = height.toByte()
    command[7] = (height shr 8).toByte()

    for (index in 0 until width * height) {
        val color = pixels[index]
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        if (red * 299 + green * 587 + blue * 114 < 180_000) {
            val byteIndex = 8 + index / 8
            val mask = 0x80 ushr (index % 8)
            command[byteIndex] = (command[byteIndex].toInt() or mask).toByte()
        }
    }
    return command
}
