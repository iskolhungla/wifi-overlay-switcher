package com.iskol.wifioverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Держит подписку на смену состояния WiFi живой постоянно. Для этого действия
 * (android.net.wifi.STATE_CHANGE) статическая регистрация в манифесте не
 * работает начиная с Android 8 — доставляется только динамически
 * зарегистрированным приёмникам из живого процесса. Поэтому нужен отдельный
 * постоянно работающий сервис, не связанный с показом самого оверлея.
 *
 * Запускается/останавливается вместе с настройкой "Вести журнал подключений",
 * плюс автозапуск при перезагрузке (см. BootReceiver), если настройка включена.
 */
class WifiLogService : Service() {

    companion object {
        private const val CHANNEL_ID = "wifi_log_channel"
        private const val NOTIF_ID = 2

        fun start(context: Context) {
            val intent = Intent(context, WifiLogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WifiLogService::class.java))
        }
    }

    private val receiver = WifiStateReceiver()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        registerReceiver(receiver, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.log_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.log_notification_title))
            .setSmallIcon(R.drawable.ic_wifi_tile)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }
}
