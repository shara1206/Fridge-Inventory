package com.sharawang.fridge.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sharawang.fridge.data.SettingsRepository
import com.sharawang.fridge.data.local.AppDatabase
import com.sharawang.fridge.data.repo.InventoryRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Runs once a day. Reads the settings first and bails out early if reminders are off, so a
 * stale scheduled job left over from a previous install can never notify anyone.
 */
class ExpiryCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(context).settings.first()
        if (!settings.enabled) return Result.success()

        val today = LocalDate.now()
        val database = AppDatabase.get(context)
        val repository = InventoryRepository(database.foodItemDao(), database.purchaseDao())
        val items = repository.expiringBy(today.plusDays(settings.leadDays.toLong()))

        val summary = ExpirySummary.build(
            entries = items.mapNotNull { item ->
                item.expiresOn?.let { ExpirySummary.Entry(item.name, it) }
            },
            today = today,
            leadDays = settings.leadDays
        ) ?: return Result.success()

        ExpiryNotifier(context).post(summary)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "expiry_check"
    }
}
