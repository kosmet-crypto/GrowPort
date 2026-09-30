# pdf.js (bundled)

Mozilla pdf.js, `pdfjs-dist@6.3.289`, legacy build (`legacy/build/pdf.min.mjs` and
`pdf.worker.min.mjs`), Apache-2.0 (see LICENSE). Used only by the transaction import to read the
text of PDF statements on the phone; nothing is sent anywhere.

The files are renamed to `.js` so the Android asset loader serves them with a JavaScript MIME type
(ES modules are refused as `text/plain`). They are copied into the APK next to `index.html`
(`copyWebApp` in `android/app/build.gradle`); the page falls back to jsDelivr when they are missing
(older APKs, or `index.html` opened on its own).

To update: `npm pack pdfjs-dist@<version>`, copy the two legacy files as above, bump the version
here and in `PDFJS_CDN` in `index.html`.
