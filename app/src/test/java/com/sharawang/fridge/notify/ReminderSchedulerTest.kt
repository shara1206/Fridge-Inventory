package com.sharawang.fridge.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderSchedulerTest {

    @Test
    fun `later today when the hour has not passed`() {
        val now = LocalDateTime.of(2026, 7, 30, 6, 30)
        assertEquals(150, ReminderScheduler.minutesUntilNext(9, now))
    }

    @Test
    fun `tomorrow when the hour has already passed`() {
        val now = LocalDateTime.of(2026, 7, 30, 10, 0)
        assertEquals(23 * 60, ReminderScheduler.minutesUntilNext(9, now))
    }

    @Test
    fun `never schedules a zero delay`() {
        val now = LocalDateTime.of(2026, 7, 30, 9, 0)
        assertTrue(ReminderScheduler.minutesUntilNext(9, now) >= 1)
    }

    @Test
    fun `out of range hours are clamped instead of crashing`() {
        val now = LocalDateTime.of(2026, 7, 30, 12, 0)
        assertTrue(ReminderScheduler.minutesUntilNext(99, now) > 0)
        assertTrue(ReminderScheduler.minutesUntilNext(-4, now) > 0)
    }
}
