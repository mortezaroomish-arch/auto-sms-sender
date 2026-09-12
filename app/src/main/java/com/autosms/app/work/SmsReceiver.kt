package com.autosms.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.autosms.app.data.SettingsRepository
import com.autosms.app.util.PhoneUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * دریافتِ پیامک‌های ورودی برای «لغوِ اشتراکِ خودکار».
 * اگر متنِ پیام شاملِ کلمهٔ لغو باشد، فرستنده به لیستِ لغو اضافه می‌شود و
 * دیگر پیامی نمی‌گیرد. فقط وقتی فعال است که کلیدِ «لغوِ اشتراکِ خودکار» روشن باشد.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository(appContext)
                val settings = repo.current()
                if (!settings.autoOptOut) return@launch

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return@launch
                val body = StringBuilder()
                var sender: String? = null
                for (m in messages) {
                    body.append(m.messageBody ?: "")
                    if (sender == null) sender = m.originatingAddress
                }

                val keyword = settings.optOutKeyword.ifBlank { "لغو" }.trim()
                if (sender != null && body.toString().contains(keyword)) {
                    repo.addOptedOut(PhoneUtil.toLocal(sender!!))
                }
            } catch (_: Exception) {
                // نادیده می‌گیریم تا برنامه کرش نکند.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
