package com.quickbill.app.ui

import android.print.PrintAttributes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.data.SavedBill
import com.quickbill.app.printing.BillText
import com.quickbill.app.printing.PrintDocument
import com.quickbill.app.printing.ReceiptLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val a6Width = PrintAttributes.MediaSize.ISO_A6.widthMils * 72 / 1000
private val a6Height = PrintAttributes.MediaSize.ISO_A6.heightMils * 72 / 1000

@Composable
internal fun ReceiptPreviewScreen(
    bill: SavedBill?,
    isBillMissing: Boolean,
    onSharePdf: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bill == null) {
        if (isBillMissing) {
            EmptyScreen(R.string.saved_bill_missing, R.string.saved_bill_missing_ne, BillIcon.Receipt, modifier)
        } else {
            ReportLoading(modifier)
        }
        return
    }

    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        key(bill) {
            ReceiptPreviewPages(bill, Modifier.weight(1f).fillMaxWidth().background(scheme.surfaceContainer))
        }
        HorizontalDivider(color = scheme.outlineVariant)
        Surface(color = scheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val padding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)
                Button(
                    onClick = onSharePdf,
                    modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = padding,
                ) {
                    BilingualLabel(R.string.share_pdf, R.string.share_pdf_ne, centered = true)
                }
                OutlinedButton(
                    onClick = onPrint,
                    modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = padding,
                ) {
                    BilingualLabel(R.string.print_bill, R.string.print_bill_ne, centered = true)
                }
            }
        }
    }
}

@Composable
private fun ReceiptPreviewPages(bill: SavedBill, modifier: Modifier) {
    val result by produceState<Result<ReceiptLayout>?>(initialValue = null, key1 = bill) {
        value = withContext(Dispatchers.Default) {
            runCatching { ReceiptLayout(PrintDocument.Receipt(bill), a6Width, a6Height, cancellationSignal = null) }
        }
    }
    val preview = result
    if (preview == null) {
        ReportLoading(modifier)
        return
    }
    val layout = preview.getOrNull()
    if (layout == null) {
        EmptyScreen(R.string.receipt_preview_unavailable, R.string.receipt_preview_unavailable_ne, BillIcon.Receipt, modifier)
        return
    }

    val description = remember(bill) { BillText.saved(bill) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(layout.pageCount) { index ->
            Surface(
                modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth(),
                color = Color.White,
                shadowElevation = 3.dp,
            ) {
                val paper = Modifier.fillMaxWidth().aspectRatio(a6Width.toFloat() / a6Height)
                Canvas(if (index == 0) paper.semantics { contentDescription = description } else paper) {
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val saved = native.save()
                        try {
                            val scale = size.width / a6Width
                            native.scale(scale, scale)
                            layout.drawPage(native, index)
                        } finally {
                            native.restoreToCount(saved)
                        }
                    }
                }
            }
        }
    }
}
