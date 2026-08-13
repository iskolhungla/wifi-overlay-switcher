package com.iskol.wifioverlay

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Подключение через классические WifiManager.addNetwork()/enableNetwork().
 *
 * Работают ПОЛНОСТЬЮ БЕЗ диалогов и мгновенно, но только если приложение
 * установлено как привилегированное системное (через Magisk-модуль,
 * /system/priv-app + privapp-permissions). Для обычной установки эти методы
 * с Android 10+ либо не действуют, либо ведут себя ненадёжно — именно поэтому
 * ограничение касается только непривилегированных сторонних приложений.
 */
object WifiPrivilegedConnector {
    private const val TAG = "WifiOverlayConnect"

    @Suppress("DEPRECATION")
    fun connect(context: Context, ssid: String, password: String?, secured: Boolean): Boolean {
        if (secured && password.isNullOrEmpty()) {
            Log.d(TAG, "privileged connect() aborted: secured network without a password — ssid=$ssid")
            return false
        }

        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val config = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            if (secured && !password.isNullOrEmpty()) {
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            } else {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
        }

        val netId = wifiManager.addNetwork(config)
        Log.d(TAG, "privileged addNetwork() returned netId=$netId ssid=$ssid")
        if (netId == -1) {
            Log.d(TAG, "privileged addNetwork() failed — app is likely not installed as a privileged system app yet")
            return false
        }

        wifiManager.disconnect()
        val enabled = wifiManager.enableNetwork(netId, true)
        val reconnected = wifiManager.reconnect()
        Log.d(TAG, "privileged enableNetwork=$enabled reconnect=$reconnected ssid=$ssid")

        repeat(10) {
            Thread.sleep(1000)
            if (RootWifiControl.currentSsid() == ssid) return true
        }
        return false
    }
}
