package com.quickbill.app.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.quickbill.app.R
import com.quickbill.app.data.FavouritePhotoStorage

internal enum class FavouritePhotoSource { Camera, Gallery }

private class OpenLocalPhoto : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

private class TakeItemPhoto : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
        clipData = ClipData.newRawUri(context.getText(R.string.take_photo), input)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}

@Composable
internal fun rememberFavouritePhotoLauncher(onSetPhoto: (Long, String?) -> Unit): (Long, FavouritePhotoSource) -> Unit {
    val context = LocalContext.current
    val photos = remember(context) { FavouritePhotoStorage(context.applicationContext) }
    var targetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(remember { OpenLocalPhoto() }) { uri ->
        val itemId = targetId
        targetId = null
        if (uri != null && itemId != null) {
            val permission = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (permission.isSuccess) {
                onSetPhoto(itemId, uri.toString())
            } else {
                Toast.makeText(context, R.string.photo_access_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val camera = rememberLauncherForActivityResult(remember { TakeItemPhoto() }) { captured ->
        val itemId = targetId
        val uri = captureUri
        targetId = null
        captureUri = null
        if (captured && itemId != null && uri != null && photos.hasCapture(uri)) {
            onSetPhoto(itemId, uri)
        } else {
            photos.deleteOwnedPhoto(uri)
            if (captured) Toast.makeText(context, R.string.photo_capture_failed, Toast.LENGTH_SHORT).show()
        }
    }
    return { itemId, source ->
        if (targetId == null) {
            targetId = itemId
            try {
                when (source) {
                    FavouritePhotoSource.Camera -> {
                        val destination = photos.createCaptureUri()
                        captureUri = destination.toString()
                        camera.launch(destination)
                    }
                    FavouritePhotoSource.Gallery -> gallery.launch(arrayOf("image/*"))
                }
            } catch (_: Exception) {
                photos.deleteOwnedPhoto(captureUri)
                targetId = null
                captureUri = null
                val message = when (source) {
                    FavouritePhotoSource.Camera -> R.string.photo_capture_failed
                    FavouritePhotoSource.Gallery -> R.string.photo_access_error
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
