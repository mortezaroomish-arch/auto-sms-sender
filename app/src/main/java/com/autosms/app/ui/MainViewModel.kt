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
import com.autosms.app.data.Customer
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
import java.util.Calendar

data class Stats(
    val totalCustomers: Int = 0,
    val neverSent: Int = 0,
    val sentToday: Int = 0
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

    /** تاریخچهٔ ارسال (جدیدترین اول، حداکثر ۳۰۰ مورد). */
    private val _history = MutableStateFlow<List<Customer>>(emptyList())
    val history: StateFlow<List<Customer>> = _history.asStateFlow()

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
            val s = _settings.value
            if (s.messageText.isBlank()) {
                _message.value = "متن پیامک خالی است."
                return@launch
            }
            // برای تست، اگر شخصی‌سازی روشن است، {نام} با یک نمونه جایگزین می‌شود.
            val text = if (s.personalizeWithName) s.messageText.replace("{نام}", "دوست") else s.messageText
            val ok = withContext(Dispatchers.IO) { smsSender.send(phoneNumber, text) }
            _message.value = if (ok) "پیامک آزمایشی ارسال شد." else "ارسال پیامک آزمایشی ناموفق بود."
        }
    }

    /** اجرای فوریِ یک دور کامل (بدون توجه به ساعت). */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<SmsWorker>()
            .setInputData(workDataOf(SmsWorker.KEY_MANUAL to true))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(Scheduler.TAG_SMS)
            .build()
        WorkManager.getInstance(getApplication()).enqueue(request)
        _message.value = "اجرای دستی شروع شد. وضعیت را در اعلان‌ها ببینید."
    }

    /** توقفِ فوریِ هر ارسالِ در حالِ اجرا و لغوِ زمان‌بندی. */
    fun stopSending() {
        val wm = WorkManager.getInstance(getApplication())
        wm.cancelAllWorkByTag(Scheduler.TAG_SMS)
        Scheduler.cancel(getApplication())
        _message.value = "ارسال متوقف شد. برای فعال‌سازی دوباره، تنظیمات را ذخیره کنید."
    }

    /** پاک‌کردنِ تاریخچه و شروعِ دوباره‌ی چرخه. */
    fun resetCycle() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.clearAllSent() }
            refreshStats()
            _message.value = "تاریخچه پاک شد؛ چرخه از ابتدا شروع می‌شود."
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val total = withContext(Dispatchers.IO) { dao.count() }
            val never = withContext(Dispatchers.IO) { dao.countNeverSent() }
            val today = withContext(Dispatchers.IO) { dao.getSentSince(startOfToday()).size }
            val recent = withContext(Dispatchers.IO) { dao.getRecentSent() }
            _history.value = recent
            _stats.value = Stats(total, never, today)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** نیمه‌شبِ امروز به میلی‌ثانیه (برای شمارشِ ارسال‌های امروز). */
    private fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
