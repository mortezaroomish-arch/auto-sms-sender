package com.autosms.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import com.autosms.app.data.SettingsRepository
import com.autosms.app.sms.SmsSender
import com.autosms.app.util.PhoneUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * تشخیصِ «تماسِ بی‌پاسخ» و ارسالِ پیامکِ خودکار به تماس‌گیرنده.
 *
 * منطق بر اساسِ تغییرِ وضعیتِ تلفن است:
 *  - RINGING: تلفن زنگ می‌خورد (تماسِ ورودی). شماره را ذخیره می‌کنیم.
 *  - OFFHOOK: تماس برقرار شد (جواب داده شد یا تماسِ خروجی) → پس بی‌پاسخ نیست.
 *  - IDLE: تماس تمام شد. اگر «زنگ خورده بود ولی جواب داده نشد» → تماسِ بی‌پاسخ → پیامک بفرست.
 *
 * تماس‌های خروجی (که RINGING ندارند) و تماس‌های جواب‌داده‌شده پیامک نمی‌گیرند.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        // شماره فقط وقتی در اینتنت هست که مجوزِ READ_CALL_LOG داده شده باشد.
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                wasRinging = true
                answered = false
                if (!number.isNullOrBlank()) incomingNumber = number
                Log.d(TAG, "RINGING (number=${if (number.isNullOrBlank()) "?" else "captured"})")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // تماس برقرار شد → دیگر بی‌پاسخ نیست.
                answered = true
                Log.d(TAG, "OFFHOOK (answered)")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val missed = wasRinging && !answered
                val callerRaw = incomingNumber
                // ریستِ وضعیت برای تماسِ بعدی
                wasRinging = false
                answered = false
                incomingNumber = null
                Log.d(TAG, "IDLE (missed=$missed)")
                if (missed) handleMissedCall(context.applicationContext, callerRaw)
            }
        }
    }

    private fun handleMissedCall(appContext: Context, callerRaw: String?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository(appContext)
                val settings = repo.current()
                if (!settings.missedCallReplyEnabled) {
                    Log.d(TAG, "missed-call reply disabled")
                    return@launch
                }
                val text = settings.missedCallReplyText.trim()
                if (text.isEmpty()) {
                    Log.d(TAG, "missed-call reply text empty; nothing sent")
                    return@launch
                }

                // شماره را از اینتنت بگیر؛ اگر نبود، از گزارشِ تماس‌ها.
                val number = callerRaw?.takeIf { it.isNotBlank() } ?: latestMissedCallNumber(appContext)
                if (number == null || !PhoneUtil.isReplyableSender(number)) {
                    Log.d(TAG, "missed-call reply skipped: no valid personal number")
                    return@launch
                }

                val local = PhoneUtil.toLocal(number)
                val now = System.currentTimeMillis()
                // ضدِ اسپم: اگر همین چند دقیقه‌ی اخیر به این شماره پیامک داده‌ایم، دوباره نده.
                if (repo.countMissedCallRepliesSince(local, now - COOLDOWN_MS) >= 1) {
                    Log.d(TAG, "missed-call reply skipped: cooldown active for $local")
                    return@launch
                }

                val ok = SmsSender(appContext).send(number, text)
                if (ok) repo.recordMissedCallReply(local, now)
                Log.d(TAG, "missed-call reply to '$number' sent=$ok")
            } catch (e: Exception) {
                Log.e(TAG, "error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** آخرین شماره‌ی تماسِ بی‌پاسخ را از گزارشِ تماس‌ها می‌خواند (نیاز به READ_CALL_LOG). */
    private fun latestMissedCallNumber(context: Context): String? {
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    if (type == CallLog.Calls.MISSED_TYPE) {
                        return cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "call log read error: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "PhoneStateReceiver"

        /** ضدِ اسپم: در این بازه (میلی‌ثانیه) فقط یک پیامک به هر شماره. */
        private const val COOLDOWN_MS = 10 * 60 * 1000L

        // وضعیتِ تماس بینِ broadcastها. در طولِ یک تماس، پروسه معمولاً زنده می‌ماند.
        @Volatile private var wasRinging = false
        @Volatile private var answered = false
        @Volatile private var incomingNumber: String? = null
    }
}
