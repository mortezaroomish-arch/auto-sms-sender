package com.autosms.app.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.autosms.app.R
import com.autosms.app.contacts.ContactRepository
import com.autosms.app.data.AppSettings
import com.autosms.app.data.AppDatabase
import com.autosms.app.data.Customer
import com.autosms.app.data.SettingsRepository
import com.autosms.app.sms.SmsSender
import com.autosms.app.util.MessageTemplates
import com.autosms.app.util.PhoneUtil
import kotlinx.coroutines.delay
import java.text.Collator
import java.util.Calendar
import java.util.Locale

/**
 * هستهٔ برنامه: هر شب در ساعتِ شروع اجرا می‌شود، مخاطبین جدید را همگام می‌کند،
 * و برای گروهی از مشتریان که بیشترین مدت از آخرین پیامشان گذشته پیامک می‌فرستد.
 */
class SmsWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val settingsRepo = SettingsRepository(context)
    private val contactRepo = ContactRepository(context)
    private val smsSender = SmsSender(context)
    private val dao = AppDatabase.get(context).customerDao()

    companion object {
        const val KEY_MANUAL = "manual"
    }

    /** برای کارهای «فوری» (اجرای دستی) در گوشی‌های قدیمی‌تر لازم است. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val total = try {
            settingsRepo.current().dailyCount
        } catch (_: Exception) {
            0
        }
        return buildForegroundInfo(0, total)
    }

    override suspend fun doWork(): Result {
        val settings = settingsRepo.current()
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        var sent = 0
        var failed = 0

        try {
            if (!manual && !settings.enabled) {
                return Result.success()
            }
            if (settings.messageText.isBlank()) {
                return Result.success()
            }

            setForegroundSafe(0, settings.dailyCount)

            // ۱) همگام‌سازی مخاطبین جدید
            val allContacts = contactRepo.readAllContacts()
            if (allContacts.isNotEmpty()) {
                dao.insertNew(allContacts)
            }

            // ۲) انتخاب افرادِ نوبتی با اعمالِ فیلترِ پیش‌شماره، لیستِ استثنا و لیستِ لغو
            val prefixes = parsePrefixes(settings.numberPrefixes)
            val excluded = parseExcluded(settings.excludedNumbers)
            val optedOut = settingsRepo.currentOptedOut()
            val blocked = excluded + optedOut

            val eligible = dao.getAllDue()
                .filter { matchesPrefix(it.phoneNumber, prefixes) }
                .filter { PhoneUtil.toLocal(it.phoneNumber) !in blocked }

            // چرخهٔ الفبایی:
            // «نوبتِ این چرخه نرسیده» = هرگز پیام نگرفته، یا آخرین پیامش پیش از شروعِ چرخهٔ فعلی بوده.
            // مخاطبِ جدید (بدونِ تاریخِ ارسال) همیشه واجدِ شرایط است و سرِ جای الفباییِ خودش
            // وارد می‌شود؛ پس اگر وسطِ «ب» یک «الف» اضافه شود، شبِ بعد اول همان «الف» می‌رود.
            val cycleStart = settingsRepo.currentCycleStart()
            var pool = eligible.filter { it.lastSentAt == null || it.lastSentAt < cycleStart }

            // اگر همه در این چرخه پیام گرفته‌اند، چرخهٔ تازه‌ای از ابتدای الفبا شروع می‌شود
            // و شمارهٔ متن یکی جلو می‌رود تا در دوره‌ی جدید متنِ تکراری فرستاده نشود.
            if (pool.isEmpty() && eligible.isNotEmpty()) {
                val now = System.currentTimeMillis()
                settingsRepo.setCycleStart(now)
                settingsRepo.setCycleMessageIndex(settingsRepo.currentCycleMessageIndex() + 1)
                pool = eligible
            }

            // در حالتِ «ترتیبی» یک متنِ ثابت برای کلِ این دوره انتخاب می‌شود؛ در غیرِ این‌صورت
            // در هر ارسال متنی تصادفی انتخاب می‌گردد (fixedTemplate = null).
            val variants = MessageTemplates.variants(settings.messageText)
            val fixedTemplate: String? =
                if (settings.sequentialMessages && variants.size > 1) {
                    variants[settingsRepo.currentCycleMessageIndex() % variants.size]
                } else {
                    null
                }

            // مرتب‌سازی «بر اساسِ نامِ الفباییِ فارسی» به‌عنوانِ کلیدِ اصلی (نه تاریخِ ارسال)،
            // با پاک‌کردنِ پیشوندِ «دکتر» تا احمد زیرِ «الف» بیاید نه «د». هم‌نام‌ها با شماره
            // مرتب می‌شوند تا ترتیب پایدار بماند.
            val faCollator = Collator.getInstance(Locale("fa"))
            val due = pool
                .sortedWith(
                    Comparator<Customer> { a, b -> faCollator.compare(nameSortKey(a.name), nameSortKey(b.name)) }
                        .thenBy { it.phoneNumber }
                )
                .take(settings.dailyCount)

            val minDelay = settings.delaySeconds.coerceAtLeast(1)
            val maxDelay = settings.delayMaxSeconds

            for ((index, customer) in due.withIndex()) {
                if (!manual && isPastEndHour(settings.endHour)) break

                val text = buildMessage(settings, customer, fixedTemplate)
                val ok = smsSender.send(customer.phoneNumber, text)
                if (ok) {
                    dao.markSent(customer.phoneNumber, System.currentTimeMillis())
                    sent++
                } else {
                    failed++
                }
                setForegroundSafe(index + 1, due.size)

                if (index < due.size - 1) {
                    // فاصلهٔ تصادفی بینِ حداقل و حداکثر (اگر حداکثر بزرگ‌تر باشد)؛ وگرنه ثابت.
                    val seconds = if (maxDelay > minDelay) (minDelay..maxDelay).random() else minDelay
                    delay(seconds * 1000L)
                }
            }

            Notifications.showCompletion(context, sent, failed)

            return Result.success()
        } finally {
            if (!manual && settings.enabled && !isStopped) {
                Scheduler.scheduleNext(context, settings.startHour)
            }
        }
    }

    /**
     * ساختِ متنِ پیام. اگر [fixedTemplate] داده شده باشد (حالتِ ترتیبی) همان استفاده می‌شود؛
     * وگرنه از بینِ متن‌ها یکی تصادفی انتخاب می‌گردد. سپس در صورتِ روشن‌بودنِ شخصی‌سازی،
     * {نام} با نامِ مخاطب جایگزین می‌شود.
     */
    private fun buildMessage(settings: AppSettings, customer: Customer, fixedTemplate: String?): String {
        val base = fixedTemplate ?: MessageTemplates.pick(settings.messageText)
        if (!settings.personalizeWithName) return base
        val name = cleanName(customer.name)
        return base.replace("{نام}", name)
    }

    /** حذفِ «دکتر»ِ ابتداییِ نام تا هنگام شخصی‌سازی تکراری نشود. */
    private fun cleanName(raw: String): String {
        var n = raw.trim()
        if (n.startsWith("دکتر")) n = n.removePrefix("دکتر").trim()
        if (n.startsWith("دكتر")) n = n.removePrefix("دكتر").trim()
        return n
    }

    /**
     * کلیدِ مرتب‌سازیِ الفبایی. برای اینکه حروف قاطی نشوند:
     *  ۱) پیشوندِ «دکتر» حذف می‌شود؛
     *  ۲) واریانت‌های عربیِ حروف به معادلِ فارسی یکسان می‌شوند
     *     (ي→ی، ك→ک، أ/إ/آ/ٱ→ا، ة→ه، ؤ→و، ئ→ی)؛
     *  ۳) اعرابِ عربی، «ـ»ِ کشیده و نیم‌فاصله حذف و فاصله‌های ابتدایی/انتهایی گرفته می‌شوند.
     * نام‌های خالی با یک نویسهٔ بالا به انتهای فهرست می‌روند تا ترتیب خراب نشود.
     */
    private fun nameSortKey(raw: String): String {
        val sb = StringBuilder()
        for (ch in cleanName(raw)) {
            when (ch) {
                'ي', 'ئ', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'أ', 'إ', 'آ', 'ٱ', 'ا' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ؤ' -> sb.append('و')
                '\u200C', '\u0640' -> {} // نیم‌فاصله و «ـ»ِ کشیده حذف شوند
                in '\u064B'..'\u0652' -> {} // اعرابِ عربی (فتحه/کسره/…) حذف شوند
                else -> sb.append(ch)
            }
        }
        val key = sb.toString().trim()
        return if (key.isBlank()) "\uFFFF" else key
    }

    private fun parsePrefixes(raw: String): List<String> =
        raw.split(',', '،', '\n', ' ', ';')
            .map { PhoneUtil.normalizeDigits(it) }
            .filter { it.isNotEmpty() }

    private fun parseExcluded(raw: String): Set<String> =
        raw.split(',', '،', '\n', ' ', ';')
            .map { PhoneUtil.toLocal(it) }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun matchesPrefix(number: String, prefixes: List<String>): Boolean {
        if (prefixes.isEmpty()) return true
        val local = PhoneUtil.toLocal(number)
        return prefixes.any { local.startsWith(it) }
    }

    private fun isPastEndHour(endHour: Int): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= endHour
    }

    private suspend fun setForegroundSafe(current: Int, total: Int) {
        try {
            setForeground(buildForegroundInfo(current, total))
        } catch (_: Exception) {
        }
    }

    private fun buildForegroundInfo(current: Int, total: Int): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ID)
            .setContentTitle("در حال ارسال پیامک")
            .setContentText("$current از $total ارسال شد")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), current, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Notifications.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(Notifications.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }
}
