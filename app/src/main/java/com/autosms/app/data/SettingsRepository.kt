package com.autosms.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** همهٔ تنظیمات قابل ویرایش برنامه. */
data class AppSettings(
    val enabled: Boolean = false,
    val messageText: String = "",
    val dailyCount: Int = 100,
    val startHour: Int = 20,
    val endHour: Int = 22,
    val delaySeconds: Int = 70
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val MESSAGE = stringPreferencesKey("message_text")
        val DAILY_COUNT = intPreferencesKey("daily_count")
        val START_HOUR = intPreferencesKey("start_hour")
        val END_HOUR = intPreferencesKey("end_hour")
        val DELAY_SECONDS = intPreferencesKey("delay_seconds")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            enabled = p[Keys.ENABLED] ?: false,
            messageText = p[Keys.MESSAGE] ?: "",
            dailyCount = p[Keys.DAILY_COUNT] ?: 100,
            startHour = p[Keys.START_HOUR] ?: 20,
            endHour = p[Keys.END_HOUR] ?: 22,
            delaySeconds = p[Keys.DELAY_SECONDS] ?: 70
        )
    }

    suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun update(settings: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.ENABLED] = settings.enabled
            p[Keys.MESSAGE] = settings.messageText
            p[Keys.DAILY_COUNT] = settings.dailyCount
            p[Keys.START_HOUR] = settings.startHour
            p[Keys.END_HOUR] = settings.endHour
            p[Keys.DELAY_SECONDS] = settings.delaySeconds
        }
    }
}
