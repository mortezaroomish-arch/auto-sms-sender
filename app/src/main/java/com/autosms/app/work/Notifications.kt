package com.autosms.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autosms.app.R

object Notifications {
    const val CHANNEL_ID = "sms_sending_channel"
    const val FOREGROUND_NOTIFICATION_ID = 1001
    const val COMPLETION_NOTIFICATION_ID = 1002

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ارسال پیامک",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش وضعیت ارسال خودکار پیامک"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /** اعلانِ پایانِ یک دور ارسال. */
    fun showCompletion(context: Context, sent: Int, failed: Int) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("ارسال پیامک تمام شد")
                .setContentText("موفق: $sent — ناموفق: $failed")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(COMPLETION_NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // اگر اجازهٔ اعلان داده نشده باشد نادیده می‌گیریم.
        }
    }
}
