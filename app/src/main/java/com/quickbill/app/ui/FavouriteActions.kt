package com.quickbill.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.data.FavouriteItem

private enum class FavouriteAction { Menu, Edit, Delete }

@Composable
internal fun FavouriteActions(
    item: FavouriteItem,
    onDismiss: () -> Unit,
    onEdit: (Long, String, Long, (Boolean) -> Unit) -> Unit,
    onDelete: (Long, (Boolean) -> Unit) -> Unit,
    onPickPhoto: (Long) -> Unit,
    onRemovePhoto: (Long) -> Unit,
) {
    var action by rememberSaveable(item.id) { mutableStateOf(FavouriteAction.Menu) }
    when (action) {
        FavouriteAction.Menu -> {
            val hasPhoto = item.photoUri != null
            val actionSize = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(item.name) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { action = FavouriteAction.Edit }, modifier = actionSize) {
                            BilingualLabel(R.string.edit_item, R.string.edit_item_ne, centered = true)
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onPickPhoto(item.id)
                            },
                            modifier = actionSize,
                        ) {
                            BilingualLabel(
                                if (hasPhoto) R.string.change_photo else R.string.add_photo,
                                if (hasPhoto) R.string.change_photo_ne else R.string.add_photo_ne,
                                centered = true,
                            )
                        }
                        if (hasPhoto) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onRemovePhoto(item.id)
                                },
                                modifier = actionSize,
                            ) {
                                BilingualLabel(R.string.remove_photo, R.string.remove_photo_ne, centered = true)
                            }
                        }
                        OutlinedButton(
                            onClick = { action = FavouriteAction.Delete },
                            modifier = actionSize,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            BilingualLabel(R.string.delete_item, R.string.delete_item_ne, centered = true)
                        }
                    }
                },
                confirmButton = { CancelButton(onDismiss) },
            )
        }
        FavouriteAction.Edit -> ItemEditorDialog(
            item = item,
            onDismiss = onDismiss,
            onSave = { name, price, onComplete -> onEdit(item.id, name, price, onComplete) },
        )
        FavouriteAction.Delete -> DeleteFavouriteDialog(
            itemName = item.name,
            onDismiss = onDismiss,
            onDelete = { onComplete -> onDelete(item.id, onComplete) },
        )
    }
}

@Composable
private fun DeleteFavouriteDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onDelete: ((Boolean) -> Unit) -> Unit,
) {
    var isDeleting by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = {
            BilingualLabel(R.string.delete_item, R.string.delete_item_ne, mainStyle = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.confirm_delete_favourite, itemName))
                Text(stringResource(R.string.confirm_delete_favourite_ne, itemName))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isDeleting) {
                        isDeleting = true
                        onDelete { deleted ->
                            isDeleting = false
                            if (deleted) onDismiss()
                        }
                    }
                },
                enabled = !isDeleting,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
            ) {
                BilingualLabel(R.string.delete_item, R.string.delete_item_ne, centered = true)
            }
        },
        dismissButton = { CancelButton(onDismiss, enabled = !isDeleting) },
    )
}
