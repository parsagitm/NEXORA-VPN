package com.parsa.nexora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

private val Navy = Color(0xFF0B1428)
private val Panel = Color(0xFF172642)
private val Blue = Color(0xFF2196F3)
private val White = Color(0xFFFFFFFF)
private val Gray = Color(0xFF9EABC2)

private const val API =
    "https://nexora-api.parsaprogrammer6.workers.dev"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NexoraApp()
        }
    }
}

data class Server(
    val id: String,
    val name: String,
    val flag: String,
    val status: String
)

@Composable
fun NexoraApp() {
    var servers by remember { mutableStateOf(emptyList<Server>()) }
    var selected by remember { mutableStateOf("de-01") }
    var mode by remember { mutableStateOf("normal") }
    var apiStatus by remember { mutableStateOf("در حال اتصال...") }
    var connected by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val result = withContext(Dispatchers.IO) {
                JSONObject(URL("$API/api/servers").readText())
            }

            val array = result.getJSONArray("servers")
            val list = mutableListOf<Server>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    Server(
                        item.getString("id"),
                        item.getString("name"),
                        item.getString("flag"),
                        item.getString("status")
                    )
                )
            }

            servers = list
            apiStatus = "متصل"
        } catch (e: Exception) {
            apiStatus = "خطا در اتصال به API"
        }

        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            "NEXORA",
            color = White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            "YOUR CONNECTION. YOUR FREEDOM.",
            color = Blue,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(24.dp))

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
                Text("وضعیت اتصال", color = Gray)

                Spacer(Modifier.height(12.dp))

                Text(
                    if (connected) "حالت نمایشی فعال" else "قطع شده",
                    color = if (connected) Blue else White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = { connected = !connected },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                ) {
                    Text(
                        if (connected) "قطع اتصال نمایشی"
                        else "اتصال نمایشی",
                        fontSize = 17.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "این دکمه فعلاً VPN واقعی را فعال نمی‌کند.",
                    color = Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "انتخاب سرور",
            color = White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(10.dp))

        if (loading) {
            CircularProgressIndicator(color = Blue)
        } else if (servers.isEmpty()) {
            Text("سرورها دریافت نشدند", color = Gray)
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
                        containerColor = if (selected == server.id)
                            Color(0xFF203D68) else Panel
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(server.flag, fontSize = 25.sp)
                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(server.name, color = White)
                            Text(
                                if (server.status == "coming_soon")
                                    "به‌زودی"
                                else "آماده",
                                color = Gray,
                                fontSize = 12.sp
                            )
                        }

                        RadioButton(
                            selected = selected == server.id,
                            onClick = { selected = server.id },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Blue
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "حالت اتصال",
            color = White,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(8.dp))

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
                    onClick = { mode = item.first },
                    label = { Text(item.second) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "وضعیت API: $apiStatus",
            color = if (apiStatus == "متصل") Color(0xFF4CAF50) else Gray
        )

        Spacer(Modifier.weight(1f))

        Text(
            "NEXORA VPN  •  Version 0.1.0",
            color = Gray,
            fontSize = 11.sp
        )
    }
}
