package com.autosms.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.autosms.app.data.SettingsRepository
import com.autosms.app.util.PhoneUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * دریافتِ پیامک‌های ورودی برای «لغوِ اشتراکِ خودکار».
 * اگر متنِ پیام شاملِ کلمهٔ لغو باشد، فرستنده به لیستِ لغو اضافه می‌شود.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository(appContext)
                val settings = repo.current()
                Log.d(TAG, "autoOptOut = ${settings.autoOptOut}, keyword = '${settings.optOutKeyword}'")
                if (!settings.autoOptOut) return@launch

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return@launch
                val body = StringBuilder()
                var sender: String? = null
                for (m in messages) {
                    body.append(m.messageBody ?: "")
                    if (sender == null) sender = m.originatingAddress
                }
                val bodyText = body.toString()
                Log.d(TAG, "received from '$sender': '$bodyText'")

                val keyword = settings.optOutKeyword.ifBlank { "لغو" }.trim()
                if (sender != null && bodyText.contains(keyword)) {
                    val normalized = PhoneUtil.toLocal(sender!!)
                    repo.addOptedOut(normalized)
                    Log.d(TAG, "opted out added: $normalized")
                } else {
                    Log.d(TAG, "keyword '$keyword' not found in message")
                }
            } catch (e: Exception) {
                Log.e(TAG, "error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
