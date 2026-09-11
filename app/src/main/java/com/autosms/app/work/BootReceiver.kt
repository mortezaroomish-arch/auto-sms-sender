package com.autosms.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autosms.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** پس از روشن شدنِ دوبارهٔ گوشی، زمان‌بندی روزانه را دوباره برقرار می‌کند. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(appContext).current()
                if (settings.enabled) {
                    Scheduler.scheduleNext(appContext, settings.startHour)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
