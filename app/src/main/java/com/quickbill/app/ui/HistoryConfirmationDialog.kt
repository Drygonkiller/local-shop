package com.quickbill.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quickbill.app.R

internal enum class HistoryConfirmation(
    @param:StringRes val title: Int,
    @param:StringRes val titleNe: Int,
    @param:StringRes val detail: Int,
) {
    DeleteBill(R.string.delete_bill, R.string.delete_bill_ne, R.string.delete_bill_detail),
    ClearHistory(R.string.clear_history_all, R.string.clear_history_all_ne, R.string.clear_history_detail),
    DeleteAllBills(R.string.delete_all_bills_question, R.string.delete_all_bills_question_ne, R.string.delete_all_bills_detail),
}

@Composable
internal fun HistoryConfirmationDialog(
    confirmation: HistoryConfirmation,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val firstStep = confirmation == HistoryConfirmation.ClearHistory
    val action = when (confirmation) {
        HistoryConfirmation.DeleteBill -> R.string.delete_bill to R.string.delete_bill_ne
        HistoryConfirmation.ClearHistory -> R.string.continue_clear_history to R.string.continue_clear_history_ne
        HistoryConfirmation.DeleteAllBills -> R.string.clear_history_all to R.string.clear_history_all_ne
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            BilingualLabel(confirmation.title, confirmation.titleNe, mainStyle = MaterialTheme.typography.titleLarge)
        },
        text = { Text(stringResource(confirmation.detail)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = if (firstStep) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { BilingualLabel(action.first, action.second, centered = true) }
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}
