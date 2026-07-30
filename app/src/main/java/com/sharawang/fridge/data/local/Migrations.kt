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
 * Every migration ever written, in order. Room needs the whole chain so a device two
 * versions behind can still upgrade in a single pass.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
