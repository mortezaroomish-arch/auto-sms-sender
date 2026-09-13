package com.autosms.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.autosms.app.data.SettingsRepository
import com.autosms.app.sms.SmsSender
import com.autosms.app.util.AutoReplyRules
import com.autosms.app.util.PhoneUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * دریافتِ پیامک‌های ورودی برای دو قابلیت:
 *  ۱) «لغوِ اشتراکِ خودکار»: اگر متنِ پیام شاملِ کلمهٔ لغو باشد، فرستنده به لیستِ لغو اضافه می‌شود.
 *  ۲) «پاسخِ خودکار»: اگر روشن باشد، برای پیامِ ورودی یک جواب فرستاده می‌شود
 *     (اول قانون‌های کلیدواژه‌ای، وگرنه جوابِ پیش‌فرض). اگر پیام «لغو» بود، جوابِ خودکار فرستاده نمی‌شود.
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
                Log.d(
                    TAG,
                    "autoOptOut=${settings.autoOptOut}, keyword='${settings.optOutKeyword}', " +
                        "autoReply=${settings.autoReplyEnabled}"
                )
                // اگر هیچ‌کدام از دو قابلیت روشن نباشد، کاری نداریم.
                if (!settings.autoOptOut && !settings.autoReplyEnabled) return@launch

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return@launch
                val body = StringBuilder()
                var sender: String? = null
                for (m in messages) {
                    body.append(m.messageBody ?: "")
                    if (sender == null) sender = m.originatingAddress
                }
                val bodyText = body.toString()
                Log.d(TAG, "received from '$sender': '$bodyText'")
                if (sender == null) return@launch

                // ۱) لغوِ اشتراکِ خودکار
                var didOptOut = false
                if (settings.autoOptOut) {
                    val keyword = settings.optOutKeyword.ifBlank { "لغو" }.trim()
                    if (bodyText.contains(keyword)) {
                        val normalized = PhoneUtil.toLocal(sender)
                        repo.addOptedOut(normalized)
                        didOptOut = true
                        Log.d(TAG, "opted out added: $normalized")
                    }
                }

                // ۲) پاسخِ خودکار (اگر پیام «لغو» بود، جواب نمی‌فرستیم)
                if (settings.autoReplyEnabled && !didOptOut) {
                    val reply = AutoReplyRules.findReply(
                        body = bodyText,
                        rulesRaw = settings.autoReplyRules,
                        default = settings.autoReplyDefault
                    )
                    if (!reply.isNullOrBlank()) {
                        val ok = SmsSender(appContext).send(sender, reply)
                        Log.d(TAG, "auto-reply to '$sender' sent=$ok")
                    } else {
                        Log.d(TAG, "auto-reply: no matching rule and no default; nothing sent")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
