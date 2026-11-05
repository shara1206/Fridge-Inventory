# Fridge Inventory, web version

A home-screen web app for iPhone (or any phone) with the same lists, history and backup
format as the Android app. Plain HTML, CSS and ES modules: no build step, no dependencies,
no network calls. Data lives in the browser's IndexedDB on the phone.

## Use it on iPhone

1. Open the published page in **Safari**.
2. Share button → **Add to Home Screen**. Open it from the icon from then on: iOS keeps a
   Home Screen app's data, but may clear a plain Safari tab's data after weeks unused.
3. Moving from the Android app: export a backup there, open it here under Settings → Import
   → Replace. The JSON format is shared both ways.

Receipts: there is no on-device OCR in a web page, so use iOS Live Text. Open the receipt
photo (or point the Camera at it), touch and hold the text, Select All, Copy, then paste on
the "Add from receipt" screen. The same parsers as Android read it.

Not in the web version: daily expiry notifications (web push needs a server, and this app
talks to none) and the printable zone labels.

## Develop

```bash
cd web
python3 -m http.server 8000      # then open http://localhost:8000
node --test tests/*.test.js      # parsers, shelf life, repository, backup, offline list
```

- `js/parsers.js`, `js/shelf-life.js`, `js/repo.js` and `js/backup.js` are ports of the
  Kotlin code and are tested against the same fixtures (`tests/sample-receipts.js` is
  copied from `SampleReceipts.kt`; `tests/fixtures/android-backup.json` was written by the
  Android encoder). A behaviour change on one side belongs on the other.
- Every app file must be listed in `sw.js` `FILES`, and `VERSION` bumped when anything
  changes, or phones keep serving the old copy. `tests/offline.test.js` checks the list.
- Published by `.github/workflows/pages.yml` on push to `main`.
