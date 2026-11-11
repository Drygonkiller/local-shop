package com.quickbill.app.printing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.CancellationSignal
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.quickbill.app.data.SavedBill
import com.quickbill.app.data.formatNpr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

private const val PAGE_PADDING = 6
private const val RECEIPT_MAX_WIDTH = 260
private const val COLUMN_GAP = 8
private const val RULE_HEIGHT = 6

/** Lays out one receipt in printable points so preview and print have the same pagination. */
internal class ReceiptLayout(
    savedBill: SavedBill,
    paperWidth: Int,
    paperHeight: Int,
    cancellationSignal: CancellationSignal?,
) {
    private val width = (paperWidth - PAGE_PADDING * 2).coerceAtMost(RECEIPT_MAX_WIDTH)
    private val leftInset = (paperWidth - width) / 2f
    private val titleStyle = textPaint(14f, Typeface.DEFAULT_BOLD)
    private val detailStyle = textPaint(9f)
    private val nameStyle = textPaint(10.5f)
    private val amountStyle = textPaint(10f, Typeface.DEFAULT_BOLD)
    private val totalStyle = textPaint(12f, Typeface.DEFAULT_BOLD)
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 0.5f
    }

    private val header: List<StaticLayout>
    private val pages: List<List<ReceiptRow>>
    val pageCount: Int get() = pages.size

    init {
        require(width >= 72) { "Printable width is too small" }
        val bill = savedBill.bill
        val date = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kathmandu")
        }.format(Date(bill.createdAtMillis))

        header = listOf(
            text(bill.shopName, titleStyle, alignment = Layout.Alignment.ALIGN_CENTER),
            text("PAN / प्यान ${bill.shopPan}", detailStyle),
            text("Bill no. / बिल नं. ${bill.billNumber}", detailStyle),
            text("Date / मिति $date", detailStyle),
        )

        val bodyHeight = paperHeight - PAGE_PADDING * 2 - header.sumOf { it.height } - RULE_HEIGHT
        val total = ReceiptRow(
            body = columns("Total / जम्मा", totalStyle, formatNpr(bill.totalPaisa), totalStyle),
            divider = true,
        )
        val receiptPages = mutableListOf<List<ReceiptRow>>()
        var page = mutableListOf<ReceiptRow>()
        var available = bodyHeight

        fun addRow(row: ReceiptRow, spaceAfter: Int = 0) {
            require(row.height <= bodyHeight) { "Printable height is too small" }
            if (row.height + spaceAfter > available) {
                receiptPages.add(page)
                page = mutableListOf()
                available = bodyHeight
            }
            page.add(row)
            available -= row.height
        }

        for ((index, line) in savedBill.lines.withIndex()) {
            cancellationSignal?.throwIfCanceled()
            val row = ReceiptRow(
                title = text(line.name, nameStyle),
                body = columns(
                    "${line.quantity} × ${formatNpr(line.unitPricePaisa)}", detailStyle,
                    formatNpr(line.totalPaisa), amountStyle,
                ),
            )
            // Keep the total with the last item when they can fit together on a page.
            val spaceAfter = if (index == savedBill.lines.lastIndex && row.height + total.height <= bodyHeight) {
                total.height
            } else {
                0
            }
            addRow(row, spaceAfter)
        }
        addRow(total)
        receiptPages.add(page)
        pages = receiptPages
    }

    fun drawPage(canvas: Canvas, index: Int) {
        var y = PAGE_PADDING.toFloat()
        for (line in header) {
            line.drawAt(canvas, leftInset, y)
            y += line.height
        }
        drawRule(canvas, y)
        y += RULE_HEIGHT

        for (row in pages[index]) {
            if (row.divider) {
                drawRule(canvas, y)
                y += RULE_HEIGHT
            }
            row.title?.let { title ->
                title.drawAt(canvas, leftInset, y)
                y += title.height
            }
            val body = row.body
            body.left.drawAt(canvas, leftInset, y)
            body.right.drawAt(canvas, leftInset + body.rightX, y + body.rightY)
            y += body.height + ROW_SPACING
        }
    }

    private fun columns(left: String, leftStyle: TextPaint, right: String, rightStyle: TextPaint): Columns {
        val rightWidth = ceil(Layout.getDesiredWidth(right, rightStyle)).toInt() + 1
        val leftWidth = width - rightWidth - COLUMN_GAP
        if (leftWidth < ceil(Layout.getDesiredWidth(left, leftStyle)).toInt() + 1) {
            val upper = text(left, leftStyle)
            val lower = text(right, rightStyle, alignment = Layout.Alignment.ALIGN_OPPOSITE)
            return Columns(upper, lower, rightX = 0, rightY = upper.height)
        }

        return Columns(
            text(left, leftStyle, leftWidth),
            text(right, rightStyle, rightWidth, Layout.Alignment.ALIGN_OPPOSITE),
            rightX = width - rightWidth,
            rightY = 0,
        )
    }

    private fun text(
        value: String,
        paint: TextPaint,
        lineWidth: Int = width,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): StaticLayout = StaticLayout.Builder.obtain(value, 0, value.length, paint, lineWidth)
        .setAlignment(alignment)
        .build()

    private fun drawRule(canvas: Canvas, top: Float) {
        val y = top + RULE_HEIGHT / 2f
        canvas.drawLine(leftInset, y, leftInset + width, y, rulePaint)
    }

    private data class Columns(val left: StaticLayout, val right: StaticLayout, val rightX: Int, val rightY: Int) {
        val height: Int get() = maxOf(left.height, rightY + right.height)
    }

    private data class ReceiptRow(val body: Columns, val title: StaticLayout? = null, val divider: Boolean = false) {
        val height: Int get() = (title?.height ?: 0) + body.height + ROW_SPACING + if (divider) RULE_HEIGHT else 0
    }

    private companion object {
        const val ROW_SPACING = 4
    }
}

private fun textPaint(size: Float, face: Typeface = Typeface.DEFAULT) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    typeface = face
    textSize = size
}

private fun StaticLayout.drawAt(canvas: Canvas, x: Float, y: Float) {
    val save = canvas.save()
    canvas.translate(x, y)
    draw(canvas)
    canvas.restoreToCount(save)
}
