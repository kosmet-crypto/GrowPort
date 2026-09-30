# GrowPort – notes for Claude

Personal Android app (beta, in daily use by the owner) for tracking funds, Bitcoin and stocks/ETFs,
and comparing them with a hypothetical savings account, Bitcoin and inflation.

- **Talk to the user in Serbian (Cyrillic).** Code, comments and commit messages stay in English.
- `SPEC.md` (Serbian) is the agreed design: formulas, data model, screens, phases. When a change
  alters behaviour described there, update `SPEC.md` in the same commit.
- `README.md` is the user-facing overview (English).

## Layout

| Path | What |
|---|---|
| `index.html` | The whole app: HTML, CSS and JS in one file, no build step, no dependencies |
| `android/` | Small WebView wrapper (Java), copies `index.html` and `pdfjs/` into the APK at build time |
| `pdfjs/` | Bundled Mozilla pdf.js for reading PDF statements offline (see `pdfjs/README.md`); only an APK update delivers changes here |
| `android/app/src/main/java/app/growport/MainActivity.java` | WebView + `GrowPortAndroid` JS bridge (`http`, `saveFile`, `checkForUpdate`, …) |
| `.../WebUpdater.java` | Downloads the latest `index.html` from `main`, falls back to the bundled page if it fails |
| `.../ApkInstaller.java` | In-app APK update (sideload flavor only) |
| `.github/workflows/android.yml` | Builds both APKs; on `main` publishes a GitHub Release `v0.1.<run>` |

### Inside `index.html` (one `<script>`, sections marked `/* ===== name ===== */`)
- `L` – translations, each key is `[English, Serbian Cyrillic, Norwegian]`. Every new UI string
  needs all three; use `t('key', {vars})`.
- `D` – user data (JSON, `schema: 1`, see SPEC §3), saved in `localStorage` (`growport.data.v1`).
  `C` – market data cache (not in backup). `UI` – view state.
- Market data: `updateFx` (Frankfurter/ECB), `updateBtc` (Binance, Coinbase fallback),
  `fetchTicker` (Yahoo), `updateCpi` (SSB). Requests go through `http()`, which uses the Android
  bridge when present (avoids CORS).
- `buildEngine()` – all calculations (fund deposits from value − return, savings/BTC/inflation
  simulation, aggregation). `metrics`, `xirr`, `maxDrawdown` – statistics.
- `drawChart` – hand-written SVG chart. `view*()` functions render the screens.

## Rules that are easy to break

- **Merging to `main` ships to the owner's phone.** The app downloads the new `index.html` on the
  next start, so a broken page on `main` breaks the beta. Work on the session branch; test before merge.
- **Data compatibility:** existing users' `localStorage` data and JSON backups must keep loading.
  Add fields with defaults in `normalize()`; if the shape changes incompatibly, bump `schema` and migrate.
- **Native API version:** when the page starts using a new `GrowPortAndroid` method, raise
  `<meta name="growport-native-api">` in `index.html` **and** `WebUpdater.NATIVE_API`, so older APKs
  keep their bundled page until they update.
- Keep `sideload` and `play` flavors working (`BuildConfig.SELF_UPDATE`); Play must never self-update.
- `targetSdk` stays 34 (35 forces edge-to-edge, the page does not pad for system bars).
- Privacy: imported files (PDF/CSV) are parsed on the device and never stored or sent; only the
  parsed transactions are saved. Don't add network calls that carry user data.
- The sideload keystore in the repo is intentional (every CI build must update the installed app).

## Checking changes

- Web: open `index.html` in a browser (Playwright/Chromium is available in cloud sessions). Some
  market sources block desktop browsers (CORS); that is expected, the app routes them natively.
  Settings has a demo-data option useful for testing.
- Quick syntax check of the script:
  `sed -n '/<script>/,/<\/script>/p' index.html | sed '1d;$d' > /tmp/gp.js && node --check /tmp/gp.js`
- Android: `cd android && ./gradlew assembleSideloadRelease` (needs Android SDK; CI builds every
  push to `claude/**` branches, so a push is also a build check).
