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
                    // فقط به شماره‌های موبایلِ شخصی جواب بده، نه به اپراتور/بانک/کدهای خدماتی.
                    if (!PhoneUtil.isReplyableSender(sender)) {
                        Log.d(TAG, "auto-reply skipped: '$sender' is not a personal mobile number")
                        return@launch
                    }

                    val reply = AutoReplyRules.findReply(
                        body = bodyText,
                        rulesRaw = settings.autoReplyRules,
                        default = settings.autoReplyDefault
                    )
                    if (reply.isNullOrBlank()) {
                        Log.d(TAG, "auto-reply: no matching rule and no default; nothing sent")
                        return@launch
                    }

                    // جلوگیری از ارسالِ مکرر/حلقه: در بازهٔ «فاصلهٔ پاسخ» بیش از یک بار جواب نده.
                    val local = PhoneUtil.toLocal(sender)
                    val now = System.currentTimeMillis()
                    val cooldownMs = settings.autoReplyCooldownMinutes.coerceAtLeast(0) * 60_000L
                    val last = repo.lastAutoReplyAt(local)
                    if (cooldownMs > 0 && last > 0 && now - last < cooldownMs) {
                        Log.d(TAG, "auto-reply skipped: cooldown active for $local")
                        return@launch
                    }

                    val ok = SmsSender(appContext).send(sender, reply)
                    if (ok) repo.recordAutoReply(local, now)
                    Log.d(TAG, "auto-reply to '$sender' sent=$ok")
                }
            } catch (e: Exception) {
                Log.e(TAG, "error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
