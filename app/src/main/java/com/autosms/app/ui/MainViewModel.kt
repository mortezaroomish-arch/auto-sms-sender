package com.autosms.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autosms.app.contacts.ContactRepository
import com.autosms.app.data.AppDatabase
import com.autosms.app.data.AppSettings
import com.autosms.app.data.BackupManager
import com.autosms.app.data.Customer
import com.autosms.app.data.SettingsRepository
import com.autosms.app.sms.SmsSender
import com.autosms.app.util.JalaliDate
import com.autosms.app.util.MessageTemplates
import com.autosms.app.util.PhoneUtil
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
    val sentToday: Int = 0,
    val sentThisWeek: Int = 0,
    val sentThisMonth: Int = 0,
    val totalSentEver: Int = 0
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

    /** زمانِ اجرای بعدی به‌صورتِ متنِ شمسی (یا «غیرفعال»). */
    private val _nextRun = MutableStateFlow("غیرفعال")
    val nextRun: StateFlow<String> = _nextRun.asStateFlow()

    /** تعداد شماره‌هایی که «لغو» کرده‌اند. */
    private val _optedOutCount = MutableStateFlow(0)
    val optedOutCount: StateFlow<Int> = _optedOutCount.asStateFlow()

    /** تاریخچهٔ ارسال (جدیدترین اول، حداکثر ۳۰۰ مورد). */
    private val _history = MutableStateFlow<List<Customer>>(emptyList())
    val history: StateFlow<List<Customer>> = _history.asStateFlow()

    /** عبارتِ جست‌وجوی مخاطب در صفحهٔ مدیریت. */
    private val _contactQuery = MutableStateFlow("")
    val contactQuery: StateFlow<String> = _contactQuery.asStateFlow()

    /** نتیجهٔ جست‌وجو / فهرستِ مخاطبین (حداکثر ۵۰ مورد). */
    private val _contactResults = MutableStateFlow<List<Customer>>(emptyList())
    val contactResults: StateFlow<List<Customer>> = _contactResults.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.value = settingsRepo.current()
            refreshStats()
            searchContacts()
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
            refreshStats()
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
                searchContacts()
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
            val base = MessageTemplates.variants(s.messageText).firstOrNull() ?: s.messageText.trim()
            val text = if (s.personalizeWithName) base.replace("(نام)", "دوست").replace("{نام}", "دوست") else base
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

    /** پاک‌کردنِ لیستِ کسانی که «لغو» کرده‌اند. */
    fun clearOptOut() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settingsRepo.clearOptedOut() }
            refreshStats()
            _message.value = "لیستِ لغو پاک شد."
        }
    }

    // ---- مدیریتِ دستیِ مخاطبین ----

    /** تغییرِ عبارتِ جست‌وجو و به‌روزرسانیِ فهرست. */
    fun setContactQuery(query: String) {
        _contactQuery.value = query
        searchContacts()
    }

    private fun searchContacts() {
        viewModelScope.launch {
            val q = _contactQuery.value.trim()
            val list = withContext(Dispatchers.IO) {
                if (q.isEmpty()) dao.listContacts(50)
                else dao.searchContacts("%$q%", 50)
            }
            _contactResults.value = list
        }
    }

    /** افزودنِ مخاطبِ جدید یا تغییرِ نامِ مخاطبِ موجود (بر اساسِ شماره). */
    fun addOrUpdateContact(name: String, phone: String) {
        viewModelScope.launch {
            val normalized = PhoneUtil.normalizeDigits(phone)
            if (normalized.filter { it.isDigit() }.length < 5) {
                _message.value = "شماره نامعتبر است."
                return@launch
            }
            val cleanName = name.trim().ifBlank { normalized }
            withContext(Dispatchers.IO) {
                val existing = dao.getByNumber(normalized)
                if (existing != null) {
                    dao.updateName(normalized, cleanName)
                } else {
                    dao.upsert(Customer(phoneNumber = normalized, name = cleanName))
                }
            }
            searchContacts()
            refreshStats()
            _message.value = "مخاطب ذخیره شد: $cleanName"
        }
    }

    /** حذفِ یک مخاطب. */
    fun deleteContact(phone: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.deleteByNumber(phone) }
            searchContacts()
            refreshStats()
            _message.value = "مخاطب حذف شد."
        }
    }

    // ---- پشتیبان‌گیری / بازیابی ----

    /** ذخیرهٔ پشتیبان در فایلی که کاربر انتخاب کرده است. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    BackupManager.toJson(
                        customers = dao.getAll(),
                        settings = settingsRepo.current(),
                        optedOut = settingsRepo.currentOptedOut(),
                        cycleStart = settingsRepo.currentCycleStart(),
                        cycleMessageIndex = settingsRepo.currentCycleMessageIndex()
                    )
                }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("خروجی باز نشد")
                }
                _message.value = "پشتیبان با موفقیت ذخیره شد."
            } catch (e: Exception) {
                _message.value = "خطا در پشتیبان‌گیری: ${e.message}"
            }
        }
    }

    /** بازیابیِ کاملِ داده‌ها از فایلِ پشتیبان (جایگزینِ داده‌های فعلی). */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } ?: throw IllegalStateException("فایل خوانده نشد")

                val backup = BackupManager.fromJson(text)
                withContext(Dispatchers.IO) {
                    dao.deleteAll()
                    if (backup.customers.isNotEmpty()) dao.insertAll(backup.customers)
                    settingsRepo.update(backup.settings)
                    settingsRepo.setOptedOut(backup.optedOut)
                    settingsRepo.setCycleStart(backup.cycleStart)
                    settingsRepo.setCycleMessageIndex(backup.cycleMessageIndex)
                }
                _settings.value = settingsRepo.current()
                searchContacts()
                refreshStats()
                _message.value = "بازیابی انجام شد: ${backup.customers.size} مخاطب."
            } catch (e: Exception) {
                _message.value = "خطا در بازیابی (فایل معتبر نیست؟): ${e.message}"
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val total = withContext(Dispatchers.IO) { dao.count() }
            val never = withContext(Dispatchers.IO) { dao.countNeverSent() }
            val today = withContext(Dispatchers.IO) { dao.countSince(startOfToday()) }
            val week = withContext(Dispatchers.IO) { dao.countSince(startOfWeek()) }
            val month = withContext(Dispatchers.IO) { dao.countSince(startOfMonth()) }
            val ever = withContext(Dispatchers.IO) { dao.countSent() }
            val recent = withContext(Dispatchers.IO) { dao.getRecentSent() }
            val optedOut = withContext(Dispatchers.IO) { settingsRepo.currentOptedOut().size }
            _history.value = recent
            _optedOutCount.value = optedOut
            _stats.value = Stats(total, never, today, week, month, ever)
            _nextRun.value = if (_settings.value.enabled) {
                JalaliDate.format(computeNextRun(_settings.value.startHour))
            } else {
                "غیرفعال"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun startOfMonth(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** شروعِ هفته: نزدیک‌ترین شنبه‌ی گذشته، ساعت ۰۰:۰۰ (هفته در ایران از شنبه شروع می‌شود). */
    private fun startOfWeek(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            c.add(Calendar.DAY_OF_YEAR, -1)
        }
        return c.timeInMillis
    }

    private fun computeNextRun(startHour: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }
}
