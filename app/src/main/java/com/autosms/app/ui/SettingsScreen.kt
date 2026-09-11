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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var testNumber by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
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

                // وضعیت
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("وضعیت", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("مجموع مخاطبین ذخیره‌شده: ${stats.totalCustomers}")
                        Text("هنوز پیام نگرفته‌اند: ${stats.neverSent}")
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

                // فعال/غیرفعال
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                }

                // متن پیامک
                OutlinedTextField(
                    value = settings.messageText,
                    onValueChange = { text -> viewModel.updateSettings { it.copy(messageText = text) } },
                    label = { Text("متن پیامک") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                // تعداد روزانه
                NumberField(
                    label = "تعداد ارسال در هر روز",
                    value = settings.dailyCount,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(dailyCount = v) } }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        label = "ساعت شروع (۰ تا ۲۳)",
                        value = settings.startHour,
                        onValueChange = { v ->
                            viewModel.updateSettings { it.copy(startHour = v.coerceIn(0, 23)) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "ساعت پایان (۰ تا ۲۳)",
                        value = settings.endHour,
                        onValueChange = { v ->
                            viewModel.updateSettings { it.copy(endHour = v.coerceIn(0, 23)) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                NumberField(
                    label = "فاصله بین پیام‌ها (ثانیه)",
                    value = settings.delaySeconds,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(delaySeconds = v) } }
                )

                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ذخیرهٔ تنظیمات")
                }

                Divider()

                // تست و اجرای دستی
                Text("آزمایش", style = MaterialTheme.typography.titleMedium)
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
                    onClick = { viewModel.runNow() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اجرای دستی یک دور (الان)")
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            val n = text.filter { it.isDigit() }.toIntOrNull() ?: 0
            onValueChange(n)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}
