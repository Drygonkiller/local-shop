package com.quickbill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.data.CreditEntry
import com.quickbill.app.data.CreditLedger
import com.quickbill.app.data.CreditSummary
import com.quickbill.app.data.formatNepalDualDateTime
import com.quickbill.app.data.formatNpr

@Composable
internal fun KhataScreen(
    customers: List<CreditSummary>?,
    onOpenCustomer: (Long) -> Unit,
    onCreateCustomer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilledTonalButton(
                onClick = onCreateCustomer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                BillIcon(BillIcon.Add, size = 20.dp)
                Spacer(Modifier.width(6.dp))
                BilingualLabel(R.string.khata_new_customer, R.string.khata_new_customer_ne)
            }
        }
        when {
            customers == null -> ReportLoading(Modifier.weight(1f).fillMaxWidth())
            customers.isEmpty() -> EmptyScreen(
                R.string.khata_no_customers,
                R.string.khata_no_customers_ne,
                BillIcon.Receipt,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(customers, key = { it.customer.id }) { summary ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.surface,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                    ) {
                        CustomerSummaryRow(
                            summary = summary,
                            clickLabel = stringResource(R.string.khata_open_action, summary.customer.name),
                            onClick = { onOpenCustomer(summary.customer.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomerSummaryRow(summary: CreditSummary, clickLabel: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = summary.customer.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            summary.customer.phone?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Column(
            modifier = Modifier.weight(0.85f),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = stringResource(R.string.khata_due_short),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
            Text(
                text = formatNpr(summary.duePaisa),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = if (summary.duePaisa.signum() > 0) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        LedgerChevron()
    }
}

@Composable
internal fun CustomerLedgerScreen(
    ledger: CreditLedger?,
    onOpenBill: (Long) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onAddPayment: (Long, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    isMissing: Boolean = false,
) {
    if (ledger == null) {
        if (isMissing) {
            EmptyScreen(R.string.khata_customer_missing, R.string.khata_customer_missing_ne, BillIcon.Receipt, modifier)
        } else {
            ReportLoading(modifier)
        }
        return
    }
    val scheme = MaterialTheme.colorScheme
    var showPayment by rememberSaveable(ledger.customer.id) { mutableStateOf(false) }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { LedgerSummary(ledger, onShare, onEdit) }
            item {
                BilingualLabel(
                    R.string.khata_transactions,
                    R.string.khata_transactions_ne,
                    modifier = Modifier.padding(top = 6.dp).semantics { heading() },
                    mainStyle = MaterialTheme.typography.titleMedium,
                )
            }
            if (ledger.entries.isEmpty()) {
                item {
                    BilingualLabel(
                        R.string.khata_no_transactions,
                        R.string.khata_no_transactions_ne,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
            items(
                items = ledger.entries,
                key = { entry ->
                    when (entry) {
                        is CreditEntry.BillEntry -> "bill-${entry.bill.billNumber}"
                        is CreditEntry.PaymentEntry -> "payment-${entry.payment.id}"
                    }
                },
            ) { entry ->
                LedgerEntryRow(entry, onOpenBill)
            }
        }
        Surface(color = scheme.surface) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = scheme.outlineVariant)
                Button(
                    onClick = { showPayment = true },
                    enabled = ledger.duePaisa.signum() > 0,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    BillIcon(BillIcon.Add, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    BilingualLabel(R.string.khata_add_payment, R.string.khata_add_payment_ne)
                }
            }
        }
    }
    if (showPayment) {
        AddPaymentDialog(
            duePaisa = ledger.duePaisa,
            onSave = onAddPayment,
            onDismiss = { showPayment = false },
        )
    }
}

@Composable
private fun LedgerSummary(ledger: CreditLedger, onShare: () -> Unit, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(ledger.customer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ledger.customer.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            BilingualLabel(
                R.string.khata_balance,
                R.string.khata_balance_ne,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = formatNpr(ledger.duePaisa),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    BilingualLabel(R.string.khata_edit_customer, R.string.khata_edit_customer_ne, centered = true)
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    BilingualLabel(R.string.khata_share, R.string.khata_share_ne, centered = true)
                }
            }
        }
    }
}

@Composable
private fun LedgerEntryRow(entry: CreditEntry, onOpenBill: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val billNumber = (entry as? CreditEntry.BillEntry)?.bill?.billNumber
    val title = if (billNumber == null) {
        stringResource(R.string.khata_payment)
    } else {
        stringResource(R.string.khata_credit_bill, billNumber)
    }
    val clickModifier = if (billNumber == null) {
        Modifier
    } else {
        Modifier.clickable(
            onClickLabel = stringResource(R.string.saved_bill_open, billNumber),
            role = Role.Button,
            onClick = { onOpenBill(billNumber) },
        )
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().then(clickModifier).heightIn(min = 60.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatNpr(if (billNumber == null) -entry.amountPaisa else entry.amountPaisa),
                    modifier = Modifier.weight(0.85f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = scheme.primary,
                )
                if (billNumber != null) LedgerChevron()
            }
            Text(
                text = formatNepalDualDateTime(entry.createdAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LedgerChevron() {
    Box(Modifier.clearAndSetSemantics {}) {
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
