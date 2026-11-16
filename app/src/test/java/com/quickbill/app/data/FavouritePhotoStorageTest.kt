package com.quickbill.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavouritePhotoStorageTest {
    @Test
    fun acceptsOnlyThisInstallsPhotoProvider() {
        val productionPhoto = "content://com.quickbill.app.fileprovider/item_photos/item-123.jpg"
        val testPhoto = "content://com.quickbill.app.qa.fileprovider/item_photos/item-456.jpg"

        assertEquals("item-123.jpg", appOwnedFavouritePhotoName(productionPhoto, "com.quickbill.app"))
        assertEquals("item-456.jpg", appOwnedFavouritePhotoName(testPhoto, "com.quickbill.app.qa"))
        assertNull(appOwnedFavouritePhotoName(testPhoto, "com.quickbill.app"))
    }

    @Test
    fun galleryAndOtherSharedFilesCannotBeDeleted() {
        val externalUris = listOf(
            null,
            "content://media/external/images/media/7",
            "content://com.android.providers.media.documents/document/image%3A82",
            "file:///sdcard/DCIM/photo.jpg",
            "content://com.quickbill.app.fileprovider/shared_pdfs/QuickBill-3.pdf",
            "content://com.quickbill.app.fileprovider/item_photos_backup/item-123.jpg",
        )

        externalUris.forEach { uri -> assertNull(uri, appOwnedFavouritePhotoName(uri, "com.quickbill.app")) }
    }

    @Test
    fun encodedAndNoncanonicalPathsAreNeverDeleted() {
        val provider = "content://com.quickbill.app.fileprovider"
        val paths = listOf(
            "/item_photos/../item-123.jpg",
            "/item_photos/%2e%2e%2fitem-123.jpg",
            "/item_photos/subfolder/item-123.jpg",
            "/item_photos/item-123.jpg?other=1",
            "/item_photos/item-123.jpg#fragment",
        )

        paths.forEach { path -> assertNull(path, appOwnedFavouritePhotoName(provider + path, "com.quickbill.app")) }
    }
}
