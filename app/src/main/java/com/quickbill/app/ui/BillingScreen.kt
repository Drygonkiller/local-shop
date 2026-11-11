package com.quickbill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.data.Bill
import com.quickbill.app.data.MAX_QUANTITY
import com.quickbill.app.data.SaleLine
import com.quickbill.app.data.formatNpr
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

@Composable
internal fun BillingScreen(
    lines: List<SaleLine>,
    canChangeQuantity: Boolean,
    onChangeQuantity: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(lines, key = { index, line ->
            line.favouriteId?.let { "favourite-$it" } ?: "line-$index"
        }) { _, line ->
            BillLineRow(
                line = line,
                canChangeQuantity = canChangeQuantity,
                onChangeQuantity = onChangeQuantity,
            )
        }
    }
}

@Composable
private fun BillLineRow(
    line: SaleLine,
    canChangeQuantity: Boolean,
    onChangeQuantity: (Long, Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = line.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatNpr(line.totalPaisa),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.unit_price, formatNpr(line.unitPricePaisa)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.unit_price_ne),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                line.favouriteId?.let { favouriteId ->
                    QuantityStepper(
                        itemName = line.name,
                        quantity = line.quantity,
                        enabled = canChangeQuantity,
                        onChange = { delta -> onChangeQuantity(favouriteId, delta) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantityStepper(
    itemName: String,
    quantity: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val minusLabel = stringResource(R.string.decrease_quantity, itemName)
    val plusLabel = stringResource(R.string.increase_quantity, itemName)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        FilledTonalIconButton(
            onClick = { onChange(-1) },
            enabled = enabled,
            modifier = Modifier.size(48.dp).semantics { contentDescription = minusLabel },
            shape = RoundedCornerShape(12.dp),
        ) { BillIcon(BillIcon.Subtract) }
        Text(
            text = quantity.toString(),
            modifier = Modifier.widthIn(min = 32.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(
            onClick = { onChange(1) },
            enabled = enabled && quantity < MAX_QUANTITY,
            modifier = Modifier.size(48.dp).semantics { contentDescription = plusLabel },
            shape = RoundedCornerShape(12.dp),
        ) { BillIcon(BillIcon.Add) }
    }
}

@Composable
internal fun HistoryScreen(
    history: List<Bill>,
    onReprint: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        EmptyScreen(R.string.empty_history, R.string.empty_history_ne, BillIcon.Receipt, modifier)
        return
    }
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kathmandu")
        }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(history, key = { it.billNumber }) { bill ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bill_number, bill.billNumber),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = dateFormat.format(Date(bill.createdAtMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatNpr(bill.totalPaisa),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = { onReprint(bill.billNumber) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        BilingualLabel(R.string.reprint, R.string.reprint_ne, centered = true)
                    }
                }
            }
        }
    }
}
