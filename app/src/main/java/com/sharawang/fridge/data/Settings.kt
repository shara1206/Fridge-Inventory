package com.sharawang.fridge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reminder preferences. Every default is the quiet one: a fresh install notifies nobody
 * and asks for no permission until the user opts in.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    /** Warn this many days before the expiry date. */
    val leadDays: Int = 2,
    /** Local hour of day, 0..23. */
    val hour: Int = 9
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<ReminderSettings> = context.dataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[KEY_ENABLED] ?: false,
            leadDays = prefs[KEY_LEAD_DAYS] ?: 2,
            hour = prefs[KEY_HOUR] ?: 9
        )
    }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_ENABLED] = value }
    }

    suspend fun setLeadDays(value: Int) {
        context.dataStore.edit { it[KEY_LEAD_DAYS] = value.coerceIn(0, 14) }
    }

    suspend fun setHour(value: Int) {
        context.dataStore.edit { it[KEY_HOUR] = value.coerceIn(0, 23) }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("reminders_enabled")
        val KEY_LEAD_DAYS = intPreferencesKey("reminder_lead_days")
        val KEY_HOUR = intPreferencesKey("reminder_hour")
    }
}
