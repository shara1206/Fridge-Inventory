package com.sharawang.fridge

import android.content.Context
import com.sharawang.fridge.data.SettingsRepository
import com.sharawang.fridge.data.local.AppDatabase
import com.sharawang.fridge.data.repo.InventoryRepository
import com.sharawang.fridge.notify.ReminderScheduler
import com.sharawang.fridge.receipt.ReceiptScanner

/**
 * Hand-rolled dependency container. Small enough that a DI framework would only add
 * build time; swap in Hilt here if the app grows.
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)
    val repository = InventoryRepository(database.foodItemDao(), database.purchaseDao())
    val scanner = ReceiptScanner()
    val settingsRepository = SettingsRepository(context.applicationContext)
    val reminderScheduler = ReminderScheduler(context.applicationContext)
}
