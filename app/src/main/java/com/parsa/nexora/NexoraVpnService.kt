
package com.parsa.nexora

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Base64
import android.util.Log
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONObject

class NexoraVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.parsa.nexora.START_VPN"
        const val ACTION_STOP = "com.parsa.nexora.STOP_VPN"
        const val ACTION_STATUS = "com.parsa.nexora.VPN_STATUS"

        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_STATUS = "status"
        const val EXTRA_MESSAGE = "message"

        private const val CHANNEL_ID = "nexora_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "NEXORA_VPN"
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

        when (intent?.action) {

            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val configJson =
                    intent.getStringExtra(EXTRA_CONFIG_JSON)

                if (configJson.isNullOrBlank()) {
                    stopVpn(false)
                    reportStatus("error", "کانفیگ خالی است.")
                    return START_NOT_STICKY
                }

                try {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        if (Build.VERSION.SDK_INT >= 34) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        } else {
                            0
                        }
                    )

                    startVpn(configJson)

                } catch (e: Exception) {

                    val errorMessage =
                        e.message?.takeIf { it.isNotBlank() }
                            ?: e.javaClass.simpleName
                            ?: "خطای ناشناخته در اتصال"

                    Log.e(TAG, "VPN startup failed", e)

                    stopVpn(false)

                    reportStatus(
                        "error",
                        "خطای اتصال: $errorMessage"
                    )
                }
            }

            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    private fun startVpn(configJson: String) {

        if (VpnService.prepare(this) != null) {
            throw IllegalStateException(
                "ابتدا باید مجوز VPN از اندروید گرفته شود."
            )
        }

        val json = JSONObject(configJson)

        val inbounds = json.optJSONArray("inbounds")
            ?: throw IllegalArgumentException(
                "بخش inbounds در کانفیگ وجود ندارد."
            )

        val outbounds = json.optJSONArray("outbounds")
            ?: throw IllegalArgumentException(
                "بخش outbounds در کانفیگ وجود ندارد."
            )

        var hasTun = false

        for (i in 0 until inbounds.length()) {
            if (
                inbounds.getJSONObject(i)
                    .optString("protocol") == "tun"
            ) {
                hasTun = true
                break
            }
        }

        if (!hasTun) {
            throw IllegalArgumentException(
                "ورودی TUN در کانفیگ وجود ندارد."
            )
        }

        if (outbounds.length() == 0) {
            throw IllegalArgumentException(
                "هیچ خروجی سروری در کانفیگ وجود ندارد."
            )
        }

        val builder = Builder()
            .setSession("NEXORA VPN")
            .setMtu(1500)
            .addAddress("10.0.0.2", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        vpnInterface = builder.establish()
            ?: throw IllegalStateException(
                "ساخت تونل VPN ناموفق بود."
            )

        Seq.setContext(applicationContext)

        val androidId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "nexora-device"

        val deviceId = Base64.encodeToString(
            androidId.toByteArray(Charsets.UTF_8)
                .copyOf(32),
            Base64.NO_PADDING or Base64.URL_SAFE
        )

        val assetPath = getDir(
            "assets",
            MODE_PRIVATE
        ).absolutePath

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

                val text = message
                    ?.takeIf { it.isNotBlank() }
                    ?: if (code == 0L) {
                        "موتور Xray فعال شد."
                    } else {
                        "موتور Xray خطا گزارش کرد. کد: $code"
                    }

                reportStatus(
                    if (code == 0L) "running" else "error",
                    text
                )

                Log.i(TAG, "Xray status $code: $text")

                return 0
            }
        }

        coreController =
            Libv2ray.newCoreController(callback)

        val fd = vpnInterface?.fd
            ?: throw IllegalStateException(
                "شناسه تونل VPN پیدا نشد."
            )

        coreController?.startLoop(configJson, fd)

        if (coreController?.isRunning != true) {
            throw IllegalStateException(
                "موتور Xray اجرا نشد."
            )
        }

        reportStatus(
            "running",
            "موتور VPN اجرا شد."
        )
    }

    private fun reportStatus(
        status: String,
        message: String
    ) {
        val intent = Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_MESSAGE, message)
        }

        sendBroadcast(intent)
    }

    private fun stopVpn(
        reportDisconnected: Boolean = true
    ) {

        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Xray", e)
        }

        coreController = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)

        if (reportDisconnected) {
            reportStatus(
                "disconnected",
                "اتصال VPN قطع شد."
            )
        }

        stopSelf()
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "NEXORA VPN",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error during service destruction", e)
        }

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interface on destroy", e)
        }

        coreController = null
        vpnInterface = null

        super.onDestroy()
    }
}
