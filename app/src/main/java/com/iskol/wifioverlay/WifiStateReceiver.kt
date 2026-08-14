package com.iskol.wifioverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Ловит переключение WiFi-сети от кого угодно — системных настроек, другого
 * приложения, авто-переключения, Magisk-модуля — не только от нашего оверлея.
 * Android сам не хранит историю IP/канала/скорости по сетям, поэтому фиксируем
 * это сами при каждом подключении.
 */
class WifiStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WifiOverlayConnect"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return

        @Suppress("DEPRECATION")
        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
        if (networkInfo?.state != NetworkInfo.State.CONNECTED) return

        val pendingResult = goAsync()
        Thread {
            try {
                recordConnection(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "WifiStateReceiver failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun recordConnection(context: Context) {
        val ssid = RootWifiControl.currentSsid() ?: return
        val now = System.currentTimeMillis()

        val scanEntry = RootWifiControl.scanNetworks().firstOrNull { it.ssid == ssid }
        val width = IwScanReader.readChannelWidths()[ssid]

        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val dhcp = wifiManager.dhcpInfo
        @Suppress("DEPRECATION")
        val linkSpeed = wifiManager.connectionInfo?.linkSpeed

        fun fmt(ip: Int?): String? = if (ip != null && ip != 0) Formatter.formatIpAddress(ip) else null

        NetworkSnapshotStore.save(
            context, ssid,
            NetworkSnapshotStore.Snapshot(
                connectedAt = now,
                ip = fmt(dhcp?.ipAddress),
                gateway = fmt(dhcp?.gateway),
                dns1 = fmt(dhcp?.dns1),
                dns2 = fmt(dhcp?.dns2),
                netmask = fmt(dhcp?.netmask),
                frequency = scanEntry?.frequency,
                channelWidthMhz = width,
                linkSpeedMbps = linkSpeed
            )
        )

        if (AppSettings.isConnectionLogEnabled(context)) {
            val timestamp = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(now)
            val widthText = width?.let { "${it}МГц" } ?: "?МГц"
            val channelText = scanEntry?.frequency?.let { NetworkAdapter.frequencyToChannel(it) } ?: "?"
            val speedText = linkSpeed?.let { "${it}Мбит/с" } ?: "?Мбит/с"
            ConnectionLogFile.appendLine(context, "$timestamp - $ssid - $widthText канал $channelText $speedText")
        }
    }
}
