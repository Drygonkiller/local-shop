# Quick Bill

Quick Bill is an offline billing app for small shops in Nepal. The native Android app uses Kotlin, Jetpack Compose, and Room. The installable web app runs in Safari on iPhone and Chrome on Android.

## Web app

With Node.js installed, run `node web/serve.mjs` from this folder and open `http://127.0.0.1:4173/`. The web app has no build step. For use on phones, serve the contents of `web/dist` over HTTPS.

To deploy with Vercel, import this GitHub repository and keep the Root Directory at the repository root. The included `vercel.json` serves `web/dist` without a build command. Use the resulting HTTPS URL on your phone.

- **iPhone** · Open the HTTPS link in Safari. Tap **Share → Add to Home Screen**, enable **Open as Web App**, then tap **Add**.
- **Android** · Open the HTTPS link in Chrome and select **Install app** or **Add to Home screen**.
- **Offline** · Open the web app once while connected. A service worker caches the app, and IndexedDB stores that browser's shop, items, bills, customers and payments. The web app uses its own local data, separate from Room and from other devices.
- **Printing and sharing** · Open a saved bill and use **Print bill**, **Share PDF**, or **Share text**. The web app uses the browser's print dialog and offers downloads or clipboard sharing when the system share sheet is unavailable. The native Android app supports direct printing to paired Classic Bluetooth ESC/POS printers.
- **Customer names** · Enter an optional name under **Current bill** to include it on a paid bill. History accepts either a bill number or a customer's name. Udharo bills use the customer selected from Khata.

Run the web tests with `node --test web/tests/*.test.mjs`.

## Run the native Android app

Open this folder in Android Studio with JDK 17 and Android SDK 36 installed. Sync Gradle, then run the `app` configuration on a device or emulator running Android 6.0 or newer. The first build downloads dependencies.

On Windows, you can also run `.\gradlew.bat :app:assembleDebug` from this folder with the Android SDK configured. The debug APK builds to `app/build/outputs/apk/debug/app-debug.apk`.

## Make a bill

1. Open shop settings and save your shop name and nine-digit PAN once.
2. In **Favourites**, add each item with its name and price in rupees. Press and hold a tile to edit it, delete it, or manage its photo.
3. Tap tiles to add items, then open **View bill** to adjust quantities. For an amount-only bill, open **Calculator** and tap **+** between amounts. A single amount prints as an express bill.
4. Tap **Print bill** to save and print the bill. Use **History** to reprint a saved bill without recording another sale.

Room stores favourites, shop settings, and numbered bills on the device. Completed bills move the most frequently sold favourites to the top.

## Daily sales and sharing

Open **Today** to see the Nepal-day sales total, bill count, average bill, top five favourites by sales revenue, and today's bills. Tap a bill to view, share, or reprint it. **Print report** uses the selected thermal printer or Android's print sheet.

**Share** opens Android's share sheet with a plain-text bill. Sharing from **View bill** produces an unnumbered draft. Printing a report, sharing a bill, and reprinting from History leave sales and bill numbers unchanged.

## Bill dates and amounts in words

Receipts, saved bills, shared bills, and daily report dates show the Bikram Sambat date alongside the Gregorian date in Nepal time. Quick Bill includes month lengths for BS 2080–2090, from 14 April 2023 through 13 April 2034. It labels BS 2084–2090 as provisional and shows the Gregorian date outside the table. [Kathmandu Metropolitan City calendar](https://kathmandu.gov.np/en/calendar) · [Published month-length table](https://github.com/KDE/kdeplasma-addons/blob/1445c417500c2a206691d0710005f64dc8efdaee/plasmacalendarplugins/alternatecalendar/provider/nepalicalendar.cpp#L105)

Printed, shared, and saved bills show the total in English words using lakh and crore. Quick Bill derives both displays from the stored timestamp and paisa total.

## Udharo khata

Tap **Udharo** beside **Print bill** and choose or add a customer. Quick Bill saves and prints a credit bill with that customer's name. Open **Khata** to see outstanding balances, record a payment, or open the share sheet with a plain-text payment reminder. The **Today** report includes credit sales in its sales total and shows them on their own line as well.

## Set up a thermal printer

1. Pair a 58 mm Classic Bluetooth ESC/POS printer in Android settings. Quick Bill uses the Serial Port Profile and a 384-dot print width.
2. Open **Shop → Thermal printer → Print test receipt**. On Android 12 and later, allow **Nearby devices** when asked. Choose the paired printer if prompted.
3. Check the test receipt for clear English and Nepali text. The selection is saved on the device.

**Print bill** and **Reprint** send the receipt directly to the selected printer. If a send fails, the bill remains in **History**. Choose **Use Android print sheet** in printer settings to switch to Android printing, which also offers **Save as PDF**.

## Run unit tests

On Windows, run `.\gradlew.bat :app:testDebugUnitTest`.
