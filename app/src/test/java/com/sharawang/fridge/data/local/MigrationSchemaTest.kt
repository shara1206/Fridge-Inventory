package com.sharawang.fridge.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opens a real version 2 database file with Room and lets Room do its own schema check.
 *
 * [MigrationTest] proves the migrations move the data correctly, but it never asks Room
 * whether the result matches the entities. Room does ask, on the user's phone, at launch:
 * a hand-written column type, nullability or index that differs from the generated schema
 * throws "Migration didn't properly handle" and the app cannot open. This test makes that
 * failure happen here instead.
 *
 * The DDL below is what Room generated for version 2. The entities have not changed since
 * then except for the new `consumption_events` table, so it is copied from the current
 * `FoodItem`, `Purchase` and `PurchaseLine`. Do not edit it to match future entity changes:
 * it is a record of what is already installed on devices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationSchemaTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-schema-test.db"

    private val v2Schema = listOf(
        """
        CREATE TABLE IF NOT EXISTS `food_items` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `category` TEXT NOT NULL,
            `storageArea` TEXT NOT NULL,
            `quantity` REAL NOT NULL,
            `unit` TEXT NOT NULL,
            `purchasedOn` TEXT NOT NULL,
            `expiresOn` TEXT,
            `store` TEXT NOT NULL,
            `priceCents` INTEGER,
            `notes` TEXT NOT NULL,
            `finishedOn` TEXT,
            `finishedReason` TEXT,
            `purchaseId` INTEGER
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_food_items_expiresOn` ON `food_items` (`expiresOn`)",
        "CREATE INDEX IF NOT EXISTS `index_food_items_finishedOn` ON `food_items` (`finishedOn`)",
        "CREATE INDEX IF NOT EXISTS `index_food_items_purchaseId` ON `food_items` (`purchaseId`)",
        """
        CREATE TABLE IF NOT EXISTS `purchases` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `store` TEXT NOT NULL,
            `purchasedOn` TEXT NOT NULL,
            `totalCents` INTEGER,
            `rawText` TEXT NOT NULL,
            `imagePath` TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `purchase_lines` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `purchaseId` INTEGER NOT NULL,
            `rawLine` TEXT NOT NULL,
            `parsedName` TEXT NOT NULL,
            `quantity` REAL NOT NULL,
            `priceCents` INTEGER,
            `accepted` INTEGER NOT NULL,
            `foodItemId` INTEGER,
            FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_purchase_lines_purchaseId` " +
            "ON `purchase_lines` (`purchaseId`)"
    )

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun createV2(seed: (SupportSQLiteDatabase) -> Unit) {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v2Schema.forEach { db.execSQL(it.trimIndent()) }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            seed(helper.writableDatabase)
        }
    }

    private fun openWithRoom(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `a version 2 database upgrades into exactly the schema Room expects`() = runTest {
        createV2 { db ->
            db.execSQL(
                """
                INSERT INTO food_items
                    (name, category, storageArea, quantity, unit, purchasedOn, store, notes,
                     finishedOn, finishedReason)
                VALUES ('Spinach', 'VEGETABLES', 'FRIDGE', 1.0, 'ea', '2026-07-01', 'HMART', '',
                        '2026-07-05', 'USED')
                """.trimIndent()
            )
        }

        // Opening runs MIGRATION_2_3 and then Room's schema validation; a mismatch throws.
        val database = openWithRoom()
        try {
            assertEquals(3, database.openHelper.writableDatabase.version)
            assertEquals(
                listOf("Spinach"),
                database.consumptionEventDao().getAll().map { it.name }
            )
        } finally {
            database.close()
        }
    }
}
