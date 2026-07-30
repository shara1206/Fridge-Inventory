# Contributing

## Setup

Open the repo in Android Studio (Ladybug or newer) and let it sync. Requires JDK 17.

```bash
./gradlew testDebugUnitTest   # parser + shelf-life tests, no device needed
./gradlew lintDebug
./gradlew installDebug        # onto a connected device
```

If `gradle-wrapper.jar` is not in the repo yet, Android Studio generates it on first sync
(or run `gradle wrapper` once). CI works either way.

## Two rules that are not negotiable

**No network.** The app declares no `INTERNET` permission, and that is a feature, not an
oversight — it is what lets the Play listing honestly say "no data collected". Any change
that needs the network has to be opt-in, off by default, and discussed in an issue first.

**Every parser change ships with a fixture.** Receipt parsing is pure string wrangling with
no ground truth other than real receipts. A change that improves your receipt and silently
breaks four others is the default outcome unless the fixtures catch it.

## Fixing a bad receipt parse

1. Get the raw OCR text out of the `purchases` row for that scan (it is stored on purpose).
2. Add it to `SampleReceipts` in `app/src/test/java/com/sharawang/fridge/receipt/`.
3. Write the assertion you *want* in `ParserTest`. Watch it fail.
4. Fix the parser. Run the whole test class — the other stores' fixtures are the regression net.

`ReceiptScanner.reparse(rawText)` re-runs stored text through the current parsers, so you
never need the original photo again.

## Adding a store

1. Subclass `LineBasedParser` in `receipt/parser/`. Set `store`, implement `matches()`, and
   override `extraNoise` / `extraAbbreviations` / `stripCodes` only as far as you need to.
2. Register it in `Parsers.all` **above** `GenericParser` — the generic parser matches
   everything, so anything below it is dead code.
3. Add a fixture and tests as above.

Resist putting store-specific hacks in `LineBasedParser`. The base class is for the shape
every receipt shares; the differences belong in the subclass.

## Two more things that will bite you

**Enum names are database values.** `FoodCategory`, `StorageArea`, `Store` and `FinishReason`
are persisted by constant name. Renaming one is a schema change: bump the version, write a
migration, and add a case to `MigrationTest`.

**Money is integer cents, dates are `LocalDate`.** Never introduce a `Double` for money or a
`Long` timestamp for a calendar day; both have already been decided and mixing conventions is
how off-by-one-day and off-by-one-cent bugs get in.

## Style

- Kotlin official style (the default in Android Studio); 4-space indent, 100-column soft wrap.
- Comments explain *why*, not *what*. The code already says what.
- Business logic stays out of composables — put it in a ViewModel or the repository so it
  can be tested on the JVM.
- Keep new Android dependencies out of `TextLayout`, `ShelfLife` and `receipt/parser/`:
  those are plain Kotlin so they stay unit-testable without Robolectric.
