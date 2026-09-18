package com.autosms.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.autosms.app.util.JalaliDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val nextRun by viewModel.nextRun.collectAsStateWithLifecycle()
    val optedOutCount by viewModel.optedOutCount.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val availableSims by viewModel.availableSims.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var testNumber by rememberSaveable { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var showRunDialog by remember { mutableStateOf(false) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var numbersExpanded by rememberSaveable { mutableStateOf(false) }
    var autoReplyExpanded by rememberSaveable { mutableStateOf(false) }
    var missedCallExpanded by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val receiveSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val phonePermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    // مجوزِ خواندنِ اطلاعاتِ سیم برای حالتِ دو سیم‌کارت؛ پس از اعطا، سیم‌ها دوباره خوانده می‌شوند.
    val simPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.loadSims() }

    // انتخابِ فایل برای ذخیره/خواندنِ پشتیبان
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("شروع دوره‌ی جدید؟") },
            text = { Text("تاریخِ ارسالِ همه پاک می‌شود و چرخه از ابتدا شروع می‌شود. مطمئنی؟") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetCycle()
                }) { Text("بله، پاک کن") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("انصراف") }
            }
        )
    }

    if (showRunDialog) {
        AlertDialog(
            onDismissRequest = { showRunDialog = false },
            title = { Text("ارسالِ دستی؟") },
            text = { Text("همین حالا تا ${settings.dailyCount} پیامک ارسال می‌شود. مطمئنی؟") },
            confirmButton = {
                TextButton(onClick = {
                    showRunDialog = false
                    viewModel.runNow()
                }) { Text("بله، ارسال کن") }
            },
            dismissButton = {
                TextButton(onClick = { showRunDialog = false }) { Text("انصراف") }
            }
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "📱 پیامک خودکار",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!permissionsGranted) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "برای کار برنامه، اجازهٔ ارسال پیامک و خواندن مخاطبین لازم است.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onRequestPermissions) {
                                Text("اعطای مجوزها")
                            }
                        }
                    }
                }

                // بنرِ وضعیت (رنگش بسته به فعال/خاموش بودن تغییر می‌کند)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.enabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (settings.enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (settings.enabled) "✅ ارسالِ خودکار فعال است" else "⏸️ ارسالِ خودکار خاموش است",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("⏰ اجرای بعدی: $nextRun", style = MaterialTheme.typography.bodyMedium)
                        Text("📨 ارسالِ امروز: ${stats.sentToday}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // آمار
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📊 آمار", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(label = "امروز", value = stats.sentToday)
                            StatItem(label = "این هفته", value = stats.sentThisWeek)
                            StatItem(label = "این ماه", value = stats.sentThisMonth)
                            StatItem(label = "کل", value = stats.totalSentEver)
                        }
                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))
                        Text("👥 مجموع مخاطبین: ${stats.totalCustomers}", style = MaterialTheme.typography.bodyMedium)
                        Text("🆕 هنوز پیام نگرفته‌اند: ${stats.neverSent}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.syncContacts() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                            } else {
                                Text("🔄 همگام‌سازی مخاطبین گوشی")
                            }
                        }
                    }
                }

                // تاریخچه‌ی ارسال (به‌صورتِ پیش‌فرض بسته تا لازم نباشد اسکرول کنی)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📜 تاریخچه‌ی ارسال", style = MaterialTheme.typography.titleMedium)
                            if (history.isNotEmpty()) {
                                TextButton(onClick = { historyExpanded = !historyExpanded }) {
                                    Text(if (historyExpanded) "بستن ▲" else "نمایش ▼")
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (history.isEmpty()) {
                            Text("هنوز پیامی ارسال نشده است.")
                        } else if (!historyExpanded) {
                            Text(
                                "${history.size} ارسالِ اخیر ذخیره شده. برای دیدن، «نمایش» را بزن.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "آخرین ${history.size} ارسال (جدیدترین اول):",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            history.forEach { customer ->
                                val date = customer.lastSentAt?.let { JalaliDate.format(it) } ?: "-"
                                Text(
                                    "• ${customer.name} — ${customer.phoneNumber}\n   $date",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.refreshStats() }) {
                                Text("به‌روزرسانی")
                            }
                            OutlinedButton(
                                onClick = { showResetDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB00020))
                            ) {
                                Text("شروع دوره‌ی جدید")
                            }
                        }
                    }
                }

                // فعال/غیرفعال
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌙 ارسال خودکار روزانه", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.enabled,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(enabled = checked) }
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "اجرای بعدی: $nextRun",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // لغوِ اشتراکِ خودکار
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🚫 لغوِ اشتراکِ خودکار", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.autoOptOut,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(autoOptOut = checked) }
                                    if (checked && ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECEIVE_SMS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        receiveSmsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                    }
                                }
                            )
                        }
                        if (settings.autoOptOut) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.optOutKeyword,
                            onValueChange = { text -> viewModel.updateSettings { it.copy(optOutKeyword = text) } },
                            label = { Text("کلمه‌ی لغو") },
                            placeholder = { Text("لغو") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("تعداد لغو کرده‌ها: $optedOutCount")
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { viewModel.clearOptOut() }) {
                            Text("پاک‌کردنِ لیستِ لغو")
                        }
                        }
                    }
                }

                // پاسخِ خودکار به پیامکِ ورودی
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🤖 پاسخِ خودکار", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.autoReplyEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(autoReplyEnabled = checked) }
                                    if (checked && ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECEIVE_SMS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        receiveSmsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                    }
                                }
                            )
                        }
                        TextButton(onClick = { autoReplyExpanded = !autoReplyExpanded }) {
                            Text(if (autoReplyExpanded) "بستن ▲" else "تنظیمات ▼")
                        }
                        if (autoReplyExpanded) {
                        Text(
                            "وقتی کسی پیامک بدهد، خودکار جواب داده می‌شود.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.autoReplyDefault,
                            onValueChange = { text -> viewModel.updateSettings { it.copy(autoReplyDefault = text) } },
                            label = { Text("جوابِ پیش‌فرض") },
                            placeholder = { Text("سلام، پیام‌تان دریافت شد؛ به‌زودی پاسخ می‌دهیم.") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.autoReplyRules,
                            onValueChange = { text -> viewModel.updateSettings { it.copy(autoReplyRules = text) } },
                            label = { Text("قانون‌های کلیدواژه‌ای (هر خط یک قانون)") },
                            placeholder = { Text("قیمت = هزینهٔ بازآموزی ... تومان است\nآدرس = تهران، خیابان ...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "هر خط: «کلیدواژه = جواب». فقط به موبایلِ واقعی و یک بار برای هر پیام.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        }
                    }
                }

                // پیامک برای تماسِ بی‌پاسخ
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📞 پیامک برای تماسِ بی‌پاسخ", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.missedCallReplyEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(missedCallReplyEnabled = checked) }
                                    if (checked) {
                                        val needed = mutableListOf<String>()
                                        if (ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.READ_PHONE_STATE
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) needed.add(Manifest.permission.READ_PHONE_STATE)
                                        if (ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.READ_CALL_LOG
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) needed.add(Manifest.permission.READ_CALL_LOG)
                                        if (needed.isNotEmpty()) phonePermsLauncher.launch(needed.toTypedArray())
                                    }
                                }
                            )
                        }
                        TextButton(onClick = { missedCallExpanded = !missedCallExpanded }) {
                            Text(if (missedCallExpanded) "بستن ▲" else "تنظیمات ▼")
                        }
                        if (missedCallExpanded) {
                        Text(
                            "برای تماسِ بی‌پاسخ، این متن خودکار فرستاده می‌شود.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.missedCallReplyText,
                            onValueChange = { text -> viewModel.updateSettings { it.copy(missedCallReplyText = text) } },
                            label = { Text("متنِ تماسِ بی‌پاسخ") },
                            placeholder = { Text("در اسرع وقت با شما تماس می‌گیرم") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        }
                    }
                }

                // شخصی‌سازی با نام (بالای متن‌ها، چون به متن مربوط است)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👤 شخصی‌سازی با نام", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.personalizeWithName,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(personalizeWithName = checked) }
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "«(نام)» در متن با نامِ مخاطب جایگزین می‌شود.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // متن‌های پیامک (هر متن یک کادرِ جدا، با دکمهٔ افزودن)
                MessageListEditor(
                    messageText = settings.messageText,
                    onChange = { joined -> viewModel.updateSettings { it.copy(messageText = joined) } }
                )

                // جلوگیری از تکرارِ متن در دوره‌ی بعد
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✍️ تغییرِ متن در هر دوره", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.sequentialMessages,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(sequentialMessages = checked) }
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "روشن: هر دوره یک متنِ متفاوت. خاموش: همیشه متنِ اول.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // دو سیم‌کارت — پخشِ پیام‌ها بینِ دو سیم برای ارسالِ بیشتر در روز
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📶 دو سیم‌کارت", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.dualSimEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(dualSimEnabled = checked) }
                                    if (checked) {
                                        if (ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.READ_PHONE_STATE
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            simPermLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                        } else {
                                            viewModel.loadSims()
                                        }
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "هر سیم را جداگانه کنترل کن: روشن/خاموش، سقفِ جدا و متنِ جدا. هر دو روشن باشند، پیام‌ها یکی‌درمیان از دو سیم می‌روند تا بتوانی بیش از حدِ یک سیم بفرستی.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (settings.dualSimEnabled) {
                            Spacer(Modifier.height(12.dp))
                            when {
                                availableSims.size < 2 -> {
                                    Text(
                                        "⚠️ دو سیم‌کارتِ فعال پیدا نشد. اگر گوشی دو سیم دارد، مجوزِ «تلفن» را بده و دوباره بررسی کن.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFB00020)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = {
                                        if (ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.READ_PHONE_STATE
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            simPermLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                        } else {
                                            viewModel.loadSims()
                                        }
                                    }) {
                                        Text("🔄 بررسیِ دوباره‌ی سیم‌ها")
                                    }
                                }
                                else -> {
                                    val sim1 = availableSims.firstOrNull { it.subscriptionId == settings.sim1SubId }
                                    val sim2 = availableSims.firstOrNull { it.subscriptionId == settings.sim2SubId }

                                    // ---- سیمِ اول ----
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "① سیمِ اول: ${sim1?.label ?: "—"}",
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = settings.sim1Enabled,
                                            onCheckedChange = { c -> viewModel.updateSettings { it.copy(sim1Enabled = c) } }
                                        )
                                    }
                                    if (settings.sim1Enabled) {
                                        SliderField(
                                            label = "سقفِ سیمِ اول در روز",
                                            value = settings.sim1DailyLimit,
                                            min = 1,
                                            max = 500,
                                            onValueChange = { v -> viewModel.updateSettings { it.copy(sim1DailyLimit = v) } }
                                        )
                                        Text(
                                            "متنِ سیمِ اول = همان «متن‌های پیامک» بالا.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))
                                    Divider()
                                    Spacer(Modifier.height(12.dp))

                                    // ---- سیمِ دوم ----
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "② سیمِ دوم: ${sim2?.label ?: "—"}",
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = settings.sim2Enabled,
                                            onCheckedChange = { c -> viewModel.updateSettings { it.copy(sim2Enabled = c) } }
                                        )
                                    }
                                    if (settings.sim2Enabled) {
                                        SliderField(
                                            label = "سقفِ سیمِ دوم در روز",
                                            value = settings.sim2DailyLimit,
                                            min = 1,
                                            max = 500,
                                            onValueChange = { v -> viewModel.updateSettings { it.copy(sim2DailyLimit = v) } }
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = settings.sim2MessageText,
                                            onValueChange = { text -> viewModel.updateSettings { it.copy(sim2MessageText = text) } },
                                            label = { Text("متنِ سیمِ دوم (خالی = مثلِ متنِ اصلی)") },
                                            placeholder = { Text("متنِ مخصوصِ سیمِ دوم را این‌جا بنویس") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(onClick = {
                                        viewModel.updateSettings {
                                            it.copy(sim1SubId = it.sim2SubId, sim2SubId = it.sim1SubId)
                                        }
                                    }) {
                                        Text("🔁 جابه‌جاییِ سیمِ اول و دوم")
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "هر دو روشن = پیام‌ها یکی‌درمیان از هر دو سیم. فقط یکی روشن = همه از همان سیم. هر سیم تا سقفِ خودش می‌فرستد.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                // فیلترِ پیش‌شماره (با کلیدِ روشن/خاموش)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📞 پیش‌شماره‌های ارسالِ پیامک", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.prefixFilterEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(prefixFilterEnabled = checked) }
                                }
                            )
                        }
                        if (settings.prefixFilterEnabled) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = settings.numberPrefixes,
                                onValueChange = { text -> viewModel.updateSettings { it.copy(numberPrefixes = text) } },
                                label = { Text("پیش‌شماره‌ها") },
                                placeholder = { Text("0912,0919") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // فیلترِ شماره‌های استثنا (با کلیدِ روشن/خاموش)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🚷 شماره‌هایی که پیام نگیرند", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.excludedFilterEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(excludedFilterEnabled = checked) }
                                }
                            )
                        }
                        if (settings.excludedFilterEnabled) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = settings.excludedNumbers,
                                onValueChange = { text -> viewModel.updateSettings { it.copy(excludedNumbers = text) } },
                                label = { Text("هر شماره یک خط") },
                                placeholder = { Text("09120000000\n09350000000") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                    }
                }

                // زمان‌بندی و تعداد — پیش‌فرض بسته تا موقعِ اسکرول اسلایدرها تغییر نکنند
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏰ زمان‌بندی و تعداد", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { numbersExpanded = !numbersExpanded }) {
                                Text(if (numbersExpanded) "بستن ▲" else "تغییر ▼")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (!numbersExpanded) {
                            val delayText = if (settings.delayMaxSeconds > settings.delaySeconds) {
                                "${settings.delaySeconds} تا ${settings.delayMaxSeconds}"
                            } else {
                                "${settings.delaySeconds}"
                            }
                            Text(
                                "تعداد: ${settings.dailyCount}  •  ساعت: ${settings.startHour} تا ${settings.endHour}  •  فاصله: $delayText ثانیه",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "برای تغییر، «تغییر» را بزن.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            SliderField(
                                label = "تعداد ارسال در هر روز",
                                value = settings.dailyCount,
                                min = 1,
                                max = 600,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(dailyCount = v) } }
                            )
                            SliderField(
                                label = "ساعت شروع",
                                value = settings.startHour,
                                min = 0,
                                max = 23,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(startHour = v) } }
                            )
                            SliderField(
                                label = "ساعت پایان",
                                value = settings.endHour,
                                min = 0,
                                max = 23,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(endHour = v) } }
                            )
                            SliderField(
                                label = "فاصله بین پیام‌ها (ثانیه)",
                                value = settings.delaySeconds,
                                min = 5,
                                max = 300,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(delaySeconds = v) } }
                            )
                            SliderField(
                                label = "حداکثرِ فاصله (ثانیه) — ۰ یعنی ثابت",
                                value = settings.delayMaxSeconds,
                                min = 0,
                                max = 300,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(delayMaxSeconds = v) } }
                            )
                            Text(
                                "اگر بزرگ‌تر از «فاصله» باشد، فاصلهٔ ارسال تصادفی می‌شود (طبیعی‌تر).",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("💾 ذخیرهٔ تنظیمات")
                }

                Divider()

                // تست و اجرای دستی
                Text("🧪 آزمایش و اجرا", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = testNumber,
                    onValueChange = { testNumber = it },
                    label = { Text("شماره برای پیامک آزمایشی") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { viewModel.sendTest(testNumber) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✉️ ارسال پیامک آزمایشی")
                }
                OutlinedButton(
                    onClick = { showRunDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▶️ اجرای دستی یک دور (الان)")
                }
                Button(
                    onClick = { viewModel.stopSending() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
                ) {
                    Text("🛑 توقفِ ارسال")
                }

                Divider()

                // پشتیبان‌گیری و بازیابی
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("💾 پشتیبان‌گیری و بازیابی", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "همه‌چیز در یک فایل ذخیره/بازیابی می‌شود. «بازیابی» جایگزینِ داده‌های فعلی می‌شود.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch("auto_sms_backup.json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ذخیرهٔ پشتیبان")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("بازیابی")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * ویرایشگرِ چند متنِ پیامک: هر متن یک کادرِ جدا + دکمهٔ «افزودن متن» + حذفِ هر متن.
 * متن‌ها داخلاً با جداکنندهٔ «---» در یک رشته ذخیره می‌شوند تا با بقیهٔ برنامه سازگار بماند.
 */
@Composable
private fun MessageListEditor(
    messageText: String,
    onChange: (String) -> Unit
) {
    val separator = "\n---\n"
    val messages = remember { mutableStateListOf<String>() }
    // هم‌گام‌سازی با منبع: هنگام بارگذاری یا تغییرِ بیرونی، لیست را از رشته بساز.
    LaunchedEffect(messageText) {
        val joined = messages.joinToString(separator)
        if (messageText != joined) {
            val parsed = if (messageText.isEmpty()) listOf("") else messageText.split(separator)
            messages.clear()
            messages.addAll(parsed.ifEmpty { listOf("") })
        } else if (messages.isEmpty()) {
            messages.add("")
        }
    }

    var messagesExpanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✉️ متن‌های پیامک", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { messagesExpanded = !messagesExpanded }) {
                    Text(if (messagesExpanded) "بستن ▲" else "تغییر ▼")
                }
            }
            Spacer(Modifier.height(4.dp))
            if (!messagesExpanded) {
                Text(
                    "تعداد متن‌ها: ${messages.count { it.isNotBlank() }} — برای نوشتن/ویرایش «تغییر» را بزن.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "برای هر متن یک کادر. با «افزودن متن» چند متن داشته باش.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                messages.forEachIndexed { index, msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("متن ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    if (messages.size > 1) {
                        TextButton(onClick = {
                            messages.removeAt(index)
                            onChange(messages.joinToString(separator))
                        }) {
                            Text("حذف", color = Color(0xFFB00020))
                        }
                    }
                }
                OutlinedTextField(
                    value = msg,
                    onValueChange = {
                        messages[index] = it
                        onChange(messages.joinToString(separator))
                    },
                    placeholder = { Text("سلام دکتر (نام)، ...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(12.dp))
            }
                OutlinedButton(
                    onClick = {
                        messages.add("")
                        onChange(messages.joinToString(separator))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("➕ افزودن متن")
                }
            }
        }
    }
}

/** یک عددِ آماری با نمایشِ درشت و برچسبِ زیرش. */
@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * انتخابِ عدد به‌صورتِ کشویی (اسلایدر) به‌جای تایپ. مقدار در بالا نمایش داده می‌شود
 * و دکمه‌های − و + برای تنظیمِ دقیقِ یک‌واحدی هم کنارِ آن هست.
 */
@Composable
private fun SliderField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val current = value.coerceIn(min, max)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { onValueChange((current - 1).coerceIn(min, max)) }) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                current.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = { onValueChange((current + 1).coerceIn(min, max)) }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
        Slider(
            value = current.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat()
        )
    }
}
