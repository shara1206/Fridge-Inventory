package com.sharawang.fridge.notify

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Decides *what* the reminder says; [ExpiryNotifier] decides how to word it.
 *
 * Returning structure instead of a finished sentence is what lets the notification be
 * localised while keeping this logic — the part that is easy to get wrong — testable on the
 * JVM with no Android dependency.
 */
object ExpirySummary {

    data class Entry(val name: String, val expiresOn: LocalDate)

    /** One line of the notification body. [daysLeft] is negative when already expired. */
    data class Shown(val name: String, val daysLeft: Long)

    data class Summary(
        val expiredCount: Int,
        val dueSoonCount: Int,
        val shown: List<Shown>,
        val hiddenCount: Int
    ) {
        val itemCount: Int get() = expiredCount + dueSoonCount
    }

    const val MAX_NAMES = 4

    /**
     * Returns null when there is nothing worth interrupting the user about. Callers must
     * treat null as "post no notification" rather than posting an empty one.
     */
    fun build(entries: List<Entry>, today: LocalDate, leadDays: Int): Summary? {
        val relevant = entries
            .map { it to ChronoUnit.DAYS.between(today, it.expiresOn) }
            .filter { (_, days) -> days <= leadDays }
            .sortedBy { (_, days) -> days }
        if (relevant.isEmpty()) return null

        val expired = relevant.count { (_, days) -> days < 0 }
        return Summary(
            expiredCount = expired,
            dueSoonCount = relevant.size - expired,
            shown = relevant.take(MAX_NAMES).map { (entry, days) -> Shown(entry.name, days) },
            hiddenCount = (relevant.size - MAX_NAMES).coerceAtLeast(0)
        )
    }
}
