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
import com.autosms.app.data.AppDatabase
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

    /**
     * برای کارهای «فوری» (expedited) مثل دکمهٔ «اجرای دستی» لازم است؛ در گوشی‌های
     * اندروید ۱۱ و پایین‌تر WorkManager این تابع را صدا می‌زند تا سرویس پیش‌زمینه
     * را بسازد. نبودِ آن باعث شکستِ کار می‌شود.
     */
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

            // ۲) انتخاب افرادِ نوبتی
            val due = dao.getDueCustomers(settings.dailyCount)
            val delayMillis = settings.delaySeconds.coerceAtLeast(1) * 1000L

            for ((index, customer) in due.withIndex()) {
                // در اجرای زمان‌بندی‌شده، اگر از ساعتِ پایان گذشتیم بقیه به فردا موکول می‌شوند.
                if (!manual && isPastEndHour(settings.endHour)) break

                val ok = smsSender.send(customer.phoneNumber, settings.messageText)
                if (ok) {
                    dao.markSent(customer.phoneNumber, System.currentTimeMillis())
                }
                setForegroundSafe(index + 1, due.size)

                // بین پیام‌ها فاصله بگذار تا به محدودیت ارسالِ اندروید نخوریم.
                if (index < due.size - 1) {
                    delay(delayMillis)
                }
            }

            return Result.success()
        } finally {
            // فقط برای اجرای زمان‌بندی‌شده و در حالت فعال، اجرای فردا را زمان‌بندی کن.
            if (!manual && settings.enabled) {
                Scheduler.scheduleNext(context, settings.startHour)
            }
        }
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
