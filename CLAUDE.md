# CLAUDE.md

Fridge Inventory: an offline Android app (Kotlin, Jetpack Compose, Room) for tracking food in
the fridge, freezer and pantry, entered by hand or by photographing a grocery receipt
(on-device ML Kit OCR plus store-specific parsers). Single module `:app`, package
`com.sharawang.fridge`.

## Build and test

Requirements: JDK 21 (Robolectric needs it for API 35; bytecode still targets 17), Android
SDK with `platforms;android-35` and `build-tools;35.0.0` (AGP 8.5.2 also wants
`build-tools;34.0.0` by default). The SDK path comes from `ANDROID_HOME` or `sdk.dir` in `local.properties`, which is gitignored.

```bash
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # all JVM tests, including Robolectric DB/migration tests
./gradlew testDebugUnitTest --tests 'com.sharawang.fridge.receipt.ParserTest'
./gradlew lintDebug            # Android Lint, blocking in CI
./gradlew ktlintCheck          # style; blocking in CI (rules in .editorconfig)
./gradlew ktlintFormat
./gradlew connectedCheck       # the single instrumented Compose test; needs a device
```

- The Gradle wrapper is committed and pinned to 8.9
  (`gradle/wrapper/gradle-wrapper.properties`). CI (`.github/workflows/ci.yml`) runs it on
  JDK 21 and fails if the SDK cannot be installed.
- Debug builds are signed with one shared key so updates install over the old copy. It
  comes from `keystore.properties` at the repo root (gitignored; `storeFile`,
  `storePassword`, `keyAlias`, `keyPassword`) or, in CI, from the `FRIDGE_KEYSTORE_BASE64`
  and `FRIDGE_KEYSTORE_PASSWORD` secrets. Without either it falls back to the default debug
  key. Never commit the keystore or its password.
- Dependency versions live only in `gradle/libs.versions.toml`. Add new libraries there and
  reference them as `libs.*`.
- Network: Google Maven (`maven.google.com`) redirects to `dl.google.com`, so that host has
  to be reachable for both the SDK and the AGP/AndroidX/ML Kit dependencies.

## Web version (`web/`)

A no-build PWA for iPhone: plain ES modules, IndexedDB, offline service worker, published to
GitHub Pages by `.github/workflows/pages.yml`. See `web/README.md`.

```bash
cd web && node --test tests/*.test.js   # also runs in CI
cd web && python3 -m http.server 8000
```

- `web/js/parsers.js`, `shelf-life.js`, `repo.js`, `backup.js` and `receipt-text.js` port the
  Kotlin `receipt/parser/`, `ShelfLife`, `InventoryRepository`/`WasteReport`,
  `InventoryBackup` and `ReceiptTextExport`. Keep both sides in step: a parser fix ships on
  both with the fixture added to both `SampleReceipts.kt` and `web/tests/sample-receipts.js`.
  The backup JSON format is shared; `web/tests/fixtures/android-backup.json` came from the
  Android encoder.
- The whole state is one IndexedDB record written in a single put per change (atomic).
- New app files go in `sw.js` `FILES` (a test enforces it); bump `VERSION` on every change.
- UI strings live in `web/js/i18n.js` (en + zh), mirroring the Android string resources.

## Layout (`app/src/main/java/com/sharawang/fridge/`)

| Path | What lives there |
|---|---|
| `FridgeApplication`, `AppContainer` | App entry; hand-rolled DI container (no Hilt). Everything is built here once. |
| `MainActivity` | Single activity. Compose `NavHost` with string routes: `inventory`, `edit/{itemId}` (0 = new), `history`, `labels`, `settings`, `scan`. |
| `data/local/` | Room: `AppDatabase` (version 3), entities `FoodItem`, `Purchase`/`PurchaseLine`, `ConsumptionEvent`, `Daos.kt`, `Enums.kt`, `Converters.kt`, `Migrations.kt` (`ALL_MIGRATIONS`). |
| `data/repo/` | `InventoryRepository`: the only entry point to the DB (merge-on-add, consumption log, undo, receipt commit). `WasteReport` aggregation. |
| `data/backup/` | JSON export/import (`format: "fridge-inventory"`, versioned, hand-written with `org.json`). |
| `data/labels/` | Printable A4 zone labels: layout (`LabelSheet`), vector PDF (`LabelPdf`), palette, strings. |
| `data/ShelfLife.kt` | Keyword table that guesses category and storage area from an item name. |
| `data/Settings.kt` | DataStore-backed reminder preferences (all off by default). |
| `notify/` | WorkManager daily expiry check, notification, scheduling. |
| `receipt/` | OCR (`ReceiptOcr`), `TextLayout` (OCR blocks to rows), `ReceiptScanner` (OCR, then detect, then parse; `reparse(rawText)`), image store. |
| `receipt/parser/` | `ReceiptParser` interface, `LineBasedParser` base, one subclass per store, `Parsers.all` registry. |
| `ui/<screen>/` | One package per screen: `XxxScreen.kt` (composables) and `XxxViewModel.kt` (state, plus a `factory(container)`). `ui/theme/` holds the Morandi palette. |

Tests are in `app/src/test/` (JVM, plus Robolectric for DB/repo/backup/migration, all
pinned to `@Config(sdk = [35])`). `app/src/androidTest/` holds one Compose smoke test.
`app/src/test/resources/robolectric.properties` swaps in a bare `Application` because the
real one starts ML Kit, which cannot run under Robolectric.

## Conventions (see CONTRIBUTING.md)

- **No network.** There is no `INTERNET` permission and it should stay that way. Anything
  network-based must be opt-in, off by default, and discussed first.
- **Enum names are database values.** `FoodCategory`, `StorageArea`, `Store` and
  `FinishReason` are persisted by constant name, in Room and in backups. Renaming or removing
  a constant is a schema change: bump the `AppDatabase` version, add a `Migration` to
  `ALL_MIGRATIONS`, and add a case to `MigrationTest`. Hand-written migration SQL must match
  Room's generated schema exactly. Never use `fallbackToDestructiveMigration`.
  `MigrationSchemaTest` opens a real v2 file with Room so its schema check runs in CI; add
  the new version's starting DDL there when you add a migration. Room exports schema JSON to
  `app/schemas/` on every build: commit it.
- **Money is `Int` cents, calendar days are `LocalDate`.** No `Double` money and no `Long`
  timestamps for dates. Quantity is a bare `Double` count; `FoodItem.unit` is vestigial.
- **Derived values go in extension functions** (`isFinished`, `daysLeft`), not in getters
  on `@Entity` classes.
- **Parsers:** each store is a `LineBasedParser` subclass that overrides only
  `extraNoise`, `extraAbbreviations` and `stripCodes` as needed. Register it in `Parsers.all`
  *above* `GenericParser`. Every parser change ships with a raw-OCR fixture in
  `SampleReceipts` and an assertion in `ParserTest`. Don't put store-specific hacks in the
  base class.
- **Keep Android out of pure logic.** `TextLayout`, `ShelfLife` and `receipt/parser/` are
  plain Kotlin so they test on the JVM without Robolectric.
- **Business logic stays out of composables.** It belongs in a ViewModel or the repository.
- **Quiet defaults.** Expiry is optional and starts empty, reminders are off, and the
  notification permission is requested only when reminders are turned on.
- **History** shows the whole consumption log (no cap), grouped into Monday-start weeks by
  `occurredOn`, filterable to eaten or thrown out. Grouping and filtering live in
  `HistoryViewModel`; entries with a null reason only appear under "all".
- **List −/+ are events.** They write a `ConsumptionEvent` or move the purchase date. In
  the editor the same buttons are corrections that write no history. Undo deletes the event;
  it never logs a compensating one.
- **UI strings** go in `res/values/strings.xml` *and* `res/values-zh/strings.xml`. Store
  names are proper nouns and stay untranslated.
- **Style:** Kotlin official style, 4-space indent, 100-column soft wrap. Comments explain
  *why*, not *what*.
