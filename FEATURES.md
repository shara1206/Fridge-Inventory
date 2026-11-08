# Feature parity: Android and web

The app ships twice: the Android app (`app/`) and the iPhone web app (`web/`). They are
separate code, so a feature only exists where it was built. **Every change that adds,
removes or changes a feature updates both, in the same pull request.** When one side cannot
or should not have it, the change updates this table instead, with the reason.

✅ = has it · ❌ = does not · ⏳ = planned

| Feature | Android | Web | Notes |
|---|---|---|---|
| **Inventory** | | | |
| List sorted by expiry, undated last | ✅ | ✅ | |
| Filter by storage area and by category (only categories present) | ✅ | ✅ | |
| Filter by store (hidden below two stores) | ✅ | ✅ | |
| Search by name | ✅ | ✅ | |
| Badge only within 7 days of expiry or past it | ✅ | ✅ | `DUE_SOON_DAYS` on both |
| − logs "ate one"; + is "bought another" and moves the purchase date | ✅ | ✅ | |
| ✓ eaten / bin thrown out, both logged, with undo | ✅ | ✅ | |
| Repeat purchases merge into the existing row | ✅ | ✅ | Same name and storage area |
| **Item editor** | | | |
| Guess category and area from the name | ✅ | ✅ | Same keyword table |
| Expiry optional and empty by default; 2 week / 1 month / 3 month shortcuts | ✅ | ✅ | |
| −/+ in the editor correct the count and log nothing | ✅ | ✅ | |
| Use part of an item (0.25 / 0.5 / 1), logged | ✅ | ✅ | |
| Delete without logging (for mistakes) | ✅ | ✅ | |
| **Receipts** | | | |
| Photograph a receipt, on-device OCR | ✅ | ❌ | Web pages have no on-device OCR; web uses iOS Live Text copy and paste |
| Store parsers: H-Mart, T&T, Trader Joe's, Whole Foods, generic | ✅ | ✅ | Same code and fixtures |
| Costco and PCC parsers | ⏳ | ⏳ | Waiting on real receipt text |
| Review lines before adding (keep, rename, storage area) | ✅ | ✅ | |
| Share or export the receipt's OCR text | ✅ | ✅ | Same text format |
| **History** | | | |
| Whole consumption log, no cap | ✅ | ✅ | |
| Grouped by Monday-start week with eaten / thrown subtotals | ✅ | ✅ | |
| Filter: all / eaten / thrown out | ✅ | ✅ | |
| Waste summary for the last 30 days | ✅ | ✅ | |
| Undo any single entry | ✅ | ✅ | |
| **Data** | | | |
| Backup export and import (merge or replace) | ✅ | ✅ | Same JSON format, both directions |
| Reminder banner when there is no backup from the last 14 days | ✅ | ✅ | Uninstalling Android, or clearing Safari data, deletes everything |
| **Other** | | | |
| Daily expiry notification | ✅ | ❌ | Web push needs a server; the app talks to none |
| Printable zone labels (A4, ten cards a page) | ✅ | ✅ | Android writes a PDF; web prints, or saves as PDF from the print screen |
| English and Chinese, with a per-app language choice | ✅ | ✅ | Android 13+: Settings › Apps › Fridge › Language; web: its own Settings |
| Light and dark theme, Morandi palette | ✅ | ✅ | |
| Works offline, no network access | ✅ | ✅ | |
