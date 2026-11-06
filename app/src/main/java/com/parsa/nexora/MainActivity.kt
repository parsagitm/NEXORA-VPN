package com.parsa.nexora

import android.os.Bundle
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
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

private const val API =
    "https://nexora-api.parsakarimi92j.workers.dev"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("nexora", MODE_PRIVATE)
        val savedToken = prefs.getString("token", null) ?: ""

        setContent {
            NexoraApp(
                initialToken = savedToken,
                saveToken = { token ->
                    prefs.edit().putString("token", token).apply()
                },
                clearToken = {
                    prefs.edit().remove("token").apply()
                }
            )
        }
    }
}

data class Server(
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

    val connection =
        URL("$API$path").openConnection() as HttpURLConnection

    try {
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )
        }

        if (!token.isNullOrBlank()) {
            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )
        }

        if (body != null) {
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        }

        val code = connection.responseCode

        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val response = stream?.bufferedReader()?.use {
            it.readText()
        } ?: "{}"

        JSONObject(response)

    } finally {
        connection.disconnect()
    }
}

@Composable
fun NexoraApp(
    initialToken: String,
    saveToken: (String) -> Unit,
    clearToken: () -> Unit
) {
    var token by remember { mutableStateOf(initialToken) }
    var authenticated by remember { mutableStateOf(false) }
    var checkingSession by remember {
        mutableStateOf(initialToken.isNotBlank())
    }
val context = LocalContext.current

val configPrefs = remember {
    context.getSharedPreferences(
        "nexora_config",
        android.content.Context.MODE_PRIVATE
    )
}

var configText by remember {
    mutableStateOf(
        configPrefs.getString("config_text", "") ?: ""
    )
}

var configMessage by remember {
    mutableStateOf("")
}
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialToken) {
        if (initialToken.isNotBlank()) {
            try {
                val result = apiRequest(
                    "/api/me",
                    token = initialToken
                )

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

        checkingSession = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background,
            surface = Panel,
            primary = Green
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            when {
                checkingSession -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Green)
                    }
                }

                authenticated -> {
                    HomeScreen(
                        token = token,
                        onLogout = {
                            scope.launch {
                                try {
                                    apiRequest(
                                        "/api/logout",
                                        "POST",
                                        token = token
                                    )
                                } catch (_: Exception) {
                                }

                                clearToken()
                                token = ""
                                authenticated = false
                            }
                        }
                    )
                }

                else -> {
                    AuthScreen(
                        onSuccess = { newToken ->
                            token = newToken
                            saveToken(newToken)
                            authenticated = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen(onSuccess: (String) -> Unit) {

    var registerMode by remember { mutableStateOf(false) }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            "NEXORA",
            color = Green,
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            "YOUR CONNECTION. YOUR FREEDOM.",
            color = Gray,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(35.dp))

        Text(
            if (registerMode) "ساخت حساب کاربری"
            else "ورود به حساب کاربری",
            color = White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        if (registerMode) {
            InputField(
                value = firstName,
                onValueChange = { firstName = it },
                label = "نام"
            )

            InputField(
                value = lastName,
                onValueChange = { lastName = it },
                label = "نام خانوادگی"
            )
        }

        InputField(
            value = phone,
            onValueChange = { phone = it },
            label = "شماره موبایل",
            keyboard = KeyboardType.Phone
        )

        InputField(
            value = password,
            onValueChange = { password = it },
            label = "رمز عبور",
            password = true
        )

        Spacer(Modifier.height(15.dp))

        Button(
            onClick = {
                if (
                    phone.isBlank() ||
                    password.isBlank() ||
                    (registerMode &&
                        (firstName.isBlank() || lastName.isBlank()))
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

                        val path = if (registerMode) {
                            "/api/register"
                        } else {
                            "/api/login"
                        }

                        val result = apiRequest(
                            path,
                            "POST",
                            data
                        )

                        if (result.optBoolean("success")) {
                            val newToken = result.optString("token")

                            if (
                                newToken.isNotBlank() &&
                                newToken != "null"
                            ) {
                                onSuccess(newToken)
                            } else {
                                message = "توکن ورود از سرور دریافت نشد."
                            }
                        } else {
                            message = result.optString(
                                "message",
                                result.optString(
                                    "error",
                                    "عملیات ناموفق بود."
                                )
                            )
                        }

                    } catch (e: Exception) {
                        message = "خطا در ارتباط با سرور: ${e.message}"
                    }

                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Green,
                contentColor = Background
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Background
                )
            } else {
                Text(
                    if (registerMode) "ثبت‌نام" else "ورود",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(15.dp))

        Text(
            if (registerMode) {
                "قبلاً حساب ساخته‌ای؟ ورود"
            } else {
                "حساب کاربری نداری؟ ثبت‌نام"
            },
            color = Green,
            modifier = Modifier.clickable {
                registerMode = !registerMode
                message = ""
            }
        )

        if (message.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(
                message,
                color = Red,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun InputField(
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard
        ),
        visualTransformation = if (password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
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
fun HomeScreen(
    token: String,
    onLogout: () -> Unit
) {
    var servers by remember {
        mutableStateOf(emptyList<Server>())
    }

    var selected by remember { mutableStateOf("de-01") }
    var mode by remember { mutableStateOf("normal") }

    var volumeInput by remember { mutableStateOf("30") }
    var expiryInput by remember { mutableStateOf("30") }
    var note by remember { mutableStateOf("") }

    var apiStatus by remember {
        mutableStateOf("در حال اتصال...")
    }

    var loading by remember { mutableStateOf(true) }
    var sendingRequest by remember { mutableStateOf(false) }
    var showRequestDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val result = apiRequest("/api/servers")
            val array = result.getJSONArray("servers")
            val list = mutableListOf<Server>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                list.add(
                    Server(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        flag = item.getString("flag"),
                        status = item.getString("status")
                    )
                )
            }

            servers = list
            apiStatus = "متصل"

        } catch (_: Exception) {
            apiStatus = "خطا در دریافت سرورها"
        }

        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(20.dp))

        Text(
            "NEXORA",
            color = Green,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            "پنل کاربری",
            color = Gray
        )

        Spacer(Modifier.height(25.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("وضعیت VPN", color = Gray)

                Spacer(Modifier.height(12.dp))

                Text(
                    "غیرفعال",
                    color = White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    "اتصال واقعی VPN هنوز پیکربندی نشده است.",
                    color = Gray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "انتخاب سرور",
            color = White,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(10.dp))

        if (loading) {
            CircularProgressIndicator(color = Green)
        } else if (servers.isEmpty()) {
            Text(
                "سرورها دریافت نشدند",
                color = Gray
            )
        } else {
            servers.forEach { server ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable {
                            selected = server.id
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected == server.id) {
                            Color(0xFF18352A)
                        } else {
                            Panel
                        }
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            server.flag,
                            fontSize = 25.sp
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                server.name,
                                color = White
                            )

                            Text(
                                if (server.status == "coming_soon") {
                                    "به‌زودی"
                                } else {
                                    "آماده"
                                },
                                color = Gray,
                                fontSize = 12.sp
                            )
                        }

                        RadioButton(
                            selected = selected == server.id,
                            onClick = {
                                selected = server.id
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Green
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "حالت اتصال",
            color = White,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(
                "normal" to "عادی",
                "game" to "بازی",
                "social" to "اجتماعی"
            ).forEach { item ->

                FilterChip(
                    selected = mode == item.first,
                    onClick = {
                        mode = item.first
                    },
                    label = {
                        Text(item.second)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Green,
                        selectedLabelColor = Background
                    )
                )
            }
        }

        Spacer(Modifier.height(25.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "درخواست کانفیگ",
                    color = White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "حجم و مدت اعتبار را هنگام ثبت درخواست خودت مشخص می‌کنی.",
                    color = Gray,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(15.dp))

                Button(
                    onClick = {
                        volumeInput = "30"
                        expiryInput = "30"
                        note = ""
                        showRequestDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green,
                        contentColor = Background
                    )
                ) {
                    Text(
                        "ساخت کانفیگ جدید",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(15.dp))

        Text(
            "وضعیت API: $apiStatus",
            color = Green,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(25.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Red
            )
        ) {
            Text("خروج از حساب کاربری")
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "NEXORA VPN • Version 0.4.0",
            color = Gray,
            fontSize = 11.sp
        )
    }

    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!sendingRequest) {
                    showRequestDialog = false
                }
            },
            containerColor = Panel,
            title = {
                Text(
                    "درخواست کانفیگ جدید",
                    color = Green,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "حجم موردنیاز را به گیگابایت وارد کن.",
                        color = Gray,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = volumeInput,
                        onValueChange = {
                            if (
                                it.length <= 6 &&
                                it.all { char -> char.isDigit() }
                            ) {
                                volumeInput = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("حجم به گیگابایت")
                        },
                        suffix = {
                            Text("GB")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
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

                    Spacer(Modifier.height(15.dp))

                    Text(
                        "مدت اعتبار را به روز وارد کن.",
                        color = Gray,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = expiryInput,
                        onValueChange = {
                            if (
                                it.length <= 4 &&
                                it.all { char -> char.isDigit() }
                            ) {
                                expiryInput = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("مدت اعتبار")
                        },
                        suffix = {
                            Text("روز")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
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

                    Spacer(Modifier.height(15.dp))

                    OutlinedTextField(
                        value = note,
                        onValueChange = {
                            if (it.length <= 1000) {
                                note = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("توضیحات (اختیاری)")
                        },
                        minLines = 2,
                        maxLines = 4,
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

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "سرور انتخاب‌شده: $selected",
                        color = Gray,
                        fontSize = 12.sp
                    )

                    Text(
                        "حالت اتصال: $mode",
                        color = Gray,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !sendingRequest,
                    onClick = {
                        val volumeNumber =
                            volumeInput.toIntOrNull()

                        val expiryNumber =
                            expiryInput.toIntOrNull()

                        if (
                            volumeNumber == null ||
                            volumeNumber !in 1..100000
                        ) {
                            apiStatus =
                                "حجم باید بین ۱ تا ۱۰۰۰۰۰ گیگابایت باشد."
                        } else if (
                            expiryNumber == null ||
                            expiryNumber !in 1..3650
                        ) {
                            apiStatus =
                                "مدت اعتبار باید بین ۱ تا ۳۶۵۰ روز باشد."
                        } else {
                            sendingRequest = true

                            scope.launch {
                                try {
                                    val data = JSONObject().apply {
                                        put("server_id", selected)
                                        put("mode", mode)
                                        put("volume", "$volumeNumber GB")
                                        put("expiry", "$expiryNumber days")
                                        put("note", note.trim())
                                    }

                                    val result = apiRequest(
                                        "/api/config-request",
                                        "POST",
                                        data,
                                        token
                                    )

                                    if (result.optBoolean("success")) {
                                        val requestId =
                                            result.optString(
                                                "request_id",
                                                "-"
                                            )

                                        val emailSent =
                                            result.optBoolean("email_sent")

                                        apiStatus = if (emailSent) {
                                            "درخواست شماره $requestId ثبت شد و ایمیل ارسال شد."
                                        } else {
                                            "درخواست شماره $requestId ثبت شد؛ اما ارسال ایمیل ناموفق بود."
                                        }

                                        showRequestDialog = false
                                    } else {
                                        apiStatus = result.optString(
                                            "message",
                                            result.optString(
                                                "error",
                                                "ثبت درخواست ناموفق بود."
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    apiStatus =
                                        "خطا در ارسال درخواست: ${e.message}"
                                }

                                sendingRequest = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green,
                        contentColor = Background
                    )
                ) {
                    if (sendingRequest) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Background
                        )
                    } else {
                        Text("ارسال درخواست")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !sendingRequest,
                    onClick = {
                        showRequestDialog = false
                    }
                ) {
                    Text(
                        "انصراف",
                        color = Gray
                    )
                }
            }
        )
    }
}
