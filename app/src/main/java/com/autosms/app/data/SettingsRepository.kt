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
    /** حداقلِ فاصلهٔ بینِ دو پیام (ثانیه). */
    val delaySeconds: Int = 70,
    /** حداکثرِ فاصله (ثانیه) برای تصادفی‌بودن. اگر ۰ یا کوچک‌تر/مساویِ حداقل باشد، فاصله ثابت است. */
    val delayMaxSeconds: Int = 0,
    /** اگر روشن باشد، عبارت {نام} در متن با نام مخاطب جایگزین می‌شود. */
    val personalizeWithName: Boolean = false,
    /**
     * اگر روشن باشد و چند متن (جداشده با «---») داشته باشی، در هر دوره یک متن به‌ترتیب
     * انتخاب می‌شود و دوره‌ی بعد متنِ بعدی؛ این‌طور یک نفر دو دوره‌ی پشتِ‌هم متنِ تکراری نمی‌گیرد.
     * اگر خاموش باشد، در هر ارسال یک متن به‌صورتِ تصادفی انتخاب می‌شود.
     */
    val sequentialMessages: Boolean = false,
    /** فقط شماره‌هایی که با این پیش‌شماره‌ها شروع می‌شوند پیام می‌گیرند (با کاما جدا). خالی = همه. */
    val numberPrefixes: String = "",
    /** شماره‌هایی که هرگز نباید پیام بگیرند (هر شماره در یک خط یا با کاما). */
    val excludedNumbers: String = "",
    /** لغوِ اشتراکِ خودکار: اگر روشن باشد، هرکس در جواب «لغو» بفرستد حذف می‌شود. */
    val autoOptOut: Boolean = false,
    /** کلمه‌ای که در پیامِ ورودی نشانهٔ لغو است. */
    val optOutKeyword: String = "لغو",
    /**
     * پاسخِ خودکار به پیامکِ ورودی: اگر روشن باشد، وقتی کسی پیامک می‌فرستد برنامه خودکار
     * جواب می‌دهد. اول قانون‌های کلیدواژه‌ای بررسی می‌شوند و اگر هیچ‌کدام نبود، جوابِ پیش‌فرض می‌رود.
     */
    val autoReplyEnabled: Boolean = false,
    /** جوابی که وقتی هیچ قانونِ کلیدواژه‌ای مطابقت نکند فرستاده می‌شود. خالی = چیزی فرستاده نشود. */
    val autoReplyDefault: String = "",
    /** قانون‌های کلیدواژه‌ای، هر خط یکی، به شکلِ «کلیدواژه = جواب». */
    val autoReplyRules: String = "",
    /**
     * حداقلِ فاصله (دقیقه) بینِ دو پاسخِ خودکار به یک شماره. جلوی ارسالِ مکرر و حلقه را می‌گیرد.
     * ۰ یعنی محدودیتی نباشد (به هر پیام جواب بده).
     */
    val autoReplyCooldownMinutes: Int = 10
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
        val DELAY_MAX_SECONDS = intPreferencesKey("delay_max_seconds")
        val PERSONALIZE = booleanPreferencesKey("personalize_with_name")
        val SEQUENTIAL_MESSAGES = booleanPreferencesKey("sequential_messages")
        val CYCLE_MESSAGE_INDEX = intPreferencesKey("cycle_message_index")
        val PREFIXES = stringPreferencesKey("number_prefixes")
        val EXCLUDED = stringPreferencesKey("excluded_numbers")
        val AUTO_OPT_OUT = booleanPreferencesKey("auto_opt_out")
        val OPT_OUT_KEYWORD = stringPreferencesKey("opt_out_keyword")
        val AUTO_REPLY_ENABLED = booleanPreferencesKey("auto_reply_enabled")
        val AUTO_REPLY_DEFAULT = stringPreferencesKey("auto_reply_default")
        val AUTO_REPLY_RULES = stringPreferencesKey("auto_reply_rules")
        val AUTO_REPLY_COOLDOWN = intPreferencesKey("auto_reply_cooldown_minutes")
        val AUTO_REPLY_LOG = stringSetPreferencesKey("auto_reply_log")
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
            delayMaxSeconds = p[Keys.DELAY_MAX_SECONDS] ?: 0,
            personalizeWithName = p[Keys.PERSONALIZE] ?: false,
            sequentialMessages = p[Keys.SEQUENTIAL_MESSAGES] ?: false,
            numberPrefixes = p[Keys.PREFIXES] ?: "",
            excludedNumbers = p[Keys.EXCLUDED] ?: "",
            autoOptOut = p[Keys.AUTO_OPT_OUT] ?: false,
            optOutKeyword = p[Keys.OPT_OUT_KEYWORD] ?: "لغو",
            autoReplyEnabled = p[Keys.AUTO_REPLY_ENABLED] ?: false,
            autoReplyDefault = p[Keys.AUTO_REPLY_DEFAULT] ?: "",
            autoReplyRules = p[Keys.AUTO_REPLY_RULES] ?: "",
            autoReplyCooldownMinutes = p[Keys.AUTO_REPLY_COOLDOWN] ?: 10
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
            p[Keys.DELAY_MAX_SECONDS] = settings.delayMaxSeconds
            p[Keys.PERSONALIZE] = settings.personalizeWithName
            p[Keys.SEQUENTIAL_MESSAGES] = settings.sequentialMessages
            p[Keys.PREFIXES] = settings.numberPrefixes
            p[Keys.EXCLUDED] = settings.excludedNumbers
            p[Keys.AUTO_OPT_OUT] = settings.autoOptOut
            p[Keys.OPT_OUT_KEYWORD] = settings.optOutKeyword
            p[Keys.AUTO_REPLY_ENABLED] = settings.autoReplyEnabled
            p[Keys.AUTO_REPLY_DEFAULT] = settings.autoReplyDefault
            p[Keys.AUTO_REPLY_RULES] = settings.autoReplyRules
            p[Keys.AUTO_REPLY_COOLDOWN] = settings.autoReplyCooldownMinutes
        }
    }

    // ---- سابقهٔ پاسخِ خودکار (برای جلوگیری از ارسالِ مکرر به یک شماره) ----

    /** آخرین زمانی که به این شماره پاسخِ خودکار داده‌ایم (میلی‌ثانیه؛ ۰ = هرگز). */
    suspend fun lastAutoReplyAt(number: String): Long {
        val set = context.dataStore.data.map { it[Keys.AUTO_REPLY_LOG] ?: emptySet() }.first()
        for (entry in set) {
            val idx = entry.lastIndexOf('=')
            if (idx > 0 && entry.substring(0, idx) == number) {
                return entry.substring(idx + 1).toLongOrNull() ?: 0L
            }
        }
        return 0L
    }

    /** ثبتِ زمانِ پاسخِ خودکار به یک شماره و پاک‌کردنِ سوابقِ قدیمی‌تر از ۲۴ ساعت. */
    suspend fun recordAutoReply(number: String, now: Long) {
        val keepMillis = 24 * 60 * 60 * 1000L
        context.dataStore.edit { p ->
            val old = p[Keys.AUTO_REPLY_LOG] ?: emptySet()
            val kept = mutableSetOf<String>()
            for (entry in old) {
                val idx = entry.lastIndexOf('=')
                if (idx <= 0) continue
                val n = entry.substring(0, idx)
                val ts = entry.substring(idx + 1).toLongOrNull() ?: 0L
                if (n != number && now - ts < keepMillis) kept.add(entry)
            }
            kept.add("$number=$now")
            p[Keys.AUTO_REPLY_LOG] = kept
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

    /** جایگزینیِ کاملِ لیستِ لغو (برای بازیابیِ پشتیبان). */
    suspend fun setOptedOut(numbers: Set<String>) {
        context.dataStore.edit { p ->
            p[Keys.OPTED_OUT] = numbers
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

    /** شمارهٔ متنِ فعلی برای حالتِ «ترتیبی»؛ با هر چرخهٔ جدید یکی زیاد می‌شود. */
    suspend fun currentCycleMessageIndex(): Int =
        context.dataStore.data.map { it[Keys.CYCLE_MESSAGE_INDEX] ?: 0 }.first()

    suspend fun setCycleMessageIndex(index: Int) {
        context.dataStore.edit { p ->
            p[Keys.CYCLE_MESSAGE_INDEX] = index
        }
    }
}
