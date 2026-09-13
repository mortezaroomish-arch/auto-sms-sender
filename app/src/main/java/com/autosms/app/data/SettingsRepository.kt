package com.autosms.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    val delaySeconds: Int = 70,
    /** اگر روشن باشد، عبارت {نام} در متن با نام مخاطب جایگزین می‌شود. */
    val personalizeWithName: Boolean = false,
    /** فقط شماره‌هایی که با این پیش‌شماره‌ها شروع می‌شوند پیام می‌گیرند (با کاما جدا). خالی = همه. */
    val numberPrefixes: String = "",
    /** شماره‌هایی که هرگز نباید پیام بگیرند (هر شماره در یک خط یا با کاما). */
    val excludedNumbers: String = "",
    /** لغوِ اشتراکِ خودکار: اگر روشن باشد، هرکس در جواب «لغو» بفرستد حذف می‌شود. */
    val autoOptOut: Boolean = false,
    /** کلمه‌ای که در پیامِ ورودی نشانهٔ لغو است. */
    val optOutKeyword: String = "لغو"
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
        val PERSONALIZE = booleanPreferencesKey("personalize_with_name")
        val PREFIXES = stringPreferencesKey("number_prefixes")
        val EXCLUDED = stringPreferencesKey("excluded_numbers")
        val AUTO_OPT_OUT = booleanPreferencesKey("auto_opt_out")
        val OPT_OUT_KEYWORD = stringPreferencesKey("opt_out_keyword")
        val OPTED_OUT = stringSetPreferencesKey("opted_out_numbers")
        val CYCLE_START = longPreferencesKey("cycle_start_at")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            enabled = p[Keys.ENABLED] ?: false,
            messageText = p[Keys.MESSAGE] ?: "",
            dailyCount = p[Keys.DAILY_COUNT] ?: 100,
            startHour = p[Keys.START_HOUR] ?: 20,
            endHour = p[Keys.END_HOUR] ?: 22,
            delaySeconds = p[Keys.DELAY_SECONDS] ?: 70,
            personalizeWithName = p[Keys.PERSONALIZE] ?: false,
            numberPrefixes = p[Keys.PREFIXES] ?: "",
            excludedNumbers = p[Keys.EXCLUDED] ?: "",
            autoOptOut = p[Keys.AUTO_OPT_OUT] ?: false,
            optOutKeyword = p[Keys.OPT_OUT_KEYWORD] ?: "لغو"
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
            p[Keys.PERSONALIZE] = settings.personalizeWithName
            p[Keys.PREFIXES] = settings.numberPrefixes
            p[Keys.EXCLUDED] = settings.excludedNumbers
            p[Keys.AUTO_OPT_OUT] = settings.autoOptOut
            p[Keys.OPT_OUT_KEYWORD] = settings.optOutKeyword
        }
    }

    // ---- لیستِ لغوِ اشتراک (جدا از تنظیمات، چون خودکار پر می‌شود) ----

    val optedOutFlow: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.OPTED_OUT] ?: emptySet() }

    suspend fun currentOptedOut(): Set<String> = optedOutFlow.first()

    suspend fun addOptedOut(number: String) {
        if (number.isBlank()) return
        context.dataStore.edit { p ->
            val current = p[Keys.OPTED_OUT] ?: emptySet()
            p[Keys.OPTED_OUT] = current + number
        }
    }

    suspend fun clearOptedOut() {
        context.dataStore.edit { p ->
            p[Keys.OPTED_OUT] = emptySet()
        }
    }

    // ---- چرخهٔ ارسالِ الفبایی ----
    // زمانِ شروعِ چرخهٔ فعلی. هرکس که آخرین ارسالش قبل از این زمان بوده (یا اصلاً
    // پیام نگرفته) هنوز «نوبتش این چرخه نرسیده» و واجدِ شرایطِ ارسال است.

    /** زمانِ شروعِ چرخهٔ فعلی (۰ یعنی هنوز چرخه‌ای شروع نشده = همه واجدِ شرایط‌اند). */
    suspend fun currentCycleStart(): Long =
        context.dataStore.data.map { it[Keys.CYCLE_START] ?: 0L }.first()

    /** شروعِ چرخهٔ جدید: از این لحظه به بعد، همهٔ مخاطبین دوباره نوبت می‌گیرند. */
    suspend fun setCycleStart(timestamp: Long) {
        context.dataStore.edit { p ->
            p[Keys.CYCLE_START] = timestamp
        }
    }
}
