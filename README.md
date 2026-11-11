# Quick Bill

An offline Android app for small shops in Nepal. It uses Kotlin, Jetpack Compose, a ViewModel, and Room.

## Run the app

Open this folder in Android Studio with JDK 17 and Android SDK 36 installed. Sync Gradle, then run the `app` configuration on a device or emulator running Android 6.0 or newer. The first build downloads dependencies.

On Windows, you can also run `.\gradlew.bat :app:assembleDebug` from this folder with the Android SDK configured. The debug APK builds to `app/build/outputs/apk/debug/app-debug.apk`.

## Make a bill

1. Open shop settings and save your shop name and nine-digit PAN once.
2. In **Favourites**, add each item with its name and price in rupees. Press and hold a tile to edit it, delete it, or manage its photo.
3. Tap tiles to add items, then open **View bill** to adjust quantities. For an amount-only bill, open **Calculator** and tap **+** between amounts. A single amount prints as an express bill.
4. Tap **Print bill** to save and print the bill. Use **History** to reprint a saved bill without recording another sale.

Room stores favourites, shop settings, and numbered bills on the device. Completed bills move the most frequently sold favourites to the top.

## Set up a thermal printer

1. Pair a 58 mm Classic Bluetooth ESC/POS printer in Android settings. Quick Bill uses the Serial Port Profile and a 384-dot print width.
2. Open **Shop → Thermal printer → Print test receipt**. On Android 12 and later, allow **Nearby devices** when asked. Choose the paired printer if prompted.
3. Check the test receipt for clear English and Nepali text. The selection is saved on the device.

**Print bill** and **Reprint** send the receipt directly to the selected printer. If a send fails, the bill remains in **History**. Choose **Use Android print sheet** in printer settings to switch to Android printing, which also offers **Save as PDF**.

## Run unit tests

On Windows, run `.\gradlew.bat :app:testDebugUnitTest`.
