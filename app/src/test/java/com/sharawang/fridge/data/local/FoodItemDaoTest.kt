package com.sharawang.fridge.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Runs on the JVM through Robolectric so the database layer is covered in CI without an
 * emulator. These are the queries that silently return the wrong rows if a WHERE clause
 * drifts, which no amount of UI testing would catch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodItemDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: FoodItemDao

    private val today = LocalDate.of(2026, 7, 30)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.foodItemDao()
    }

    @After
    fun tearDown() = database.close()

    private fun item(
        name: String,
        expiresOn: LocalDate? = null,
        area: StorageArea = StorageArea.FRIDGE,
        category: FoodCategory = FoodCategory.VEGETABLES,
        quantity: Double = 1.0
    ) = FoodItem(
        name = name,
        category = category,
        storageArea = area,
        quantity = quantity,
        purchasedOn = today,
        expiresOn = expiresOn
    )

    @Test
    fun `active list is soonest first with untracked expiry last`() = runTest {
        dao.insert(item("No expiry"))
        dao.insert(item("Next week", today.plusDays(7)))
        dao.insert(item("Tomorrow", today.plusDays(1)))
        dao.insert(item("Expired", today.minusDays(2)))

        assertEquals(
            listOf("Expired", "Tomorrow", "Next week", "No expiry"),
            dao.observeActive().first().map { it.name }
        )
    }

    @Test
    fun `finishing hides the row and restoring brings it back with a given quantity`() = runTest {
        val id = dao.insert(item("Spinach", today.plusDays(2), quantity = 3.0))

        dao.markFinished(id, today, FinishReason.DISCARDED)
        assertTrue(dao.observeActive().first().isEmpty())

        val finished = dao.getById(id)!!
        assertEquals(today, finished.finishedOn)
        assertEquals(FinishReason.DISCARDED, finished.finishedReason)

        dao.restore(id, quantity = 1.5)
        val restored = dao.getById(id)!!
        assertNull(restored.finishedOn)
        assertNull(restored.finishedReason)
        assertEquals(1.5, restored.quantity, 0.0001)
        assertEquals(1, dao.observeActive().first().size)
    }

    @Test
    fun `area filter does not leak other areas`() = runTest {
        dao.insert(item("Peas", area = StorageArea.FREEZER))
        dao.insert(item("Milk", area = StorageArea.FRIDGE))

        assertEquals(
            listOf("Peas"),
            dao.observeByArea(StorageArea.FREEZER).first().map { it.name }
        )
    }

    @Test
    fun `expiringBy is inclusive and ignores untracked and finished rows`() = runTest {
        dao.insert(item("Due today", today))
        dao.insert(item("Due in three", today.plusDays(3)))
        dao.insert(item("No expiry"))
        val finishedId = dao.insert(item("Already eaten", today))
        dao.markFinished(finishedId, today, FinishReason.USED)

        assertEquals(
            listOf("Due today"),
            dao.expiringBy(today).map { it.name }
        )
        assertEquals(2, dao.expiringBy(today.plusDays(3)).size)
    }

    @Test
    fun `findActiveMatch ignores case and surrounding space but respects storage area`() =
        runTest {
            dao.insert(item("  Baby Spinach ", today.plusDays(4)))

            assertNotNull(dao.findActiveMatch("baby spinach", StorageArea.FRIDGE))
            assertNull(dao.findActiveMatch("baby spinach", StorageArea.FREEZER))
            assertNull(dao.findActiveMatch("bok choy", StorageArea.FRIDGE))
        }

    @Test
    fun `findActiveMatch never returns a finished row`() = runTest {
        val id = dao.insert(item("Tofu"))
        dao.markFinished(id, today, FinishReason.USED)

        assertNull(dao.findActiveMatch("Tofu", StorageArea.FRIDGE))
    }

    @Test
    fun `search is a case insensitive substring match`() = runTest {
        dao.insert(item("Organic Baby Spinach"))
        dao.insert(item("Pork Belly"))

        assertEquals(
            listOf("Organic Baby Spinach"),
            dao.search("spinach").first().map { it.name }
        )
    }

    @Test
    fun `updateQuantity leaves everything else alone`() = runTest {
        val id = dao.insert(item("Rice", today.plusDays(300), quantity = 2.0))

        dao.updateQuantity(id, 0.5)

        val updated = dao.getById(id)!!
        assertEquals(0.5, updated.quantity, 0.0001)
        assertEquals("Rice", updated.name)
        assertEquals(today.plusDays(300), updated.expiresOn)
    }

    @Test
    fun `waste window excludes anything finished before it`() = runTest {
        val oldId = dao.insert(item("Old"))
        val recentId = dao.insert(item("Recent"))
        dao.markFinished(oldId, today.minusDays(40), FinishReason.DISCARDED)
        dao.markFinished(recentId, today.minusDays(3), FinishReason.DISCARDED)

        assertEquals(
            listOf("Recent"),
            dao.observeFinishedSince(today.minusDays(30)).first().map { it.name }
        )
    }
}
