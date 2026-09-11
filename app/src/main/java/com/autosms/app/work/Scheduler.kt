package com.autosms.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * زمان‌بندی اجرای روزانه.
 *
 * از یک کار «یک‌بارِ خود-زمان‌بند» استفاده می‌شود: هر بار که کار اجرا می‌شود،
 * در پایانِ کار دوباره خودش را برای فردا در ساعتِ شروع زمان‌بندی می‌کند. این روش
 * نسبت به کار دوره‌ای WorkManager دقت زمانی بهتری دارد.
 */
object Scheduler {

    const val UNIQUE_WORK_NAME = "daily_sms_work"

    /** کار بعدی را برای نزدیک‌ترین ساعتِ شروعِ پیشِ‌رو زمان‌بندی می‌کند. */
    fun scheduleNext(context: Context, startHour: Int) {
        val delayMillis = millisUntilNext(startHour)
        val request = OneTimeWorkRequestBuilder<SmsWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** زمان‌بندی را لغو می‌کند. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun millisUntilNext(startHour: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // اگر ساعتِ شروعِ امروز گذشته باشد، برای فردا زمان‌بندی کن.
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
