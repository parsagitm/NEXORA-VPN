package com.parsa.nexora

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun VpnControls(configText: String) {

    val context = LocalContext.current

    var status by remember {
        mutableStateOf("disconnected")
    }

    var message by remember {
        mutableStateOf("برای اتصال، کانفیگ VLESS وارد کن.")
    }

    var pendingConfig by remember {
        mutableStateOf("")
    }

    fun startVpn(config: String) {
        try {
            val intent = Intent(
                context,
                NexoraVpnService::class.java
            ).apply {
                action = NexoraVpnService.ACTION_START
                putExtra(
                    NexoraVpnService.EXTRA_CONFIG_JSON,
                    config
                )
            }

            ContextCompat.startForegroundService(
                context,
                intent
            )

            message = "درخواست اتصال ارسال شد."
        } catch (e: Exception) {
            status = "error"
            message = e.message ?: "شروع سرویس ناموفق بود."
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == android.app.Activity.RESULT_OK) {
                if (pendingConfig.isNotBlank()) {
                    startVpn(pendingConfig)
                }
            } else {
                status = "disconnected"
                message = "مجوز VPN داده نشد."
            }
        }

    DisposableEffect(context) {

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (intent?.action == NexoraVpnService.ACTION_STATUS) {

                    status = intent.getStringExtra(
                        NexoraVpnService.EXTRA_STATUS
                    ) ?: "error"

                    message = intent.getStringExtra(
                        NexoraVpnService.EXTRA_MESSAGE
                    ) ?: ""
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(NexoraVpnService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val connected = status == "running"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141D25)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = when (status) {
                    "running" -> "متصل"
                    "error" -> "خطا در اتصال"
                    else -> "قطع"
                },
                color = if (connected) {
                    Color(0xFF00E676)
                } else {
                    Color(0xFFFF7070)
                },
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = message,
                color = Color.White
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {

                    if (configText.isBlank()) {
                        message = "ابتدا کانفیگ را وارد و ذخیره کن."
                        return@Button
                    }

                    try {
                        val json = VlessConfigParser.convert(
                            configText.trim()
                        )

                        pendingConfig = json

                        val permission = VpnService.prepare(context)

                        if (permission == null) {
                            startVpn(json)
                        } else {
                            permissionLauncher.launch(permission)
                        }

                    } catch (e: Exception) {
                        status = "error"
                        message = e.message ?: "کانفیگ نامعتبر است."
                    }
                },
                enabled = !connected,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color(0xFF080D12)
                )
            ) {
                Text("اتصال VPN")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        context,
                        NexoraVpnService::class.java
                    ).apply {
                        action = NexoraVpnService.ACTION_STOP
                    }

                    context.startService(intent)
                },
                enabled = connected || status == "error",
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("قطع اتصال")
            }
        }
    }
}
