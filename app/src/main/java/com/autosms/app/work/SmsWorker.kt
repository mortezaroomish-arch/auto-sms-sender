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
import com.autosms.app.sms.SimUtil
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
            val prefixes = if (settings.prefixFilterEnabled) parsePrefixes(settings.numberPrefixes) else emptyList()
            val excluded = if (settings.excludedFilterEnabled) parseExcluded(settings.excludedNumbers) else emptySet()
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

            // انتخابِ متن برای این دوره:
            //  - اگر «ترتیبی» روشن باشد و چند متن باشد: متنِ این دوره بر اساسِ شمارهٔ دوره
            //    (هر دوره متنِ بعدی؛ پس تکراری فرستاده نمی‌شود).
            //  - در غیرِ این‌صورت: همیشه متنِ پیش‌فرض (اولین متن) فرستاده می‌شود.
            val cycleMessageIndex = settingsRepo.currentCycleMessageIndex()
            val messageForCycle = pickMessage(settings.messageText, settings, cycleMessageIndex)

            // ---- آماده‌سازیِ حالتِ دو سیم‌کارت ----
            // اگر روشن باشد و دستگاه دو سیمِ فعال داشته باشد، دو subId جدا و متنِ سیمِ دوم
            // را آماده می‌کنیم. اگر متنِ سیمِ دوم خالی باشد، همان متنِ اصلی استفاده می‌شود.
            val sims = SimUtil.activeSims(context)
            val dualSim = settings.dualSimEnabled && sims.size >= 2
            val sim1SubId = if (dualSim) SimUtil.resolveSubId(context, settings.sim1SubId, fallbackSlot = 0) else -1
            val sim2SubId = if (dualSim) SimUtil.resolveSubId(context, settings.sim2SubId, fallbackSlot = 1) else -1
            val messageForSim2: String =
                if (settings.sim2MessageText.isNotBlank())
                    pickMessage(settings.sim2MessageText, settings, cycleMessageIndex)
                else messageForCycle
            // سقفِ هر سیم؛ اگر دو subId یکسان شدند (خطای انتخاب)، حالتِ دو سیم را خاموش می‌کنیم.
            val simLimit = settings.simDailyLimit.coerceAtLeast(1)
            val useDualSim = dualSim && sim1SubId != sim2SubId
            var sentSim1 = 0
            var sentSim2 = 0

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

                // انتخابِ سیم برای این پیام (حالتِ دو سیم = یکی‌درمیان با رعایتِ سقفِ هر سیم).
                val useSim2: Boolean
                val subId: Int
                if (useDualSim) {
                    val sim1Full = sentSim1 >= simLimit
                    val sim2Full = sentSim2 >= simLimit
                    if (sim1Full && sim2Full) break // سقفِ هر دو سیم پر شد
                    useSim2 = when {
                        sim1Full -> true          // سیم ۱ پر است → همه به سیم ۲
                        sim2Full -> false         // سیم ۲ پر است → همه به سیم ۱
                        else -> index % 2 == 1    // یکی‌درمیان: زوج → سیم ۱، فرد → سیم ۲
                    }
                    subId = if (useSim2) sim2SubId else sim1SubId
                } else {
                    useSim2 = false
                    subId = -1
                }

                val template = if (useSim2) messageForSim2 else messageForCycle
                val text = buildMessage(settings, customer, template)
                val ok = smsSender.send(customer.phoneNumber, text, subId)
                if (ok) {
                    dao.markSent(customer.phoneNumber, System.currentTimeMillis())
                    sent++
                    if (useDualSim) {
                        if (useSim2) sentSim2++ else sentSim1++
                    }
                } else {
                    failed++
                }
                setForegroundSafe(index + 1, due.size)

                if (index < due.size - 1) {
                    // در حالتِ دو سیم فاصله نصف می‌شود: چون دو سیم بارِ ارسال را تقسیم می‌کنند،
                    // فاصلهٔ هر سیم با پیامِ بعدیِ خودش ≈ مقدارِ تنظیم‌شده می‌ماند، ولی سرعتِ کل دوبرابر.
                    val loMin = if (useDualSim) (minDelay / 2).coerceAtLeast(1) else minDelay
                    val loMax = if (useDualSim) (maxDelay / 2) else maxDelay
                    // فاصلهٔ تصادفی بینِ حداقل و حداکثر (اگر حداکثر بزرگ‌تر باشد)؛ وگرنه ثابت.
                    val seconds = if (loMax > loMin) (loMin..loMax).random() else loMin
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
     * انتخابِ متنِ این دوره از یک رشتهٔ خام (که ممکن است چند متنِ جداشده با «---» داشته باشد):
     *  - اگر «ترتیبی» روشن باشد و چند متن باشد، متنِ متناظرِ شمارهٔ دوره انتخاب می‌شود.
     *  - در غیرِ این‌صورت، اولین متن (پیش‌فرض) برمی‌گردد.
     */
    private fun pickMessage(raw: String, settings: AppSettings, cycleMessageIndex: Int): String {
        val variants = MessageTemplates.variants(raw)
        return if (settings.sequentialMessages && variants.size > 1) {
            variants[cycleMessageIndex % variants.size]
        } else {
            variants.firstOrNull() ?: raw.trim()
        }
    }

    /**
     * ساختِ متنِ پیام از متنِ انتخاب‌شدهٔ این دوره. در صورتِ روشن‌بودنِ شخصی‌سازی،
     * «(نام)» با نامِ مخاطب جایگزین می‌شود (برای سازگاری، «{نام}» هم پشتیبانی می‌شود).
     */
    private fun buildMessage(settings: AppSettings, customer: Customer, template: String): String {
        if (!settings.personalizeWithName) return template
        val name = cleanName(customer.name)
        return template.replace("(نام)", name).replace("{نام}", name)
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
