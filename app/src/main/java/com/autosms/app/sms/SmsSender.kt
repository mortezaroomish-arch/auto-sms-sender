package com.autosms.app.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

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
            val number = normalizeNumber(phoneNumber)
            if (number.isEmpty()) {
                Log.e(TAG, "شماره نامعتبر است: '$phoneNumber'")
                return false
            }
            val manager = smsManager
            val parts = manager.divideMessage(message)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(number, null, parts, null, null)
            } else {
                manager.sendTextMessage(number, null, message, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "خطا در ارسال پیامک به '$phoneNumber': ${e.message}", e)
            false
        }
    }

    /**
     * شماره را برای ارسال آماده می‌کند: اعداد فارسی/عربی را به انگلیسی تبدیل می‌کند،
     * فاصله و خط‌تیره و پرانتز و کاراکترهای اضافه را حذف می‌کند و در صورت وجود،
     * علامت + ابتدایی را نگه می‌دارد.
     */
    private fun normalizeNumber(raw: String): String {
        val sb = StringBuilder()
        for (c in raw.trim()) {
            when {
                c == '+' && sb.isEmpty() -> sb.append('+')
                Character.isDigit(c) -> sb.append(Character.digit(c, 10))
            }
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "SmsSender"
    }
}
