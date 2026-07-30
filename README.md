# Fridge Inventory

[![CI](https://github.com/USERNAME/fridge-inventory/actions/workflows/ci.yml/badge.svg)](https://github.com/USERNAME/fridge-inventory/actions/workflows/ci.yml)

An Android app for tracking what is actually in the fridge, freezer and pantry — logged
either by typing an item in or by photographing a grocery receipt.

Everything is stored locally in SQLite. The app declares **no `INTERNET` permission**:
receipt OCR runs on-device with a model bundled in the APK, so no photo or shopping
history ever leaves the phone. The only permission it declares at all is
`POST_NOTIFICATIONS`, requested lazily when you switch reminders on.

> Replace `USERNAME` in the badge URL above with your GitHub account once the repo is pushed.

## Status

Working skeleton, developed in the open. The data layer, receipt pipeline and screens are
complete and the parser logic is covered by unit tests running in CI. A Play Store release
is on the roadmap but deliberately not the current focus.

## Features

- Inventory list sorted by what expires soonest, filterable on **two axes**: storage area
  (fridge / freezer / pantry) and shopping-aisle category, plus search
- 13 categories built around how you actually shop — vegetables, fruit, meat, seafood,
  tofu & soy, dairy & eggs, frozen staples, grains & noodles, seasoning, snacks, drinks,
  ready to eat, other. Only categories present in the kitchen appear in the filter row
- English and Chinese UI labels (`values/` and `values-zh/`)
- Manual entry with category, storage area, quantity, unit, store, price and notes
- Expiry dates guessed from the item name via a keyword shelf-life table, always overridable
- Optional daily expiry reminder — **off by default**, and the notification permission is
  only requested if you turn it on
- Two outcomes when something leaves the kitchen — eaten or thrown out — feeding a waste
  report (how much you binned, which categories, which store), with undo on every removal
- Partial use: knock 0.25 / 0.5 / 1 off an item instead of only finishing the whole thing
- Repeat purchases merge into the row that is already there rather than piling up duplicates
- Receipt scanning: photograph or pick an image, review the parsed lines, uncheck the junk, add the rest
- Store-aware receipt parsers for **H-Mart**, **T&T**, **Trader Joe's** and **Whole Foods**, plus a generic fallback
- Raw OCR text kept on every receipt so a bad parse can be replayed against an improved parser
- Morandi palette: a clean beige base with blue-led accents, no dynamic colour

## Getting started

```bash
git clone <your-repo-url>
cd fridge-inventory
```

Open the folder in Android Studio (Ladybug or newer) and let it sync. The Gradle wrapper
JAR is intentionally not committed — Android Studio regenerates it on first sync, or run
`gradle wrapper` once if you have Gradle installed.

```bash
./gradlew testDebugUnitTest   # parsers, shelf life, database, migrations — all on the JVM
./gradlew lintDebug
./gradlew ktlintCheck         # style, non-blocking until the baseline is clean
./gradlew connectedCheck      # the one instrumented Compose test (needs a device)
./gradlew installDebug
```

Every push and pull request runs the unit tests, Android Lint and a debug build via
[GitHub Actions](.github/workflows/ci.yml); the debug APK and both reports are uploaded as
build artifacts. The workflow provisions its own Gradle, so it works whether or not
`gradle-wrapper.jar` has been committed.

Requires JDK 17, `compileSdk` 35, `minSdk` 26 (API 26 for `java.time` without desugaring).

## How the receipt pipeline works

```
photo ──► ReceiptOcr ──► TextLayout.toRows ──► Parsers.forText ──► LineBasedParser.parse
          (ML Kit,        (stitch OCR           (detect store       (noise filter,
           on-device)      fragments into        from keywords)      price extraction,
                           physical rows)                            name cleanup)
                                    │
                                    ▼
                          ScanScreen review list  ──►  InventoryRepository.commitReceipt
                          (edit names, uncheck)         (Purchase + PurchaseLine + FoodItems)
```

Two details do most of the work:

**Row stitching.** OCR returns a product name and its price as separate fragments because
a wide gap separates the receipt's columns. `TextLayout` regroups fragments by vertical
position before any parsing happens — without this step, prices and names arrive as
unrelated lines and nothing matches.

**Abbreviation expansion.** Receipts print `ORG SPNCH 5OZ`, not `Organic Spinach`. Each
parser merges a store-specific abbreviation table over a shared one, so H-Mart's `BLGGI`
becomes Bulgogi and Whole Foods' `AIRCHL` becomes Air Chilled.

### Adding a store

1. Subclass `LineBasedParser` in `receipt/parser/`, set `store`, implement `matches()`,
   and override `extraNoise` / `extraAbbreviations` / `stripCodes` as needed.
2. Register it in `Parsers.all` **above** `GenericParser`.
3. Paste real OCR text into `SampleReceipts` in the test sources and assert on it.

### When a parse goes wrong

The full OCR text is stored on the `Purchase` row. Copy it into `SampleReceipts`, write a
failing test, then fix the parser. `ReceiptScanner.reparse(rawText)` re-runs a stored
receipt through the current parsers without needing the original photo.

There is an issue template for exactly this — see
[Receipt parsed wrong](.github/ISSUE_TEMPLATE/receipt-parse.yml). More detail in
[CONTRIBUTING.md](CONTRIBUTING.md).

## Project layout

```
app/src/main/java/com/sharawang/fridge/
├── AppContainer.kt          hand-rolled DI (swap for Hilt if this grows)
├── FridgeApplication.kt
├── MainActivity.kt          Compose NavHost: inventory / edit / scan
├── data/
│   ├── ShelfLife.kt         keyword → category + storage area + days
│   ├── local/               Room entities, DAOs, database, converters
│   └── repo/                InventoryRepository — the only door to the database
├── receipt/
│   ├── ReceiptOcr.kt        ML Kit text recognition
│   ├── TextLayout.kt        OCR fragments → physical rows (pure Kotlin, unit tested)
│   ├── ReceiptScanner.kt    OCR → store detection → parse
│   ├── ParsedReceipt.kt
│   └── parser/              one parser per store + generic fallback
└── ui/
    ├── inventory/           list, filters, search, mark-as-used
    ├── edit/                add / edit form
    ├── scan/                capture and review
    └── theme/
```

## Testing

Everything except one Compose smoke test runs on the JVM, so CI covers the risky parts with
no emulator:

| Suite | Covers |
|-------|--------|
| `ParserTest` | Four stores' receipt fixtures + the generic fallback |
| `TextLayoutTest` | Stitching OCR fragments back into receipt rows |
| `ShelfLifeTest` | Category / storage / expiry guessing, including the frozen override |
| `ExpirySummaryTest` | What the reminder says, and when it says nothing |
| `ReminderSchedulerTest` | Next-occurrence timing, clamping, never-zero delay |
| `WasteReportTest` | Eaten vs binned, money, ranking, legacy rows with no reason |
| `FoodItemDaoTest` | Room queries under Robolectric — ordering, filters, restore |
| `InventoryRepositoryTest` | Merging, partial use, receipt commit, waste aggregation |
| `MigrationTest` | A hand-built v1 database run through the real migration |
| `InventoryScreenTest` | Instrumented: the screen composes and renders a row |

`MigrationTest` builds the old schema with raw SQL instead of using Room's
`MigrationTestHelper`, which needs exported schema JSON from a previous build. That means the
migration is covered on a clean checkout, which matters because migrations are the only bug
class here that destroys the user's data.

## Palette

Neutrals sit at a yellow hue (roughly 45°) and never at a red one — a beige with red in it
reads as pink as soon as it goes pale, which is the one thing this palette must not do. The
status accents are kept on the brown side of orange for the same reason, so their pale fills
land on tan rather than peach.

Material You dynamic colour is off by default: it would pull everything toward the user's
wallpaper and wash the palette out. `FridgeTheme(dynamicColor = true)` turns it on if that
trade ever stops mattering.

## Categories and shelf life

`StorageArea` (where it lives) and `FoodCategory` (which aisle it came from) are deliberately
independent: frozen shrimp is still seafood, it just keeps for months. `ShelfLife` picks the
category from the longest matching keyword, then a separate "frozen" override moves the item
to the freezer and extends its life — so `Frozen Blueberries` lands in fruit + freezer rather
than being mislabelled as a frozen staple.

Enum *names* are the persisted database values, so renaming a `FoodCategory` constant is a
schema change. `MIGRATION_1_2` is exactly that: it remaps the old coarse categories
(`PRODUCE`, `MEAT_SEAFOOD`, `FROZEN`, `DRY_GOODS`), parks anything unrecognised in `OTHER`
rather than letting the enum converter crash on read, and adds the `finishedReason` column.
There is no `fallbackToDestructiveMigration` — a missing migration should fail loudly in
development, not silently wipe someone's kitchen.

## Data model

| Table            | Purpose |
|------------------|---------|
| `food_items`     | One row per thing in the kitchen. `finishedOn IS NULL` = still here. |
| `purchases`      | One shopping trip, including the raw OCR text and photo path. |
| `purchase_lines` | Every parsed receipt line, accepted or not — the parser's audit trail. |

Money is stored as integer cents. Dates are `LocalDate` stored as ISO strings.
`finishedReason` distinguishes eaten from thrown out — without it a waste report could only
report that food vanished.

## Roadmap

**Done**

- [x] CI: unit tests, ktlint, Android Lint and a debug build on every push
- [x] Expiry notifications via WorkManager, opt-in and off by default
- [x] Filtering on three axes (storage area, category, store), bilingual labels
- [x] Room migrations, with the migration itself under test
- [x] Undo on every removal, plus a history screen and waste report
- [x] Partial use, and duplicate merging on both manual entry and receipt import
- [x] Receipt photos pruned after 30 days on app start
- [x] Per-line storage area override on the receipt review screen
- [x] Input validation on quantity and price
- [x] Database, repository and migration tests on the JVM; one instrumented Compose test

**Next**

- [ ] JSON export / import for backups
- [ ] Shopping list generated from what is running low
- [ ] Clean the ktlint baseline, then flip `ignoreFailures` to false
- [ ] Widen the instrumented suite past the single smoke test

**Parked until a release is actually on the table**

- [ ] App icon and screenshots
- [ ] Release signing config reading from `keystore.properties` (kept out of git)
- [ ] Privacy policy stating that no data leaves the device (Play requires one even for offline apps)
- [ ] Play Data Safety form: no collection, no sharing

**Needs a decision first**

- [ ] Barcode scanning — offline scanning only yields the barcode digits; resolving them to
      product names needs a network lookup, which breaks the no-`INTERNET` design
- [ ] Optional cloud LLM pass for abbreviations the tables miss — same trade-off, would have
      to be strictly opt-in and off by default

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Short version: no network, and every parser change
ships with a receipt fixture.

## License

MIT — see [LICENSE](LICENSE).
