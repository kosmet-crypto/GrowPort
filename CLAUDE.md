# GrowPort – notes for Claude

Personal Android app for tracking funds and investments and comparing them with a savings
account, Bitcoin and inflation. The agreed design is in `SPEC.md` (Serbian); keep it in sync
when behaviour changes.

## Working with the owner
- Talk to the owner in **Serbian, Cyrillic script**. Code, comments and commit messages stay in English.
- New features: **discuss first**. Give an honest critical opinion (say when an idea is impractical
  or not feasible), ask what is unclear, and write code only after the owner explicitly says to
  start (e.g. „сад крени да пишеш код“ / „крени“). Small fixes the owner asks for directly can be done right away.
- The owner tests on an Android phone from the release APK; explain steps simply (links, which button to press).

## Layout
- `index.html` – the whole app: HTML, CSS and vanilla JavaScript in one file, no build step,
  no external libraries, fonts or CDNs (the APK bundles it and must work offline).
- `android/` – small WebView wrapper (package `app.growport`), Java.
  - `MainActivity` – hosts the page, `GrowPortAndroid` JS bridge (`http`, `saveFile`, `ready`,
    `getVersion`, `canSelfUpdate`, `checkForUpdate`).
  - `WebUpdater` – over-the-air page updates (downloads `index.html` from `main`).
  - `ApkInstaller` – installs a newer release APK from inside the app.
- `.github/workflows/android.yml` – builds both flavors on every push; on `main` publishes
  `growport.apk` as a GitHub Release tagged `v0.1.<run number>` (the updater relies on that tag format).
- `SPEC.md` – agreed product spec; `README.md` – user-facing install/update notes.

## Two builds (product flavors)
- `sideload` – the APK from Releases; `BuildConfig.SELF_UPDATE = true`, has `REQUEST_INSTALL_PACKAGES`
  (only in `src/sideload/AndroidManifest.xml`).
- `play` – for Google Play later; no self-updating of any kind (Play policy forbids apps from
  downloading code or updating themselves). Keep every update code path behind `SELF_UPDATE`.
- The sideload signing key (`android/app/growport.keystore`) is committed on purpose so every CI
  build can update the installed app. Never use it for Play; Play gets its own upload key outside the repo.

## Rules that keep updates working
- Installed apps accept a downloaded page only if it contains `id="gp-app"`, `</html>`, is at least
  20 000 bytes, and its `<meta name="growport-native-api">` is ≤ `WebUpdater.NATIVE_API`.
  Keep the `gp-app` element and the meta tag.
- When the page starts calling a **new** `GrowPortAndroid` method, raise both the meta value and
  `WebUpdater.NATIVE_API`, so older APKs keep their page until they update.
- The page must call `GrowPortAndroid.ready()` after its first render, otherwise the app rolls back
  to the bundled page after 15 s.
- Stored data (`localStorage`, key `growport.data.v1`) must survive updates. If its shape changes,
  migrate it in `normalize()` (bump `schema`), never drop user data. Backups use the same JSON,
  so `normalize()` must also accept older backups.

## App conventions (index.html)
- Every user-visible string goes through `t(key)`; add each new key to `L` in **all three**
  languages: English, Serbian (Cyrillic), Norwegian Bokmål.
- Money is shown in the main currency (`settings.baseCurrency`, default NOK); historical points use
  the exchange rate of that day (`fx(cur, n)` in the engine). Format with `fmtMoney`/`fmtPct`, and
  put numbers into inputs with `fmtInput` (read back with `parseNum`, which accepts `,` and `.`).
- Dates are `YYYY-MM-DD` strings; the engine works with day numbers (`dnum`/`dstr`).
- Engine (`buildEngine`): per-holding daily series `act` (real value), `inv` (invested), `sav`
  (hypothetical bank), `btc` (hypothetical Bitcoin), `inf` (inflation). Funds derive deposits from
  value − return; comparison starts at the first entry with a return (variant B in SPEC 4.2);
  savings interest accrues daily and is credited on the 1st; rate changes apply from their month on.
- Chart colors come from the validated palette in the CSS tokens (`--s1`..`--s4`, light and dark);
  keep text in text colors, and good/bad deltas always with an arrow, not color alone.
- Market data sources (no API keys): Frankfurter (ECB rates), Binance / Coinbase (BTC),
  Yahoo Finance (stocks/ETFs), SSB table 03013 (KPI). In the app all requests go through the
  native `http` bridge; results are cached in `growport.cache.v1`, so the app works offline.

## Checking changes
- There is no test suite. Open `index.html` in headless Chromium (Playwright is preinstalled,
  browsers in `/opt/pw-browsers`) with a synthetic market-data cache in `localStorage`, press
  "Load example data", check the console for errors and look at screenshots in light/dark and
  in all three languages.
- The cloud sandbox usually cannot reach the market-data hosts or the Android SDK; the APK is
  built and verified by GitHub Actions – check that the workflow run is green after pushing.
