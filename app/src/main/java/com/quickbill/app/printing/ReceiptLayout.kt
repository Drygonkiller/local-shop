package com.quickbill.app.printing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.CancellationSignal
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.quickbill.app.data.formatNepalBillTime
import com.quickbill.app.data.formatNepalDualDate
import com.quickbill.app.data.formatNepalDualDateTime
import com.quickbill.app.data.formatNpr
import kotlin.math.ceil

private const val PAGE_PADDING = 6
private const val RECEIPT_MAX_WIDTH = 260
private const val COLUMN_GAP = 8
private const val RULE_HEIGHT = 6

/** Lays out a document for a paginated PDF or a continuous thermal roll. */
internal class ReceiptLayout(
    document: PrintDocument,
    paperWidth: Int,
    paperHeight: Int?,
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
        header = when (document) {
            is PrintDocument.Receipt -> receiptHeader(document)
            is PrintDocument.Report -> reportHeader(document)
        }
        val rows = when (document) {
            is PrintDocument.Receipt -> receiptRows(document, cancellationSignal)
            is PrintDocument.Report -> reportRows(document, cancellationSignal)
        }
        pages = if (paperHeight == null) listOf(rows) else paginate(rows, paperHeight)
    }

    private fun receiptHeader(document: PrintDocument.Receipt): List<StaticLayout> {
        val bill = document.bill.bill
        return listOf(
            text(bill.shopName, titleStyle, alignment = Layout.Alignment.ALIGN_CENTER),
            text(BillText.pan(bill.shopPan), detailStyle),
            text(
                if (document.isTest) "Test receipt / परीक्षण बिल" else BillText.number(bill.billNumber),
                if (document.isTest) amountStyle else detailStyle,
            ),
            text(BillText.date(formatNepalDualDateTime(bill.createdAtMillis)), detailStyle),
        )
    }

    private fun receiptRows(
        document: PrintDocument.Receipt,
        cancellationSignal: CancellationSignal?,
    ): List<ReceiptRow> = buildList {
        val savedBill = document.bill
        for ((index, line) in savedBill.lines.withIndex()) {
            cancellationSignal?.throwIfCanceled()
            add(ReceiptRow(
                title = text(line.name, nameStyle),
                body = columns(
                    BillText.quantityAndUnitPrice(line.quantity, line.unitPricePaisa), detailStyle,
                    formatNpr(line.totalPaisa), amountStyle,
                ),
                keepWithNext = index == savedBill.lines.lastIndex,
            ))
        }
        add(ReceiptRow(
            body = columns(BillText.Total, totalStyle, formatNpr(savedBill.bill.totalPaisa), totalStyle),
            divider = true,
            keepWithNext = true,
        ))
        add(ReceiptRow(title = text(BillText.inWords(savedBill.bill.totalPaisa), nameStyle)))
    }

    private fun reportHeader(document: PrintDocument.Report): List<StaticLayout> = buildList {
        add(text("Daily sales / दैनिक बिक्री", titleStyle, alignment = Layout.Alignment.ALIGN_CENTER))
        document.shop?.let { shop ->
            add(text(shop.name, totalStyle, alignment = Layout.Alignment.ALIGN_CENTER))
            add(text(BillText.pan(shop.pan), detailStyle))
        }
        add(text(BillText.date(formatNepalDualDate(document.report.day.startMillis)), detailStyle))
    }

    private fun reportRows(
        document: PrintDocument.Report,
        cancellationSignal: CancellationSignal?,
    ): List<ReceiptRow> = buildList {
        val report = document.report
        add(ReceiptRow(body = columns("Total sales / कुल बिक्री", totalStyle, formatNpr(report.totalPaisa), totalStyle)))
        add(ReceiptRow(body = columns("Bills / बिल", nameStyle, report.billCount.toString(), amountStyle)))
        add(ReceiptRow(body = columns("Average bill / औसत बिल", nameStyle, formatNpr(report.averagePaisa), amountStyle)))

        add(sectionHeading("Top items by revenue / बिक्री रकमअनुसार शीर्ष सामान"))
        if (report.topItems.isEmpty()) {
            add(ReceiptRow(title = text("No favourite items sold / मनपर्ने सामानको बिक्री भएको छैन", detailStyle)))
        }
        for (item in report.topItems) {
            cancellationSignal?.throwIfCanceled()
            add(ReceiptRow(body = columns(item.name, nameStyle, formatNpr(item.totalPaisa), amountStyle)))
        }

        add(sectionHeading("Today’s bills / आजका बिल"))
        if (report.bills.isEmpty()) {
            add(ReceiptRow(title = text("No bills today / आज कुनै बिल छैन", detailStyle)))
        }
        for (bill in report.bills) {
            cancellationSignal?.throwIfCanceled()
            add(ReceiptRow(body = columns(
                "${BillText.number(bill.billNumber)} · ${formatNepalBillTime(bill.createdAtMillis)}", detailStyle,
                formatNpr(bill.totalPaisa), amountStyle,
            )))
        }
    }

    private fun sectionHeading(value: String) = ReceiptRow(
        title = text(value, amountStyle),
        divider = true,
        keepWithNext = true,
    )

    private fun paginate(rows: List<ReceiptRow>, paperHeight: Int): List<List<ReceiptRow>> {
        val bodyHeight = paperHeight - headerHeight - PAGE_PADDING
        val documentPages = mutableListOf<List<ReceiptRow>>()
        var page = mutableListOf<ReceiptRow>()
        var available = bodyHeight

        for ((index, row) in rows.withIndex()) {
            require(row.height <= bodyHeight) { "Printable height is too small" }
            var groupHeight = row.height
            var groupEnd = index
            while (groupEnd < rows.lastIndex && rows[groupEnd].keepWithNext) {
                groupEnd++
                groupHeight += rows[groupEnd].height
            }
            val needed = if (groupHeight <= bodyHeight) groupHeight else row.height
            if (needed > available) {
                documentPages.add(page)
                page = mutableListOf()
                available = bodyHeight
            }
            page.add(row)
            available -= row.height
        }
        documentPages.add(page)
        return documentPages
    }

    fun drawPage(canvas: Canvas, index: Int) {
        drawHeader(canvas)
        var y = headerHeight.toFloat()
        for (row in pages[index]) {
            drawRow(canvas, row, y)
            y += row.height
        }
    }

    fun drawRoll(drawSection: (Int, (Canvas) -> Unit) -> Unit) {
        drawSection(headerHeight, ::drawHeader)
        for (row in pages.single()) {
            drawSection(row.height) { canvas -> drawRow(canvas, row, 0f) }
        }
        drawSection(PAGE_PADDING) {}
    }

    private val headerHeight: Int get() = PAGE_PADDING + header.sumOf { it.height } + RULE_HEIGHT

    private fun drawHeader(canvas: Canvas) {
        var y = PAGE_PADDING.toFloat()
        for (line in header) {
            line.drawAt(canvas, leftInset, y)
            y += line.height
        }
        drawRule(canvas, y)
    }

    private fun drawRow(canvas: Canvas, row: ReceiptRow, top: Float) {
        var y = top
        if (row.divider) {
            drawRule(canvas, y)
            y += RULE_HEIGHT
        }
        row.title?.let { title ->
            title.drawAt(canvas, leftInset, y)
            y += title.height
        }
        row.body?.let { body ->
            body.left.drawAt(canvas, leftInset, y)
            body.right.drawAt(canvas, leftInset + body.rightX, y + body.rightY)
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

    private data class ReceiptRow(
        val body: Columns? = null,
        val title: StaticLayout? = null,
        val divider: Boolean = false,
        val keepWithNext: Boolean = false,
    ) {
        val height: Int get() = (title?.height ?: 0) + (body?.height ?: 0) + ROW_SPACING + if (divider) RULE_HEIGHT else 0
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
