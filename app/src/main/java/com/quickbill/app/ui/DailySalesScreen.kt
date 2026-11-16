package com.quickbill.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.data.DailySales
import com.quickbill.app.data.TopFavourite
import com.quickbill.app.data.formatNepalBillTime
import com.quickbill.app.data.formatNepalDualDate
import com.quickbill.app.data.formatNpr
import com.quickbill.app.printing.BillText

@Composable
internal fun DailySalesScreen(
    report: DailySales?,
    onOpenBill: (Long) -> Unit,
    onPrintReport: (DailySales) -> Unit,
    canPrint: Boolean,
    modifier: Modifier = Modifier,
) {
    if (report == null) {
        ReportLoading(modifier)
        return
    }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                DailySalesSummary(report)
            }
            item {
                ReportSectionHeading(R.string.report_top_items, R.string.report_top_items_ne)
            }
            if (report.topItems.isEmpty()) {
                item {
                    ReportEmptyMessage(R.string.report_no_top_items, R.string.report_no_top_items_ne)
                }
            } else {
                item {
                    ReportTopItems(report.topItems)
                }
            }
            item {
                ReportSectionHeading(R.string.report_bills_today, R.string.report_bills_today_ne)
            }
            if (report.bills.isEmpty()) {
                item {
                    ReportEmptyMessage(R.string.report_no_bills, R.string.report_no_bills_ne)
                }
            } else {
                items(report.bills, key = { "bill-${it.billNumber}" }) { bill ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        SavedBillSummary(
                            bill = bill,
                            displayDate = formatNepalBillTime(bill.createdAtMillis),
                            onOpenBill = { onOpenBill(bill.billNumber) },
                        )
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = { onPrintReport(report) },
                    enabled = canPrint,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    BillIcon(BillIcon.Print, size = 21.dp)
                    Spacer(Modifier.width(8.dp))
                    BilingualLabel(R.string.report_print, R.string.report_print_ne)
                }
            }
        }
    }
}

@Composable
private fun DailySalesSummary(report: DailySales) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = BillText.date(formatNepalDualDate(report.day.startMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                BilingualLabel(R.string.report_total_sales, R.string.report_total_sales_ne)
                Text(
                    text = formatNpr(report.totalPaisa),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        BoxWithConstraints {
            val baseWidth = maxWidth / LocalDensity.current.fontScale
            if (baseWidth >= 288.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalesMetric(
                        R.string.report_bill_count,
                        R.string.report_bill_count_ne,
                        report.billCount.toString(),
                        Modifier.weight(1f),
                    )
                    SalesMetric(
                        R.string.report_average_bill,
                        R.string.report_average_bill_ne,
                        formatNpr(report.averagePaisa),
                        Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalesMetric(
                        R.string.report_bill_count,
                        R.string.report_bill_count_ne,
                        report.billCount.toString(),
                        Modifier.fillMaxWidth(),
                    )
                    SalesMetric(
                        R.string.report_average_bill,
                        R.string.report_average_bill_ne,
                        formatNpr(report.averagePaisa),
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BilingualLabel(
                    R.string.report_udharo,
                    R.string.report_udharo_ne,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                    Text(
                        formatNpr(report.udharoTotalPaisa),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        stringResource(R.string.report_udharo_count, report.udharoBillCount),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun SalesMetric(@StringRes label: Int, @StringRes nepali: Int, value: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BilingualLabel(label, nepali)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ReportSectionHeading(@StringRes label: Int, @StringRes nepali: Int) {
    BilingualLabel(
        label,
        nepali,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        mainStyle = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ReportEmptyMessage(@StringRes label: Int, @StringRes nepali: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BilingualLabel(label, nepali, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun ReportTopItems(topItems: List<TopFavourite>) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column {
            topItems.forEachIndexed { index, item ->
                if (index != 0) HorizontalDivider(color = scheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatNpr(item.totalPaisa),
                        modifier = Modifier.weight(0.75f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
