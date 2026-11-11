package com.quickbill.app.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.data.FavouriteItem
import com.quickbill.app.data.SaleLine
import com.quickbill.app.data.formatNpr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FavouritesScreen(
    favourites: List<FavouriteItem>,
    lines: List<SaleLine>,
    isLoading: Boolean,
    canAddToBill: Boolean,
    onAddItem: () -> Unit,
    onTap: (FavouriteItem) -> Unit,
    onManagePhoto: (FavouriteItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quantities = remember(lines) { lines.associate { it.favouriteId to it.quantity } }
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BilingualLabel(
                R.string.favourites,
                R.string.favourites_ne,
                modifier = Modifier.weight(1f).semantics { heading() },
                mainStyle = MaterialTheme.typography.titleLarge,
                secondaryStyle = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(
                onClick = onAddItem,
                enabled = !isLoading,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            ) {
                BillIcon(BillIcon.Add, size = 20.dp)
                Spacer(Modifier.width(6.dp))
                BilingualLabel(R.string.add_item, R.string.add_item_ne)
            }
        }
        when {
            isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            favourites.isEmpty() -> EmptyScreen(
                R.string.empty_favourites,
                R.string.empty_favourites_ne,
                BillIcon.Bag,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 136.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(favourites, key = { it.id }) { item ->
                    FavouriteTile(
                        item = item,
                        quantity = quantities[item.id] ?: 0,
                        enabled = canAddToBill,
                        onClick = { onTap(item) },
                        onLongClick = { onManagePhoto(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteTile(
    item: FavouriteItem,
    quantity: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val selected = quantity > 0
    val shape = RoundedCornerShape(16.dp)
    val scheme = MaterialTheme.colorScheme
    val quantityDescription = stringResource(R.string.quantity_in_bill, quantity)
    Surface(
        shape = shape,
        color = if (selected) scheme.primaryContainer else scheme.surface,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(shape)
                .combinedClickable(
                    enabled = enabled,
                    onClickLabel = stringResource(R.string.add_to_bill),
                    onLongClickLabel = stringResource(R.string.manage_photo),
                    role = Role.Button,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .semantics { if (selected) stateDescription = quantityDescription }
                .heightIn(min = 104.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.photoUri?.let { FavouritePhoto(it) }
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatNpr(item.unitPricePaisa),
                    modifier = Modifier.weight(1f),
                    color = scheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = scheme.primary,
                        contentColor = scheme.onPrimary,
                        modifier = Modifier.clearAndSetSemantics {},
                    ) {
                        Text(
                            text = quantity.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavouritePhoto(photoUri: String) {
    val resolver = LocalContext.current.contentResolver
    val image by produceState<ImageBitmap?>(null, photoUri, resolver) {
        value = null
        value = withContext(Dispatchers.IO) { loadThumbnail(resolver, Uri.parse(photoUri)) }
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun loadThumbnail(resolver: ContentResolver, uri: Uri): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 256) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()
