package com.quickbill.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.data.CreditSummary
import com.quickbill.app.data.Customer
import com.quickbill.app.data.formatNpr
import com.quickbill.app.data.parseNpr
import java.math.BigInteger

@Composable
internal fun CustomerPickerDialog(
    customers: List<CreditSummary>?,
    onChoose: (Customer) -> Unit,
    onCreateCustomer: () -> Unit,
    onDismiss: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val matches = remember(customers, search) {
        val query = search.trim()
        val isPhoneQuery = query.all { it.isDigit() || it in "+-() " }
        val digits = query.phoneDigits()
        customers?.filter {
            val customer = it.customer
            customer.name.contains(query, ignoreCase = true) ||
                (isPhoneQuery && digits.isNotEmpty() && customer.phone?.phoneDigits()?.contains(digits) == true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            BilingualLabel(
                R.string.khata_choose_customer,
                R.string.khata_choose_customer_ne,
                mainStyle = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BilingualLabel(
                    R.string.khata_choose_hint,
                    R.string.khata_choose_hint_ne,
                    mainStyle = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.khata_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                when {
                    matches == null -> ReportLoading(Modifier.fillMaxWidth().heightIn(min = 56.dp))
                    matches.isEmpty() -> BilingualLabel(
                        if (search.isBlank()) R.string.khata_no_customers else R.string.khata_no_matches,
                        if (search.isBlank()) R.string.khata_no_customers_ne else R.string.khata_no_matches_ne,
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(matches, key = { it.customer.id }) { summary ->
                            val customer = summary.customer
                            CustomerSummaryRow(
                                summary = summary,
                                clickLabel = stringResource(R.string.khata_choose_action, customer.name),
                                onClick = { onChoose(customer) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreateCustomer, modifier = Modifier.heightIn(min = 48.dp)) {
                BillIcon(BillIcon.Add, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                BilingualLabel(R.string.khata_new_customer, R.string.khata_new_customer_ne, centered = true)
            }
        },
        dismissButton = { CancelButton(onDismiss) },
    )
}

private fun String.phoneDigits(): String = mapNotNull { digit ->
    digit.digitToIntOrNull()?.toString()
}.joinToString("")

@Composable
internal fun CustomerEditorDialog(
    customer: Customer? = null,
    onSave: (String, String?, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    forCredit: Boolean = false,
) {
    var name by rememberSaveable(customer?.id) { mutableStateOf(customer?.name.orEmpty()) }
    var phone by rememberSaveable(customer?.id) { mutableStateOf(customer?.phone.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val submit: () -> Unit = {
        if (!isSaving && name.isNotBlank()) {
            isSaving = true
            onSave(name.trim(), phone.trim().takeIf(String::isNotEmpty)) { saved ->
                isSaving = false
                if (saved) onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            BilingualLabel(
                if (customer == null) R.string.khata_new_customer else R.string.khata_edit_customer,
                if (customer == null) R.string.khata_new_customer_ne else R.string.khata_edit_customer_ne,
                mainStyle = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.khata_customer_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.khata_customer_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                LaunchedEffect(Unit) { nameFocus.requestFocus() }
            }
        },
        confirmButton = {
            Button(
                onClick = submit,
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                BilingualLabel(
                    if (forCredit) R.string.khata_add_and_print else R.string.save,
                    if (forCredit) R.string.khata_add_and_print_ne else R.string.save_ne,
                    centered = true,
                )
            }
        },
        dismissButton = { CancelButton(onDismiss, enabled = !isSaving) },
    )
}

@Composable
internal fun AddPaymentDialog(
    duePaisa: BigInteger,
    onSave: (Long, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<Int?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val submit: () -> Unit = {
        if (!isSaving) {
            val amount = parseNpr(input, maxPaisa = Long.MAX_VALUE)
            when {
                amount == null || amount <= 0 -> validationError = R.string.khata_payment_invalid
                amount.toBigInteger() > duePaisa -> validationError = R.string.khata_payment_exceeds_due
                else -> {
                    validationError = null
                    isSaving = true
                    onSave(amount) { saved ->
                        isSaving = false
                        if (saved) onDismiss() else validationError = R.string.khata_payment_save_failed
                    }
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            BilingualLabel(
                R.string.khata_add_payment,
                R.string.khata_add_payment_ne,
                mainStyle = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.khata_balance_value, formatNpr(duePaisa)))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.khata_payment_amount)) },
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = validationError?.let { message -> { Text(stringResource(message)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                LaunchedEffect(Unit) { inputFocus.requestFocus() }
            }
        },
        confirmButton = {
            Button(
                onClick = submit,
                enabled = !isSaving && input.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                BilingualLabel(R.string.save, R.string.save_ne, centered = true)
            }
        },
        dismissButton = { CancelButton(onDismiss, enabled = !isSaving) },
    )
}
