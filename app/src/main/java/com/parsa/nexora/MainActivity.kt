package com.parsa.nexora

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Background = Color(0xFF080D12)
private val Panel = Color(0xFF141D25)
private val Green = Color(0xFF00E676)
private val White = Color.White
private val Gray = Color(0xFF9EABB5)
private val Red = Color(0xFFFF7070)
private const val API = "https://nexora-api.parsakarimi92j.workers.dev"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nexora", MODE_PRIVATE)
        val savedToken = prefs.getString("token", "") ?: ""
        setContent {
            NexoraApp(
                initialToken = savedToken,
                saveToken = { value -> prefs.edit().putString("token", value).apply() },
                clearToken = { prefs.edit().remove("token").apply() }
            )
        }
    }
}

private data class Server(
    val id: String,
    val name: String,
    val flag: String,
    val status: String
)

private suspend fun apiRequest(
    path: String,
    method: String = "GET",
    body: JSONObject? = null,
    token: String? = null
): JSONObject = withContext(Dispatchers.IO) {
    val connection = URL("$API$path").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        JSONObject(response)
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun NexoraApp(
    initialToken: String,
    saveToken: (String) -> Unit,
    clearToken: () -> Unit
) {
    var token by remember { mutableStateOf(initialToken) }
    var authenticated by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(initialToken.isNotBlank()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialToken) {
        if (initialToken.isNotBlank()) {
            try {
                val result = apiRequest("/api/me", token = initialToken)
                if (result.optBoolean("success")) {
                    token = initialToken
                    authenticated = true
                } else {
                    clearToken()
                    token = ""
                }
            } catch (_: Exception) {
                clearToken()
                token = ""
            }
        }
        checking = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background,
            surface = Panel,
            primary = Green
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            when {
                checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green)
                }
                authenticated -> HomeScreen(
                    token = token,
                    onLogout = {
                        scope.launch {
                            try {
                                apiRequest("/api/logout", "POST", token = token)
                            } catch (_: Exception) {
                            }
                            clearToken()
                            token = ""
                            authenticated = false
                        }
                    }
                )
                else -> AuthScreen { newToken ->
                    token = newToken
                    saveToken(newToken)
                    authenticated = true
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(onSuccess: (String) -> Unit) {
    var registerMode by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("NEXORA", color = Green, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
        Text("YOUR CONNECTION. YOUR FREEDOM.", color = Gray, fontSize = 11.sp)
        Spacer(Modifier.height(30.dp))
        Text(
            if (registerMode) "ساخت حساب کاربری" else "ورود به حساب کاربری",
            color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(18.dp))
        if (registerMode) {
            NexoraInput(firstName, { firstName = it }, "نام")
            NexoraInput(lastName, { lastName = it }, "نام خانوادگی")
        }
        NexoraInput(phone, { phone = it }, "شماره موبایل", keyboard = KeyboardType.Phone)
        NexoraInput(password, { password = it }, "رمز عبور", password = true)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (phone.isBlank() || password.isBlank() ||
                    (registerMode && (firstName.isBlank() || lastName.isBlank()))
                ) {
                    message = "لطفاً تمام فیلدها را تکمیل کن."
                    return@Button
                }
                if (registerMode && password.length < 8) {
                    message = "رمز عبور باید حداقل ۸ کاراکتر باشد."
                    return@Button
                }
                loading = true
                message = ""
                scope.launch {
                    try {
                        val data = JSONObject().apply {
                            put("phone", phone.trim())
                            put("password", password)
                            if (registerMode) {
                                put("first_name", firstName.trim())
                                put("last_name", lastName.trim())
                            }
                        }
                        val path = if (registerMode) "/api/register" else "/api/login"
                        val result = apiRequest(path, "POST", data)
                        if (result.optBoolean("success")) {
                            val newToken = result.optString("token")
                            if (newToken.isNotBlank() && newToken != "null") {
                                onSuccess(newToken)
                            } else {
                                message = "توکن ورود از سرور دریافت نشد."
                            }
                        } else {
                            message = result.optString(
                                "message",
                                result.optString("error", "عملیات ناموفق بود.")
                            )
                        }
                    } catch (e: Exception) {
                        message = "خطا در ارتباط با سرور: ${e.message}"
                    }
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Background),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Background)
            else Text(if (registerMode) "ثبت‌نام" else "ورود", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(15.dp))
        Text(
            if (registerMode) "قبلاً حساب ساخته‌ای؟ ورود" else "حساب کاربری نداری؟ ثبت‌نام",
            color = Green,
            modifier = Modifier.clickable { registerMode = !registerMode; message = "" }
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(message, color = Red, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NexoraInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = White,
            unfocusedTextColor = White,
            focusedBorderColor = Green,
            unfocusedBorderColor = Gray,
            focusedLabelColor = Green,
            unfocusedLabelColor = Gray,
            cursorColor = Green
        )
    )
}

@Composable
private fun HomeScreen(token: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val configPrefs = remember {
        context.getSharedPreferences("nexora_config", Context.MODE_PRIVATE)
    }
    var configText by remember { mutableStateOf(configPrefs.getString("config_text", "") ?: "") }
    var configMessage by remember { mutableStateOf("") }
    var vpnStatus by remember { mutableStateOf("disconnected") }
    var vpnMessage by remember { mutableStateOf("آماده اتصال") }
    var connectedSeconds by remember { mutableStateOf(0L) }
    var servers by remember { mutableStateOf(emptyList<Server>()) }
    var selected by remember { mutableStateOf("de-01") }
    var mode by remember { mutableStateOf("normal") }
    var volumeInput by remember { mutableStateOf("30") }
    var expiryInput by remember { mutableStateOf("30") }
    var deviceCountInput by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var apiStatus by remember { mutableStateOf("در حال دریافت اطلاعات...") }
    var loading by remember { mutableStateOf(true) }
    var sendingRequest by remember { mutableStateOf(false) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var autoConnect by remember { mutableStateOf(configPrefs.getBoolean("auto_connect", false)) }
    val scope = rememberCoroutineScope()

    val startVpn: () -> Unit = {
        val jsonConfig = configPrefs.getString("config_json", null)
        if (jsonConfig.isNullOrBlank()) {
            vpnMessage = "ابتدا کانفیگ VLESS را ذخیره کن."
        } else {
            val serviceIntent = Intent(context, NexoraVpnService::class.java).apply {
                action = NexoraVpnService.ACTION_START
                putExtra(NexoraVpnService.EXTRA_CONFIG_JSON, jsonConfig)
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
                vpnStatus = "connecting"
                vpnMessage = "در حال ارسال درخواست اتصال..."
            } catch (e: Exception) {
                vpnStatus = "error"
                vpnMessage = "شروع سرویس ناموفق بود: ${e.message}"
            }
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpn()
        } else {
            vpnStatus = "disconnected"
            vpnMessage = "مجوز اتصال VPN رد شد."
        }
    }

    val connectVpn: () -> Unit = {
        if (configPrefs.getString("config_json", null).isNullOrBlank()) {
            vpnMessage = "ابتدا کانفیگ را وارد و ذخیره کن."
        } else {
            val permissionIntent = VpnService.prepare(context)
            if (permissionIntent != null) vpnPermissionLauncher.launch(permissionIntent)
            else startVpn()
        }
    }

    val disconnectVpn: () -> Unit = {
        val serviceIntent = Intent(context, NexoraVpnService::class.java).apply {
            action = NexoraVpnService.ACTION_STOP
        }
        try {
            context.startService(serviceIntent)
            vpnMessage = "درخواست قطع اتصال ارسال شد."
        } catch (e: Exception) {
            vpnMessage = "قطع سرویس ناموفق بود: ${e.message}"
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == NexoraVpnService.ACTION_STATUS) {
                    vpnStatus = intent.getStringExtra(NexoraVpnService.EXTRA_STATUS) ?: "disconnected"
                    vpnMessage = intent.getStringExtra(NexoraVpnService.EXTRA_MESSAGE) ?: "وضعیت نامشخص"
                }
            }
        }
        val filter = IntentFilter(NexoraVpnService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(vpnStatus) {
        if (vpnStatus == "running" || vpnStatus == "connected") {
            connectedSeconds = 0L
            while (vpnStatus == "running" || vpnStatus == "connected") {
                delay(1000)
                connectedSeconds += 1L
            }
        } else {
            connectedSeconds = 0L
        }
    }

    LaunchedEffect(autoConnect) {
        if (autoConnect) connectVpn()
    }

    LaunchedEffect(Unit) {
        try {
            val result = apiRequest("/api/servers", token = token)
            val array = result.optJSONArray("servers")
            val list = mutableListOf<Server>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    list.add(
                        Server(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            flag = item.optString("flag", "🌐"),
                            status = item.optString("status", "coming_soon")
                        )
                    )
                }
            }
            servers = list
            if (list.isNotEmpty() && list.none { it.id == selected }) selected = list.first().id
            apiStatus = "متصل به API"
        } catch (e: Exception) {
            apiStatus = "خطا در دریافت سرورها: ${e.message ?: ""}"
        }
        loading = false
    }

    val selectedServer = servers.firstOrNull { it.id == selected }
    val isActive = vpnStatus == "connected" || vpnStatus == "running"

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("NEXORA", color = Green, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("پنل کاربری", color = Gray)
        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("NEXORA VPN", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier.size(164.dp).clip(CircleShape)
                        .background(if (isActive) Color(0xFF075B43) else Color(0xFF263445))
                        .clickable {
                            if (isActive || vpnStatus == "connecting") disconnectVpn() else connectVpn()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isActive) HeartbeatEffect(Modifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                isActive -> "قطع اتصال"
                                vpnStatus == "connecting" -> "در حال اتصال"
                                else -> "اتصال"
                            },
                            color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold
                        )
                        Text("NEXORA", color = White, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(vpnMessage, color = Gray, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    when (vpnStatus) {
                        "connected", "running" -> "متصل / موتور فعال"
                        "connecting" -> "در حال برقراری ارتباط"
                        "error" -> "خطا در اتصال"
                        else -> "قطع شده"
                    },
                    color = when (vpnStatus) {
                        "connected", "running" -> Color(0xFF20C997)
                        "error" -> Red
                        else -> Gray
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isActive) {
                    Spacer(Modifier.height(10.dp))
                    Text("مدت اتصال: ${formatDuration(connectedSeconds)}", color = Green, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "سرور کانفیگ: ${selectedServer?.name ?: "از داخل لینک VLESS"}",
                    color = Gray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = autoConnect,
                onCheckedChange = {
                    autoConnect = it
                    configPrefs.edit().putBoolean("auto_connect", it).apply()
                },
                colors = CheckboxDefaults.colors(checkedColor = Green)
            )
            Column {
                Text("اتصال خودکار", color = White, fontWeight = FontWeight.SemiBold)
                Text("هنگام باز شدن پنل، اتصال ذخیره‌شده را آغاز کن", color = Gray, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("انتخاب سرور")
        if (loading) {
            CircularProgressIndicator(color = Green)
        } else if (servers.isEmpty()) {
            Text("سرورها دریافت نشدند", color = Gray)
        } else {
            servers.forEach { server ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        .clickable { selected = server.id },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected == server.id) Color(0xFF18352A) else Panel
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(server.flag, fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(server.name, color = White)
                            Text(
                                if (server.status == "coming_soon") "به‌زودی" else "آماده",
                                color = Gray,
                                fontSize = 12.sp
                            )
                        }
                        RadioButton(
                            selected = selected == server.id,
                            onClick = { selected = server.id },
                            colors = RadioButtonDefaults.colors(selectedColor = Green)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("حالت اتصال")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Triple("normal", "NORMAL", "عادی"),
                Triple("game", "GAME", "بازی"),
                Triple("social", "SOCIAL", "اجتماعی")
            ).forEach { item ->
                FilterChip(
                    selected = mode == item.first,
                    onClick = { mode = item.first },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(item.second, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(item.third, fontSize = 9.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Green,
                        selectedLabelColor = Background
                    )
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("درخواست کانفیگ", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("حجم، مدت اعتبار و تعداد دستگاه را مشخص کن.", color = Gray, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        volumeInput = "30"
                        expiryInput = "30"
                        deviceCountInput = "1"
                        note = ""
                        showRequestDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Background)
                ) {
                    Text("ساخت کانفیگ جدید", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("کانفیگ من", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("لینک VLESS دریافتی از مدیر را اینجا وارد کن.", color = Gray, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = configText,
                    onValueChange = { configText = it; configMessage = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("لینک کانفیگ VLESS") },
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = Green,
                        unfocusedBorderColor = Gray,
                        focusedLabelColor = Green,
                        unfocusedLabelColor = Gray,
                        cursorColor = Green
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            configText = clip.getItemAt(0).coerceToText(context).toString()
                            configMessage = if (configText.isBlank()) "کلیپ‌بورد خالی است." else "متن وارد شد؛ حالا ذخیره کن."
                        } else {
                            configMessage = "کلیپ‌بورد خالی است."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
                ) {
                    Text("چسباندن از کلیپ‌بورد")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (configText.isBlank()) {
                            configMessage = "ابتدا کانفیگ را وارد کن."
                        } else {
                            try {
                                val rawConfig = configText.trim()
                                val jsonConfig = VlessConfigParser.convert(rawConfig)
                                configPrefs.edit()
                                    .putString("config_text", rawConfig)
                                    .putString("config_json", jsonConfig)
                                    .apply()
                                configText = rawConfig
                                configMessage = "کانفیگ VLESS معتبر است و ذخیره شد."
                            } catch (e: Exception) {
                                configMessage = e.message ?: "کانفیگ نامعتبر است."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Background)
                ) {
                    Text("ذخیره کانفیگ")
                }
                if (configMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(configMessage, color = Green, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "فعلاً تبدیل و ذخیره لینک VLESS فعال است؛ سرور انتخابی در فرم درخواست ثبت می‌شود و اتصال از اطلاعات داخل لینک استفاده می‌کند.",
                    color = Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("درباره ما", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("ساخته شده توسط: @Kurdestan_qorveh", color = Green, modifier = Modifier.clickable {
                    openExternal(context, "https://t.me/Kurdestan_qorveh")
                })
                Spacer(Modifier.height(8.dp))
                Text("کانال تلگرام: @qorveh_mobile_parsa", color = Green, modifier = Modifier.clickable {
                    openExternal(context, "https://t.me/qorveh_mobile_parsa")
                })
                Spacer(Modifier.height(8.dp))
                Text("پیج اینستاگرام: qorveh_mobile_parsa", color = Green, modifier = Modifier.clickable {
                    openExternal(context, "https://www.instagram.com/qorveh_mobile_parsa?dlrf=MWxwbmF4cXpoY3Y4dw==")
                })
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("وضعیت API: $apiStatus", color = Green, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
        ) {
            Text("خروج از حساب کاربری")
        }
        Spacer(Modifier.height(18.dp))
        Text("NEXORA VPN • Version 0.1.0", color = Gray, fontSize = 11.sp)
    }

    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { if (!sendingRequest) showRequestDialog = false },
            containerColor = Panel,
            title = { Text("درخواست کانفیگ جدید", color = Green, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = volumeInput,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) volumeInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("حجم به گیگابایت") },
                        suffix = { Text("GB") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = expiryInput,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) expiryInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("مدت اعتبار") },
                        suffix = { Text("روز") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = deviceCountInput,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) deviceCountInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("تعداد دستگاه‌های همزمان") },
                        suffix = { Text("دستگاه") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= 1000) note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("توضیحات (اختیاری)") },
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("سرور: ${selectedServer?.name ?: selected}", color = Gray, fontSize = 12.sp)
                    Text("حالت: ${mode.uppercase()}", color = Gray, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    enabled = !sendingRequest,
                    onClick = {
                        val volume = volumeInput.toIntOrNull()
                        val expiry = expiryInput.toIntOrNull()
                        val deviceCount = deviceCountInput.toIntOrNull() ?: 0
                        when {
                            volume == null || volume !in 1..100000 -> apiStatus = "حجم باید بین ۱ تا ۱۰۰۰۰۰ باشد."
                            expiry == null || expiry !in 1..3650 -> apiStatus = "مدت باید بین ۱ تا ۳۶۵۰ روز باشد."
                            deviceCount !in 1..10 -> apiStatus = "تعداد دستگاه باید بین ۱ تا ۱۰ باشد."
                            else -> {
                                sendingRequest = true
                                scope.launch {
                                    try {
                                        val data = JSONObject().apply {
                                            put("server_id", selected)
                                            put("mode", mode)
                                            put("volume", "$volume GB")
                                            put("expiry", "$expiry days")
                                            put("device_count", deviceCount)
                                            put("note", note.trim())
                                        }
                                        val result = apiRequest("/api/config-request", "POST", data, token)
                                        if (result.optBoolean("success")) {
                                            val id = result.optString("request_id", "-")
                                            apiStatus = if (result.optBoolean("email_sent")) {
                                                "درخواست $id ثبت و ایمیل ارسال شد."
                                            } else {
                                                "درخواست $id ثبت شد؛ ارسال ایمیل ناموفق بود."
                                            }
                                            showRequestDialog = false
                                        } else {
                                            apiStatus = result.optString(
                                                "message",
                                                result.optString("error", "ثبت درخواست ناموفق بود.")
                                            )
                                        }
                                    } catch (e: Exception) {
                                        apiStatus = "خطا در ارسال درخواست: ${e.message}"
                                    }
                                    sendingRequest = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Background)
                ) {
                    if (sendingRequest) CircularProgressIndicator(Modifier.size(18.dp), color = Background)
                    else Text("ارسال درخواست")
                }
            },
            dismissButton = {
                TextButton(enabled = !sendingRequest, onClick = { showRequestDialog = false }) {
                    Text("انصراف", color = Gray)
                }
            }
        )
    }
}

@Composable
private fun HeartbeatEffect(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "nexora-heartbeat")
    val progress by transition.animateFloat(
        initialValue = 1.15f,
        targetValue = -0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "heartbeat-progress"
    )
    Canvas(modifier) {
        val y = size.height / 2f
        val x = progress * size.width
        val path = Path().apply {
            moveTo(x + 46f, y)
            lineTo(x + 30f, y)
            lineTo(x + 22f, y - 10f)
            lineTo(x + 14f, y + 17f)
            lineTo(x + 4f, y - 28f)
            lineTo(x - 5f, y + 5f)
            lineTo(x - 18f, y)
            lineTo(x - 46f, y)
        }
        drawPath(path, color = Green.copy(alpha = 0.22f), style = Stroke(width = 9f))
        drawPath(path, color = Green, style = Stroke(width = 3f))
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun openExternal(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        color = White,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    )
}
