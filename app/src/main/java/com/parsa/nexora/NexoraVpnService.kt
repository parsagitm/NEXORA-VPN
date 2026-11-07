package com.parsa.nexora

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Base64
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONObject

class NexoraVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.parsa.nexora.START_VPN"
        const val ACTION_STOP = "com.parsa.nexora.STOP_VPN"
        const val EXTRA_CONFIG_JSON = "config_json"

        private const val CHANNEL_ID = "nexora_vpn"
        private const val NOTIFICATION_ID = 1001
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }

        val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON)

        if (configJson.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            JSONObject(configJson)

            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                if (Build.VERSION.SDK_INT >= 34) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )

            startVpn(configJson)

        } catch (e: Exception) {
            stopVpn()
        }

        return START_NOT_STICKY
    }

    private fun startVpn(configJson: String) {

        if (VpnService.prepare(this) != null) {
            stopVpn()
            return
        }

        val builder = Builder()
            .setSession("NEXORA VPN")
            .setMtu(1500)
            .addAddress("10.0.0.2", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        vpnInterface = builder.establish()
            ?: throw IllegalStateException("ساخت تونل VPN ناموفق بود")

        Seq.setContext(applicationContext)

        val androidId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "nexora-device"

        val deviceId = Base64.encodeToString(
            androidId.toByteArray(Charsets.UTF_8).copyOf(32),
            Base64.NO_PADDING or Base64.URL_SAFE
        )

        val assetPath = getDir("assets", MODE_PRIVATE).absolutePath

        Libv2ray.initCoreEnv(assetPath, deviceId)

        val callback = object : CoreCallbackHandler {

            override fun startup(): Long {
                return 0
            }

            override fun shutdown(): Long {
                return 0
            }

            override fun onEmitStatus(
                code: Long,
                message: String?
            ): Long {
                return 0
            }
        }

        coreController = Libv2ray.newCoreController(callback)

        val fd = vpnInterface?.fd
            ?: throw IllegalStateException("شناسه تونل VPN پیدا نشد")

        coreController?.startLoop(configJson, fd)

        if (coreController?.isRunning != true) {
            throw IllegalStateException("موتور Xray اجرا نشد")
        }
    }

    private fun stopVpn() {
        try {
            coreController?.stopLoop()
        } catch (_: Exception) {
        }

        coreController = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }

        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NEXORA VPN",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(
                NotificationManager::class.java
            )

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("NEXORA VPN")
            .setContentText("سرویس VPN در حال اجرا است")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
