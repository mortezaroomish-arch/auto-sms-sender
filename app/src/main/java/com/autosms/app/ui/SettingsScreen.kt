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
    val contactQuery by viewModel.contactQuery.collectAsStateWithLifecycle()
    val contactResults by viewModel.contactResults.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var testNumber by rememberSaveable { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var showRunDialog by remember { mutableStateOf(false) }
    var newContactName by rememberSaveable { mutableStateOf("") }
    var newContactPhone by rememberSaveable { mutableStateOf("") }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val receiveSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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
            topBar = { TopAppBar(title = { Text("پیامک خودکار") }) },
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

                // وضعیت و آمار
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("وضعیت و آمار", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("مجموع مخاطبین ذخیره‌شده: ${stats.totalCustomers}")
                        Text("هنوز پیام نگرفته‌اند: ${stats.neverSent}")
                        Text("ارسال‌های امروز: ${stats.sentToday}")
                        Text("ارسال در این ماه: ${stats.sentThisMonth}")
                        Text("کلِ ارسال‌شده‌ها تا حالا: ${stats.totalSentEver}")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.syncContacts() }, enabled = !busy) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                            } else {
                                Text("همگام‌سازی مخاطبین گوشی")
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
                            Text("تاریخچه‌ی ارسال", style = MaterialTheme.typography.titleMedium)
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

                // مدیریتِ مخاطبین
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("مدیریتِ مخاطبین", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "افزودنِ دستیِ مخاطب یا حذفِ مخاطب. اگر شماره‌ای که وارد می‌کنی از قبل باشد، فقط نامش به‌روز می‌شود.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newContactName,
                            onValueChange = { newContactName = it },
                            label = { Text("نام") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newContactPhone,
                            onValueChange = { newContactPhone = it },
                            label = { Text("شماره") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.addOrUpdateContact(newContactName, newContactPhone)
                                newContactName = ""
                                newContactPhone = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("افزودن / ذخیرهٔ مخاطب")
                        }

                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = contactQuery,
                            onValueChange = { viewModel.setContactQuery(it) },
                            label = { Text("جست‌وجوی مخاطب (نام یا شماره)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        if (contactResults.isEmpty()) {
                            Text("مخاطبی برای نمایش نیست.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                "نمایشِ ${contactResults.size} مخاطب:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            contactResults.forEach { customer ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "• ${customer.name} — ${customer.phoneNumber}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { viewModel.deleteContact(customer.phoneNumber) }
                                    ) {
                                        Text("حذف", color = Color(0xFFB00020))
                                    }
                                }
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
                            Text("ارسال خودکار روزانه", style = MaterialTheme.typography.titleMedium)
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

                // شخصی‌سازی با نام
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("شخصی‌سازی با نام", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = settings.personalizeWithName,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(personalizeWithName = checked) }
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "اگر روشن باشد، هر جای متن که {نام} بنویسی با نام مخاطب جایگزین می‌شود. " +
                                "مثال: «سلام دکتر {نام} عزیز». اگر خاموش باشد، متن یکسان به همه ارسال می‌شود.",
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
                            Text("لغوِ اشتراکِ خودکار", style = MaterialTheme.typography.titleMedium)
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "اگر روشن باشد، هرکس در جواب این کلمه را بفرستد، خودکار از لیست حذف می‌شود " +
                                "و دیگر پیام نمی‌گیرد. (نیاز به مجوزِ «دریافت پیامک» دارد.)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.optOutKeyword,
                            onValueChange = { text -> viewModel.updateSettings { it.copy(optOutKeyword = text) } },
                            label = { Text("کلمه‌ی لغو") },
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

                // متن پیامک
                OutlinedTextField(
                    value = settings.messageText,
                    onValueChange = { text -> viewModel.updateSettings { it.copy(messageText = text) } },
                    label = { Text("متن پیامک") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Text(
                    "می‌توانی چند متنِ متفاوت بنویسی و بینشان یک خط با «---» بگذاری؛ هنگامِ هر ارسال " +
                        "یکی به‌صورتِ تصادفی انتخاب می‌شود. مثال:\n" +
                        "سلام دکتر {نام}، جلسهٔ بازآموزی...\n---\nدرود دکتر {نام}، برنامهٔ این هفته...",
                    style = MaterialTheme.typography.bodySmall
                )

                // فیلتر پیش‌شماره
                OutlinedTextField(
                    value = settings.numberPrefixes,
                    onValueChange = { text -> viewModel.updateSettings { it.copy(numberPrefixes = text) } },
                    label = { Text("فقط این پیش‌شماره‌ها (خالی = همه)") },
                    placeholder = { Text("مثال: 0912,0919") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // لیست استثنا
                OutlinedTextField(
                    value = settings.excludedNumbers,
                    onValueChange = { text -> viewModel.updateSettings { it.copy(excludedNumbers = text) } },
                    label = { Text("شماره‌هایی که پیام نگیرند (هر شماره یک خط)") },
                    placeholder = { Text("مثال:\n09120000000\n09350000000") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                // تعداد روزانه
                SliderField(
                    label = "تعداد ارسال در هر روز",
                    value = settings.dailyCount,
                    min = 1,
                    max = 500,
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
                    "اگر این عدد از «فاصله بین پیام‌ها» بزرگ‌تر باشد، فاصلهٔ هر ارسال به‌صورتِ " +
                        "تصادفی بینِ این دو انتخاب می‌شود (مثلاً ۶۰ تا ۹۰). این‌طور ارسال طبیعی‌تر " +
                        "به نظر می‌رسد و کمتر شبیهِ اسپم می‌شود.",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ذخیرهٔ تنظیمات")
                }

                Divider()

                // تست و اجرای دستی
                Text("آزمایش و اجرا", style = MaterialTheme.typography.titleMedium)
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
                    Text("ارسال پیامک آزمایشی")
                }
                OutlinedButton(
                    onClick = { showRunDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اجرای دستی یک دور (الان)")
                }
                Button(
                    onClick = { viewModel.stopSending() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
                ) {
                    Text("توقفِ ارسال")
                }

                Divider()

                // پشتیبان‌گیری و بازیابی
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("پشتیبان‌گیری و بازیابی", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "همهٔ مخاطبین، تنظیمات، تاریخچه و لیستِ لغو در یک فایل ذخیره می‌شود. " +
                                "اگر گوشی عوض یا برنامه پاک شد، از همین فایل بازیابی کن.\n" +
                                "توجه: «بازیابی» داده‌های فعلی را با محتوای فایل جایگزین می‌کند.",
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
