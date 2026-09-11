package com.autosms.app

import android.app.Application
import com.autosms.app.work.Notifications

class AutoSmsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
    }
}
