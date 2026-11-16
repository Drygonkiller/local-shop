package com.quickbill.app.printing

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.system.Os
import androidx.core.content.FileProvider
import com.quickbill.app.data.SavedBill
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val PDF_CACHE_DIRECTORY = "bill_pdfs"
private const val RECEIPT_WIDTH_POINTS = 164 // 58 mm rounded to whole PDF points.
private const val PREFERRED_PAGE_HEIGHT = 920 // In thermal layout units.
private const val MAX_PDF_PAGE_HEIGHT = 14_400

internal fun billPdfFileName(billNumber: Long): String = "QuickBill-$billNumber.pdf"

internal suspend fun createBillPdf(context: Context, bill: SavedBill): Uri = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, PDF_CACHE_DIRECTORY)
    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
        throw IOException("Could not create the receipt PDF directory")
    }

    val file = File(directory, billPdfFileName(bill.bill.billNumber))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val temporary = File.createTempFile("receipt-", ".tmp", directory)
    try {
        temporary.outputStream().buffered().use { writeBillPdf(it, bill) }
        currentCoroutineContext().ensureActive()
        // Replace the cache entry atomically so a share target can only open complete PDFs.
        Os.rename(temporary.absolutePath, file.absolutePath)
        uri
    } finally {
        temporary.delete()
    }
}

private suspend fun writeBillPdf(output: OutputStream, bill: SavedBill) {
    val document = PrintDocument.Receipt(bill)
    val roll = ReceiptLayout(document, THERMAL_LAYOUT_WIDTH, paperHeight = null, cancellationSignal = null)
    val rollHeight = roll.pageHeight(0)
    val pageHeight = minOf(rollHeight, maxOf(PREFERRED_PAGE_HEIGHT.toLong(), roll.minimumPageHeight))
    val scale = RECEIPT_WIDTH_POINTS.toFloat() / THERMAL_LAYOUT_WIDTH
    require(ceil(pageHeight * scale.toDouble()).toInt() in 1..MAX_PDF_PAGE_HEIGHT) {
        "Receipt height is outside the PDF page limits"
    }
    val layout = if (pageHeight == rollHeight) roll else {
        ReceiptLayout(document, THERMAL_LAYOUT_WIDTH, pageHeight.toInt(), cancellationSignal = null)
    }

    val pdf = PdfDocument()
    try {
        for (index in 0 until layout.pageCount) {
            currentCoroutineContext().ensureActive()
            val heightPoints = ceil(layout.pageHeight(index) * scale.toDouble()).toInt()
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(RECEIPT_WIDTH_POINTS, heightPoints, index + 1).create())
            try {
                page.canvas.scale(scale, scale)
                layout.drawPage(page.canvas, index)
            } finally {
                pdf.finishPage(page)
            }
        }
        currentCoroutineContext().ensureActive()
        pdf.writeTo(output)
    } finally {
        pdf.close()
    }
}
