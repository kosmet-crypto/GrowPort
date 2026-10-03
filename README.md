# GrowPort

Personal Android app for tracking funds and investments, and comparing them with a savings account, Bitcoin and inflation ("what if I had put the same money there?").

See [SPEC.md](SPEC.md) for the agreed design (in Serbian).

## Features
* Holdings grouped in accounts: funds (value + return entered by hand, or purchases with units and price), Bitcoin you own, stocks/ETFs (live price).
* A holdings statement from the platform (units, market value, cost price) updates each fund's value on the price date, checks the units against the imported purchases and shows the platform's cost price and unrealised gain.
* Funds with purchases can follow a daily price from Yahoo Finance; after an import the app looks each fund up and links it only when the price matches the user's own purchase prices.
* Import transactions from a bank or fund platform (PDF or CSV statement, or a pasted table); the comparison then starts from the real purchase dates. PDFs are read on the phone with Mozilla pdf.js bundled in the APK (`pdfjs/`); the file is not stored or sent anywhere.
* For every deposit the app simulates the same money going into a savings account (interest credited on the 1st of each month, rate editable per period), into Bitcoin (with a purchase fee) and keeping up with inflation.
* Overview with total value, gain, the difference against bank / Bitcoin / inflation in money and as a ratio, and a main chart (value or difference view).
* Detailed statistics: total and annual return (XIRR), largest drop, by holding, by month.
* Main currency (NOK by default), historical exchange rates on each day.
* English, Serbian (Cyrillic) and Norwegian.
* Everything stays on the phone; backup and restore as a JSON file.

Market data (no API keys): ECB exchange rates via Frankfurter, Bitcoin from Binance (Coinbase as fallback), stocks/ETFs from Yahoo Finance, inflation (KPI) from Statistics Norway.

## Android app (APK)
Every change merged into `main` builds a new APK with GitHub Actions and publishes it as a release.
Always the newest version: https://github.com/kosmet-crypto/GrowPort/releases/latest/download/growport.apk

1. Open the link on your Android phone and download `growport.apk`.
2. Open the file. Android asks to allow installs from your browser or file manager; allow it once.
3. Install. Newer APKs install over the old one and keep your data.

Updates (sideload build):
* **Page updates (most changes):** on every start with internet, the app downloads the latest `index.html` from `main` and uses it from the next start. If a downloaded page fails to start, the app falls back to the version inside the APK.
* **APK updates (Android-side changes):** checked at most twice a day (or with **Check for updates** in Settings) and installed from inside the app with one confirmation.

When the page starts calling a new `GrowPortAndroid` method, raise `<meta name="growport-native-api">` in `index.html` and `WebUpdater.NATIVE_API` in the app, so older apps keep their page until the APK is updated.

### Two builds
* `sideload` – the APK above, updates itself.
* `play` – the same app without self-updating (Google Play does not allow it); Play updates it. CI builds it too (`growport-play.apk` in the workflow artifacts).

The Android project lives in `android/` (a small WebView wrapper). To build locally: `cd android && ./gradlew assembleSideloadRelease`.
The sideload signing key is in the repo so every CI build can update the installed app; Google Play will use a separate upload key kept out of the repo.

## Development
`index.html` is the whole app (HTML, CSS and JavaScript, no build step). Open it in a browser to work on it; in a desktop browser some market data sources block requests (CORS), inside the app they go through the Android side.
