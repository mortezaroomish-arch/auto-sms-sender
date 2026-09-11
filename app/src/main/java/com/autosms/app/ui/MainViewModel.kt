package com.autosms.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autosms.app.contacts.ContactRepository
import com.autosms.app.data.AppDatabase
import com.autosms.app.data.AppSettings
import com.autosms.app.data.SettingsRepository
import com.autosms.app.sms.SmsSender
import com.autosms.app.work.Scheduler
import com.autosms.app.work.SmsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Stats(
    val totalCustomers: Int = 0,
    val neverSent: Int = 0
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val contactRepo = ContactRepository(app)
    private val dao = AppDatabase.get(app).customerDao()
    private val smsSender = SmsSender(app)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.value = settingsRepo.current()
            refreshStats()
        }
    }

    fun updateSettings(update: (AppSettings) -> AppSettings) {
        _settings.value = update(_settings.value)
    }

    fun save() {
        viewModelScope.launch {
            val s = _settings.value
            settingsRepo.update(s)
            if (s.enabled) {
                Scheduler.scheduleNext(getApplication(), s.startHour)
                _message.value = "تنظیمات ذخیره شد و زمان‌بندی روزانه فعال شد."
            } else {
                Scheduler.cancel(getApplication())
                _message.value = "تنظیمات ذخیره شد. ارسال خودکار غیرفعال است."
            }
        }
    }

    /** خواندن مخاطبین گوشی و افزودن مواردِ جدید به پایگاه‌داده. */
    fun syncContacts() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val contacts = withContext(Dispatchers.IO) { contactRepo.readAllContacts() }
                withContext(Dispatchers.IO) {
                    if (contacts.isNotEmpty()) dao.insertNew(contacts)
                }
                refreshStats()
                _message.value = "همگام‌سازی انجام شد. مجموع مخاطبین: ${_stats.value.totalCustomers}"
            } catch (e: Exception) {
                _message.value = "خطا در خواندن مخاطبین: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    /** ارسال یک پیامکِ آزمایشی به شمارهٔ واردشده. */
    fun sendTest(phoneNumber: String) {
        if (phoneNumber.isBlank()) {
            _message.value = "لطفاً یک شماره برای تست وارد کنید."
            return
        }
        viewModelScope.launch {
            val text = _settings.value.messageText
            if (text.isBlank()) {
                _message.value = "متن پیامک خالی است."
                return@launch
            }
            val ok = withContext(Dispatchers.IO) { smsSender.send(phoneNumber, text) }
            _message.value = if (ok) "پیامک آزمایشی ارسال شد." else "ارسال پیامک آزمایشی ناموفق بود."
        }
    }

    /** اجرای فوریِ یک دور کامل (بدون توجه به ساعت). */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<SmsWorker>()
            .setInputData(workDataOf(SmsWorker.KEY_MANUAL to true))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(getApplication()).enqueue(request)
        _message.value = "اجرای دستی شروع شد. وضعیت را در اعلان‌ها ببینید."
    }

    fun refreshStats() {
        viewModelScope.launch {
            val total = withContext(Dispatchers.IO) { dao.count() }
            val never = withContext(Dispatchers.IO) { dao.countNeverSent() }
            _stats.value = Stats(total, never)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
