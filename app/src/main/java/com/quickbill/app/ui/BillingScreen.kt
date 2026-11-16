package com.quickbill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.data.Bill
import com.quickbill.app.data.BillLine
import com.quickbill.app.data.MAX_QUANTITY
import com.quickbill.app.data.SaleLine
import com.quickbill.app.data.SavedBill
import com.quickbill.app.data.formatNepalDualDateTime
import com.quickbill.app.data.formatNpr
import com.quickbill.app.data.parseNpr
import com.quickbill.app.printing.BillText

@Composable
internal fun BillingScreen(
    lines: List<SaleLine>,
    totalPaisa: Long,
    cashReceived: String,
    onCashReceivedChange: (String) -> Unit,
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
        if (lines.isNotEmpty()) {
            item(key = "cash-received") {
                CashChangePanel(totalPaisa, cashReceived, onCashReceivedChange)
            }
        }
    }
}

private val cashPresetsRupees = listOf(100L, 200L, 500L, 1_000L)

@Composable
private fun CashChangePanel(
    totalPaisa: Long,
    cashReceived: String,
    onCashReceivedChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val receivedPaisa = remember(cashReceived) { parseNpr(cashReceived, maxPaisa = Long.MAX_VALUE) }
    val changePaisa = calculateChange(totalPaisa, receivedPaisa)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BilingualLabel(R.string.cash_received, R.string.cash_received_ne)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (rupees in cashPresetsRupees) {
                    FilterChip(
                        selected = receivedPaisa == rupees * 100,
                        onClick = {
                            onCashReceivedChange(rupees.toString())
                            focusManager.clearFocus()
                        },
                        label = {
                            Text(formatNpr(rupees * 100), style = MaterialTheme.typography.labelMedium)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
            OutlinedTextField(
                value = cashReceived,
                onValueChange = onCashReceivedChange,
                label = { Text(stringResource(R.string.cash_custom_amount)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            if (changePaisa != null) {
                Text(
                    text = stringResource(R.string.cash_change, formatNpr(changePaisa)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
    query: String,
    onQueryChange: (String) -> Unit,
    onReprint: (Long) -> Unit,
    canReprint: Boolean,
    onOpenBill: (Long) -> Unit,
    onShare: (Long) -> Unit,
    onClearHistory: () -> Unit,
    canDeleteBills: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val matches = remember(history, query) { filterHistory(history, query) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    Column(modifier) {
        if (history.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    onClearHistory()
                },
                enabled = canDeleteBills,
                modifier = Modifier.align(Alignment.End).padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                BilingualLabel(R.string.clear_history_all, R.string.clear_history_all_ne, centered = true)
            }
        }
        val clearSearchLabel = stringResource(R.string.history_clear_search)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text(stringResource(R.string.history_search)) },
            leadingIcon = { BillIcon(BillIcon.Search, size = 20.dp) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = clearSearchLabel },
                    ) { BillIcon(BillIcon.Close, size = 20.dp) }
                }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )
        if (matches.isEmpty()) {
            EmptyScreen(
                if (history.isEmpty()) R.string.empty_history else R.string.history_no_match,
                if (history.isEmpty()) R.string.empty_history_ne else R.string.history_no_match_ne,
                BillIcon.Receipt,
                Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(matches, key = { it.billNumber }) { bill ->
                    HistoryBillCard(
                        bill = bill,
                        canReprint = canReprint,
                        onOpen = { onOpenBill(bill.billNumber) },
                        onShare = { onShare(bill.billNumber) },
                        onReprint = { onReprint(bill.billNumber) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryBillCard(
    bill: Bill,
    canReprint: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onReprint: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            SavedBillSummary(
                bill = bill,
                displayDate = formatNepalDualDateTime(bill.createdAtMillis),
                onOpenBill = onOpen,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f, fill = false).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    BilingualLabel(R.string.share, R.string.share_ne, centered = true)
                }
                OutlinedButton(
                    onClick = onReprint,
                    enabled = canReprint,
                    modifier = Modifier.weight(1f, fill = false).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    BilingualLabel(R.string.reprint, R.string.reprint_ne, centered = true)
                }
            }
        }
    }
}

@Composable
internal fun SavedBillSummary(bill: Bill, displayDate: String, onOpenBill: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.saved_bill_open, bill.billNumber),
                role = Role.Button,
                onClick = onOpenBill,
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.bill_number, bill.billNumber),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(displayDate, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Text(
            text = formatNpr(bill.totalPaisa),
            modifier = Modifier.weight(0.85f),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.primary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
        Text(
            text = "›",
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SavedBillScreen(
    bill: SavedBill?,
    onShare: (Long) -> Unit,
    onReprint: (Long) -> Unit,
    canReprint: Boolean,
    onDeleteBill: (Long) -> Unit,
    onNewBill: () -> Unit,
    canDeleteBills: Boolean = true,
    isBillMissing: Boolean = false,
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
    val header = bill.bill
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(header.shopName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.saved_bill_pan, header.shopPan))
                        Text(
                            text = stringResource(R.string.bill_number, header.billNumber),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = BillText.date(formatNepalDualDateTime(header.createdAtMillis)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (header.customerId != null) {
                            header.customerName?.let { Text(BillText.udharo(it), fontWeight = FontWeight.SemiBold) }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = scheme.outlineVariant)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BilingualLabel(
                                R.string.bill_total,
                                R.string.bill_total_ne,
                                modifier = Modifier.weight(1f),
                                mainStyle = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = formatNpr(header.totalPaisa),
                                modifier = Modifier.weight(2f),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End,
                            )
                        }
                        Text(
                            text = BillText.inWords(header.totalPaisa),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(bill.lines, key = { it.position }) { line -> SavedBillLine(line) }
        }
        Surface(color = scheme.surface) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = scheme.outlineVariant)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onNewBill,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        BilingualLabel(
                            R.string.new_bill,
                            R.string.new_bill_ne,
                            centered = true,
                            mainStyle = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val actionPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)
                        OutlinedButton(
                            onClick = { onShare(header.billNumber) },
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = actionPadding,
                        ) {
                            BilingualLabel(R.string.share, R.string.share_ne, centered = true)
                        }
                        Button(
                            onClick = { onReprint(header.billNumber) },
                            enabled = canReprint,
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = actionPadding,
                        ) {
                            BilingualLabel(R.string.reprint, R.string.reprint_ne, centered = true)
                        }
                        OutlinedButton(
                            onClick = { onDeleteBill(header.billNumber) },
                            enabled = canDeleteBills,
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = actionPadding,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                        ) {
                            BilingualLabel(R.string.delete_bill, R.string.delete_bill_ne, centered = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedBillLine(line: BillLine) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(line.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(R.string.saved_bill_line_quantity, line.quantity, formatNpr(line.unitPricePaisa)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatNpr(line.totalPaisa),
                modifier = Modifier.weight(0.85f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
internal fun ReportLoading(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
