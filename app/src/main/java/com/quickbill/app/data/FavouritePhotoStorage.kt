package com.quickbill.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.URI
import java.net.URISyntaxException

private const val PHOTO_DIRECTORY = "item_photos"
private val photoPath = Regex("/$PHOTO_DIRECTORY/([A-Za-z0-9][A-Za-z0-9_.-]*\\.jpg)")

internal fun appOwnedFavouritePhotoName(uri: String?, packageName: String): String? {
    if (uri == null) return null
    val parsed = try {
        URI(uri)
    } catch (_: URISyntaxException) {
        return null
    }
    if (parsed.scheme != "content" || parsed.rawAuthority != "$packageName.fileprovider" ||
        parsed.rawQuery != null || parsed.rawFragment != null
    ) return null
    return parsed.rawPath?.let { photoPath.matchEntire(it)?.groupValues?.get(1) }
}

internal class FavouritePhotoStorage(private val context: Context) {
    private val directory get() = File(context.filesDir, PHOTO_DIRECTORY)

    fun createCaptureUri(): Uri {
        val photoDirectory = directory
        check(photoDirectory.isDirectory || photoDirectory.mkdirs()) { "Could not create item photo directory" }
        val file = File.createTempFile("item-", ".jpg", photoDirectory)
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (failure: Exception) {
            deleteFile(file)
            throw failure
        }
    }

    fun hasCapture(uri: String): Boolean = ownedFile(uri)?.let { it.isFile && it.length() > 0 } == true

    fun deleteOwnedPhoto(uri: String?) {
        ownedFile(uri)?.let(::deleteFile)
    }

    private fun ownedFile(uri: String?): File? =
        appOwnedFavouritePhotoName(uri, context.packageName)?.let { File(directory, it) }

    private fun deleteFile(file: File) {
        try {
            if (!file.delete() && file.exists()) Log.w("QuickBill", "Could not remove the item photo file")
        } catch (failure: SecurityException) {
            Log.w("QuickBill", "Could not remove the item photo file", failure)
        }
    }
}
