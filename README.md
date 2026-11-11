# Quick Bill

An offline Android app for small shops in Nepal. It uses Kotlin, Jetpack Compose, a ViewModel, and Room.

## Run the app

Open this folder in Android Studio with JDK 17 and Android SDK 36 installed. Sync Gradle, then run the `app` configuration on a device or emulator running Android 6.0 or newer. The first build downloads dependencies.

On Windows, you can also run `.\gradlew.bat :app:assembleDebug` from this folder with the Android SDK configured. The debug APK builds to `app/build/outputs/apk/debug/app-debug.apk`.

## Make a bill

1. Open shop settings and save your shop name and nine-digit PAN once.
2. Add each favourite with its name and price in rupees. To attach a photo, press and hold its tile.
3. Tap tiles to add items. Open **View bill** to adjust quantities, or print directly from Home.
4. Tap **Print bill** to save the bill and open the Android print sheet. Use **History** to reprint a saved bill without recording another sale.

Room stores favourites, shop settings, and numbered bills on the device. Completed bills move the most frequently sold favourites to the top. Use a compatible Android print service for a physical printer, or choose **Save as PDF** from the print sheet.

This first milestone covers Home and item billing. Calculator and Express are later milestones.
