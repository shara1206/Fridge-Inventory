package com.sharawang.fridge.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Enum columns are stored by constant *name*, which makes renaming a [FoodCategory] a schema
 * change. Version 2 split the original coarse categories into shopping-aisle ones:
 *
 *   PRODUCE       -> VEGETABLES      (v1 data cannot tell an apple from a cabbage, so
 *                                     everything lands in VEGETABLES and the user re-files
 *                                     the fruit)
 *   MEAT_SEAFOOD  -> MEAT            (same problem: seafood is indistinguishable in v1)
 *   FROZEN        -> FROZEN_STAPLES
 *   DRY_GOODS     -> GRAINS_NOODLES
 *
 * It also adds `finishedReason`, so the waste report can tell eaten from thrown out.
 *
 * Losing precision here is acceptable because the alternative is losing the row. The lesson
 * for later versions: do not encode a taxonomy you expect to refine as a bare enum name
 * without keeping the original text alongside it.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Nullable with no default, so existing rows simply have no recorded reason.
        db.execSQL("ALTER TABLE food_items ADD COLUMN finishedReason TEXT")

        db.execSQL(
            """
            UPDATE food_items SET category = CASE category
                WHEN 'PRODUCE'      THEN 'VEGETABLES'
                WHEN 'MEAT_SEAFOOD' THEN 'MEAT'
                WHEN 'FROZEN'       THEN 'FROZEN_STAPLES'
                WHEN 'DRY_GOODS'    THEN 'GRAINS_NOODLES'
                ELSE category
            END
            """
        )
        // Anything the CASE above did not recognise would crash the enum converter on read,
        // so park it in OTHER rather than let the app fail to open.
        db.execSQL(
            """
            UPDATE food_items SET category = 'OTHER' WHERE category NOT IN (
                'VEGETABLES','FRUIT','MEAT','SEAFOOD','TOFU_SOY','DAIRY_EGGS',
                'FROZEN_STAPLES','GRAINS_NOODLES','SEASONING','SNACKS','BEVERAGE',
                'PREPARED','OTHER'
            )
            """
        )
    }
}

/**
 * Version 3 adds `consumption_events`: the log of what actually left the kitchen.
 *
 * Until now the only record was `finishedOn` / `finishedReason` on the item row, which can
 * only describe a row that is gone. Eating two of three eggs wrote nothing, so history and
 * the waste report were blind to every partial use — the common case.
 *
 * The table is backfilled from rows that are already finished so the history screen is not
 * empty on the first launch after upgrading. Those rows carry `recordedAt = 0` (no clock
 * time was ever recorded for them) and keep a null reason where the old row had none, so a
 * pre-v2 finish stays "unknown" instead of being counted as eaten.
 *
 * The backfill's quantity is the quantity the row held when it was finished. That is all the
 * old schema knew; any amount used before that point is genuinely unrecoverable.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Written to match Room's generated schema exactly — a column type or nullability
        // that differs here fails validation on open, on the user's phone, at launch.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `consumption_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `itemId` INTEGER,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `storageArea` TEXT NOT NULL,
                `store` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `reason` TEXT,
                `occurredOn` TEXT NOT NULL,
                `recordedAt` INTEGER NOT NULL,
                `valueCents` INTEGER
            )
            """
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_consumption_events_occurredOn` " +
                "ON `consumption_events` (`occurredOn`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_consumption_events_itemId` " +
                "ON `consumption_events` (`itemId`)"
        )
        db.execSQL(
            """
            INSERT INTO consumption_events
                (itemId, name, category, storageArea, store, quantity, reason,
                 occurredOn, recordedAt, valueCents)
            SELECT id, name, category, storageArea, store, quantity, finishedReason,
                   finishedOn, 0, priceCents
            FROM food_items
            WHERE finishedOn IS NOT NULL
            """
        )
    }
}

/**
 * Every migration ever written, in order. Room needs the whole chain so a device two
 * versions behind can still upgrade in a single pass.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
