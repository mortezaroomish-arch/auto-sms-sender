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

    /**
     * SmsManagerِ مربوط به یک سیمِ مشخص (بر اساسِ subscriptionId).
     *
     * ⚠️ برخلافِ قبل، اگر ساختِ managerِ اختصاصی ممکن نباشد، **به سیمِ پیش‌فرض برنمی‌گردیم**
     * بلکه استثنا پرتاب می‌شود؛ این‌طور اگر ارسال از سیمِ انتخابی ممکن نباشد، به‌جای اینکه
     * بی‌سروصدا از سیمِ پیش‌فرض برود و «موفق» گزارش شود، صادقانه شکست گزارش می‌شود.
     */
    private fun managerForSubscription(subscriptionId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
                .createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }

    /**
     * یک پیامک را ارسال می‌کند. در صورت بروز خطا، false برمی‌گرداند.
     *
     * @param subscriptionId شناسه‌ی سیمی که پیامک باید از آن ارسال شود. مقدارِ ‑۱ (پیش‌فرض)
     *   یعنی از سیمِ پیش‌فرضِ سیستم فرستاده شود (رفتارِ قبلی).
     */
    fun send(phoneNumber: String, message: String, subscriptionId: Int = -1): Boolean {
        return try {
            val number = normalizeNumber(phoneNumber)
            if (number.isEmpty()) {
                Log.e(TAG, "شماره نامعتبر است: '$phoneNumber'")
                return false
            }
            val manager = if (subscriptionId < 0) smsManager else managerForSubscription(subscriptionId)
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
