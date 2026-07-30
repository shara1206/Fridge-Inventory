package com.sharawang.fridge.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Owns the single periodic job. Nothing is enqueued until the user turns reminders on —
 * a fresh install schedules no work at all.
 */
class ReminderScheduler(private val context: Context) {

    fun schedule(hour: Int) {
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(minutesUntilNext(hour), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ExpiryCheckWorker.WORK_NAME,
            // UPDATE so changing the reminder hour re-times the existing job instead of
            // stacking a second one.
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(ExpiryCheckWorker.WORK_NAME)
    }

    companion object {
        /** Minutes from now until the next occurrence of [hour] o'clock, local time. */
        fun minutesUntilNext(hour: Int, now: LocalDateTime = LocalDateTime.now()): Long {
            val target = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour.coerceIn(0, 23), 0))
            val next = if (target.isAfter(now)) target else target.plusDays(1)
            return Duration.between(now, next).toMinutes().coerceAtLeast(1)
        }
    }
}
