package com.autosms.app.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/** ارسال پیامک با مدیریت پیام‌های طولانی (چندبخشی). */
class SmsSender(private val context: Context) {

    private val smsManager: SmsManager
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    /** یک پیامک را ارسال می‌کند. در صورت بروز خطا، false برمی‌گرداند. */
    fun send(phoneNumber: String, message: String): Boolean {
        return try {
            val manager = smsManager
            val parts = manager.divideMessage(message)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                manager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
