package com.sharawang.fridge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sharawang.fridge.data.local.AppDatabase
import com.sharawang.fridge.data.repo.InventoryRepository
import com.sharawang.fridge.ui.inventory.InventoryScreen
import com.sharawang.fridge.ui.inventory.InventoryViewModel
import com.sharawang.fridge.ui.theme.FridgeTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test: proves the inventory screen composes, reads from the database and
 * renders a row. Deliberately shallow — its job is to catch "the screen crashes on launch",
 * which unit tests cannot see.
 */
@RunWith(AndroidJUnit4::class)
class InventoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: InventoryRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = InventoryRepository(database.foodItemDao(), database.purchaseDao())
    }

    @After
    fun tearDown() = database.close()

    private fun show() {
        composeRule.setContent {
            FridgeTheme {
                InventoryScreen(
                    viewModel = InventoryViewModel(repository),
                    onAddManual = {},
                    onScanReceipt = {},
                    onOpenSettings = {},
                    onOpenHistory = {},
                    onOpenItem = {}
                )
            }
        }
    }

    @Test
    fun emptyKitchenShowsTheInvitation() {
        show()
        composeRule
            .onNodeWithText(
                InstrumentationRegistry.getInstrumentation().targetContext
                    .getString(R.string.inventory_empty)
            )
            .assertIsDisplayed()
    }

    @Test
    fun savedItemAppearsInTheList() {
        runBlocking { repository.save(repository.draftFor("Organic Baby Spinach")) }
        show()
        // The list arrives through a Flow, so give it a frame or two rather than assuming
        // it is present the instant setContent returns.
        composeRule.waitUntil(timeoutMillis = 5_000) { nodeExists("Organic Baby Spinach") }
        composeRule.onNodeWithText("Organic Baby Spinach").assertIsDisplayed()
    }

    private fun nodeExists(text: String): Boolean = runCatching {
        composeRule.onNodeWithText(text).assertExists()
        true
    }.getOrDefault(false)
}
