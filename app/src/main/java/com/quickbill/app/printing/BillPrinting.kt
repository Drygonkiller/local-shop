package com.quickbill.app.printing

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.util.Log
import com.quickbill.app.data.formatNepalReportDate
import java.util.concurrent.Executors

/** Opens the device's print dialog for a receipt or daily report. */
fun printDocument(activity: Activity, document: PrintDocument): Boolean {
    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PRINTING)) return false
    val manager = activity.getSystemService(PrintManager::class.java) ?: return false
    val jobName = when (document) {
        is PrintDocument.Receipt -> if (document.isTest) "Quick Bill Test" else "Quick Bill ${document.bill.bill.billNumber}"
        is PrintDocument.Report -> "Quick Bill Daily Sales ${formatNepalReportDate(document.report.day)}"
    }
    val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
        .build()
    val adapter = DocumentPrintAdapter(activity.applicationContext, document, jobName)

    return try {
        // PrintManager can return null when the system cannot launch its print dialog.
        @Suppress("SENSELESS_COMPARISON")
        val launched = manager.print(jobName, adapter, attributes) != null
        launched
    } catch (error: IllegalStateException) {
        Log.w(PRINT_TAG, "Could not open Android printing", error)
        false
    } catch (error: SecurityException) {
        Log.w(PRINT_TAG, "Android printing is unavailable", error)
        false
    }
}

private const val PRINT_TAG = "QuickBillPrint"
private val printExecutor = Executors.newSingleThreadExecutor()

private class DocumentPrintAdapter(
    private val context: Context,
    private val content: PrintDocument,
    private val jobName: String,
) : PrintDocumentAdapter() {
    // Accessed only by the print executor, including when the system changes paper size.
    private var currentLayout: DocumentLayout? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        printExecutor.execute {
            try {
                cancellationSignal?.throwIfCanceled()
                val previous = currentLayout
                val layout = if (previous?.attributes == newAttributes) {
                    previous
                } else {
                    val document = PrintedPdfDocument(context, newAttributes)
                    try {
                        val bounds = document.pageContentRect
                        val receipt = ReceiptLayout(content, bounds.width(), bounds.height(), cancellationSignal)
                        DocumentLayout(newAttributes, receipt)
                    } finally {
                        document.close()
                    }
                }
                cancellationSignal?.throwIfCanceled()
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(layout.receipt.pageCount)
                    .build()
                currentLayout = layout
                callback.onLayoutFinished(info, layout !== previous)
            } catch (_: OperationCanceledException) {
                callback.onLayoutCancelled()
            } catch (error: Exception) {
                Log.w(PRINT_TAG, "Could not lay out document for printing", error)
                callback.onLayoutFailed(when (content) {
                    is PrintDocument.Receipt -> "Could not fit the bill on this paper. / यो कागजमा बिल अटेन।"
                    is PrintDocument.Report -> "Could not fit the report on this paper. / यो कागजमा रिपोर्ट अटेन।"
                })
            }
        }
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        printExecutor.execute {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                    cancellationSignal?.throwIfCanceled()
                    val layout = checkNotNull(currentLayout)
                    val selectedPages = (0 until layout.receipt.pageCount).filter { index ->
                        pages.any { index in it.start..it.end }
                    }
                    check(selectedPages.isNotEmpty())

                    val document = PrintedPdfDocument(context, layout.attributes)
                    try {
                        for (index in selectedPages) {
                            cancellationSignal?.throwIfCanceled()
                            val page = document.startPage(index)
                            try {
                                layout.receipt.drawPage(page.canvas, index)
                            } finally {
                                document.finishPage(page)
                            }
                        }
                        cancellationSignal?.throwIfCanceled()
                        document.writeTo(output)
                    } finally {
                        document.close()
                    }
                }
                cancellationSignal?.throwIfCanceled()
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (_: OperationCanceledException) {
                callback.onWriteCancelled()
            } catch (error: Exception) {
                Log.w(PRINT_TAG, "Could not write document PDF", error)
                callback.onWriteFailed(when (content) {
                    is PrintDocument.Receipt -> "Could not prepare the bill. / बिल तयार गर्न सकिएन।"
                    is PrintDocument.Report -> "Could not prepare the report. / रिपोर्ट तयार गर्न सकिएन।"
                })
            }
        }
    }

    private data class DocumentLayout(val attributes: PrintAttributes, val receipt: ReceiptLayout)
}
