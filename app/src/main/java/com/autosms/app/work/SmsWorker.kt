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
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * هستهٔ برنامه: هر شب در ساعتِ شروع اجرا می‌شود، مخاطبین جدید را همگام می‌کند،
 * و برای گروهی از مشتریان که بیشترین مدت از آخرین پیامشان گذشته پیامک می‌فرستد.
 * سپس خودش را برای فردا زمان‌بندی می‌کند.
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
        // اجرای دستی از داخل برنامه، محدودیتِ فعال‌بودن و بازهٔ زمانی را نادیده می‌گیرد.
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

            // ۲) انتخاب افرادِ نوبتی با اعمالِ فیلترِ پیش‌شماره و لیستِ استثنا
            val prefixes = parsePrefixes(settings.numberPrefixes)
            val excluded = parseExcluded(settings.excludedNumbers)
            val due = dao.getAllDue()
                .asSequence()
                .filter { matchesPrefix(it.phoneNumber, prefixes) }
                .filter { toLocal(it.phoneNumber) !in excluded }
                .take(settings.dailyCount)
                .toList()

            val delayMillis = settings.delaySeconds.coerceAtLeast(1) * 1000L

            for ((index, customer) in due.withIndex()) {
                // در اجرای زمان‌بندی‌شده، اگر از ساعتِ پایان گذشتیم بقیه به فردا موکول می‌شوند.
                if (!manual && isPastEndHour(settings.endHour)) break

                val text = buildMessage(settings, customer)
                val ok = smsSender.send(customer.phoneNumber, text)
                if (ok) {
                    dao.markSent(customer.phoneNumber, System.currentTimeMillis())
                    sent++
                } else {
                    failed++
                }
                setForegroundSafe(index + 1, due.size)

                // بین پیام‌ها فاصله بگذار تا به محدودیت ارسالِ اندروید نخوریم.
                if (index < due.size - 1) {
                    delay(delayMillis)
                }
            }

            // اعلانِ پایانِ ارسال
            Notifications.showCompletion(context, sent, failed)

            return Result.success()
        } finally {
            // فقط برای اجرای زمان‌بندی‌شده، در حالتِ فعال، و اگر کاربر توقف نکرده باشد،
            // اجرای فردا را زمان‌بندی کن.
            if (!manual && settings.enabled && !isStopped) {
                Scheduler.scheduleNext(context, settings.startHour)
            }
        }
    }

    /** ساختِ متنِ پیام بر اساس تنظیمات (شخصی‌سازی با نام یا متنِ یکسان). */
    private fun buildMessage(settings: AppSettings, customer: Customer): String {
        if (!settings.personalizeWithName) return settings.messageText
        val name = cleanName(customer.name)
        return settings.messageText.replace("{نام}", name)
    }

    /** حذفِ «دکتر»ِ ابتداییِ نام تا هنگام شخصی‌سازی تکراری نشود. */
    private fun cleanName(raw: String): String {
        var n = raw.trim()
        if (n.startsWith("دکتر")) n = n.removePrefix("دکتر").trim()
        if (n.startsWith("دكتر")) n = n.removePrefix("دكتر").trim()
        return n
    }

    private fun parsePrefixes(raw: String): List<String> =
        raw.split(',', '،', '\n', ' ', ';')
            .map { normalizeDigits(it) }
            .filter { it.isNotEmpty() }

    private fun parseExcluded(raw: String): Set<String> =
        raw.split(',', '،', '\n', ' ', ';')
            .map { toLocal(it) }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun matchesPrefix(number: String, prefixes: List<String>): Boolean {
        if (prefixes.isEmpty()) return true
        val local = toLocal(number)
        return prefixes.any { local.startsWith(it) }
    }

    /** فقط ارقام (اعداد فارسی/عربی به انگلیسی) و علامتِ + ابتدایی. */
    private fun normalizeDigits(raw: String): String {
        val sb = StringBuilder()
        for (c in raw.trim()) {
            when {
                c == '+' && sb.isEmpty() -> sb.append('+')
                Character.isDigit(c) -> sb.append(Character.digit(c, 10))
            }
        }
        return sb.toString()
    }

    /** شماره را به شکلِ محلیِ «0…» درمی‌آورد تا مقایسهٔ پیش‌شماره/استثنا درست باشد. */
    private fun toLocal(raw: String): String {
        var s = normalizeDigits(raw)
        s = when {
            s.startsWith("+98") -> "0" + s.substring(3)
            s.startsWith("0098") -> "0" + s.substring(4)
            s.startsWith("98") && s.length == 12 -> "0" + s.substring(2)
            else -> s
        }
        return s
    }

    private fun isPastEndHour(endHour: Int): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= endHour
    }

    private suspend fun setForegroundSafe(current: Int, total: Int) {
        try {
            setForeground(buildForegroundInfo(current, total))
        } catch (_: Exception) {
            // ممکن است به دلیل محدودیت‌های سرویسِ پیش‌زمینه شکست بخورد؛ نادیده می‌گیریم.
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
