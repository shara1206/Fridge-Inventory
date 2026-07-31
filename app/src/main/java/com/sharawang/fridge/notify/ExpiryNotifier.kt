package com.sharawang.fridge.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sharawang.fridge.MainActivity
import com.sharawang.fridge.R

/**
 * Posts the one daily reminder, in the device language. Silently does nothing if the user
 * never granted notification access.
 */
class ExpiryNotifier(private val context: Context) {

    // canPost() (below) already checks POST_NOTIFICATIONS before we get here;
    // lint can't trace that across methods, so suppress its false positive.
    @SuppressLint("MissingPermission")
    fun post(summary: ExpirySummary.Summary) {
        if (!canPost()) return
        ensureChannel()

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val body = formatBody(summary)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(formatTitle(summary))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    internal fun formatTitle(summary: ExpirySummary.Summary): String = when {
        summary.expiredCount > 0 && summary.dueSoonCount > 0 -> context.getString(
            R.string.notif_title_both, summary.expiredCount, summary.dueSoonCount
        )
        summary.expiredCount > 0 ->
            context.getString(R.string.notif_title_expired, summary.expiredCount)
        else -> context.getString(R.string.notif_title_due, summary.dueSoonCount)
    }

    internal fun formatBody(summary: ExpirySummary.Summary): String {
        val names = summary.shown.joinToString(", ") { shown ->
            context.getString(R.string.notif_entry, shown.name, describe(shown.daysLeft))
        }
        if (summary.hiddenCount == 0) return names
        return names + " " + context.getString(R.string.notif_more, summary.hiddenCount)
    }

    private fun describe(days: Long): String = when {
        days < -1 -> context.getString(R.string.notif_days_over, -days)
        days == -1L -> context.getString(R.string.notif_day_over)
        days == 0L -> context.getString(R.string.notif_today)
        days == 1L -> context.getString(R.string.notif_tomorrow)
        else -> context.getString(R.string.notif_in_days, days)
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "expiry_reminders"
        private const val NOTIFICATION_ID = 1001
    }
}
