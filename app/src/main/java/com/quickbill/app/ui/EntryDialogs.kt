package com.quickbill.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.quickbill.app.data.FavouriteItem
import com.quickbill.app.data.ShopDetails
import com.quickbill.app.data.normalizePan
import com.quickbill.app.data.parseNpr
import java.math.BigDecimal

@Composable
internal fun ItemEditorDialog(
    item: FavouriteItem? = null,
    onDismiss: () -> Unit,
    onSave: (String, Long, (Boolean) -> Unit) -> Unit,
) {
    var name by rememberSaveable(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var price by rememberSaveable(item?.id) {
        val enteredPrice = item?.let { BigDecimal.valueOf(it.unitPricePaisa, 2).stripTrailingZeros().toPlainString() }
        mutableStateOf(enteredPrice.orEmpty())
    }
    var invalidPrice by rememberSaveable { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val submit = {
        if (!isSaving && name.isNotBlank()) {
            val amount = parseNpr(price)
            invalidPrice = amount == null
            if (amount != null) {
                isSaving = true
                onSave(name.trim(), amount) { saved ->
                    isSaving = false
                    if (saved) onDismiss()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            BilingualLabel(
                if (item == null) R.string.add_item else R.string.edit_item,
                if (item == null) R.string.add_item_ne else R.string.edit_item_ne,
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
                    label = { Text(stringResource(R.string.item_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = {
                        price = it
                        invalidPrice = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.item_price)) },
                    isError = invalidPrice,
                    supportingText = if (invalidPrice) {
                        { Text(stringResource(R.string.item_price_error)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                LaunchedEffect(Unit) { nameFocus.requestFocus() }
            }
        },
        confirmButton = {
            Button(
                onClick = submit,
                enabled = !isSaving && name.isNotBlank() && price.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                BilingualLabel(R.string.save, R.string.save_ne, centered = true)
            }
        },
        dismissButton = { CancelButton(onDismiss, enabled = !isSaving) },
    )
}

@Composable
internal fun ShopDialog(
    shop: ShopDetails?,
    forPrint: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, (Boolean) -> Unit) -> Unit,
    onSaved: () -> Unit,
    onThermalPrinter: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(shop?.name.orEmpty()) }
    var pan by rememberSaveable { mutableStateOf(shop?.pan.orEmpty()) }
    var invalidPan by rememberSaveable { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val submit = {
        if (!isSaving && name.isNotBlank()) {
            val normalizedPan = normalizePan(pan)
            invalidPan = normalizedPan == null
            if (normalizedPan != null) {
                isSaving = true
                onSave(name.trim(), normalizedPan) { saved ->
                    isSaving = false
                    if (saved) onSaved()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            BilingualLabel(R.string.shop_details, R.string.shop_details_ne, mainStyle = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BilingualLabel(
                    R.string.shop_details_print,
                    R.string.shop_details_print_ne,
                    mainStyle = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.shop_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
                OutlinedTextField(
                    value = pan,
                    onValueChange = {
                        pan = it
                        invalidPan = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.shop_pan)) },
                    isError = invalidPan,
                    supportingText = if (invalidPan) {
                        { Text(stringResource(R.string.shop_pan_error)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                if (onThermalPrinter != null) {
                    OutlinedButton(
                        onClick = onThermalPrinter,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.thermal_title))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = submit,
                enabled = !isSaving && name.isNotBlank() && pan.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                BilingualLabel(
                    if (forPrint) R.string.save_and_print else R.string.save,
                    if (forPrint) R.string.save_and_print_ne else R.string.save_ne,
                    centered = true,
                )
            }
        },
        dismissButton = { CancelButton(onDismiss, enabled = !isSaving) },
    )
}

@Composable
internal fun CancelButton(onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        BilingualLabel(R.string.cancel, R.string.cancel_ne, centered = true)
    }
}
