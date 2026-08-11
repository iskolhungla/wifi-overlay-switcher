package com.iskol.wifioverlay

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.util.Log

/**
 * Подключение через WifiNetworkSuggestion — в отличие от WifiNetworkSpecifier,
 * система НЕ показывает диалог на каждое подключение (только один раз за всё
 * время работы приложения, системное согласие "разрешить приложению предлагать
 * сети"). Минус: это не мгновенная команда "подключи сейчас" — система сама
 * решает, когда переключиться, обычно за несколько секунд.
 */
object WifiSuggestionConnector {

    private const val TAG = "WifiOverlayConnect"
    private val suggestedSsids = mutableSetOf<String>()

    fun connect(context: Context, ssid: String, password: String?, secured: Boolean): Boolean {
        if (secured && password.isNullOrEmpty()) {
            Log.d(TAG, "suggestion connect() aborted: secured network without a password — ssid=$ssid")
            return false
        }

        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
        if (secured && !password.isNullOrEmpty()) {
            builder.setWpa2Passphrase(password)
        }
        val suggestion = builder.build()

        val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
        Log.d(TAG, "addNetworkSuggestions status=$status ssid=$ssid")

        val accepted = status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
        if (!accepted) return false
        suggestedSsids.add(ssid)

        // Система переключается сама и не мгновенно — ждём и проверяем факт
        // через тот же канал (shell), которым уже пользуемся для статуса.
        repeat(10) {
            Thread.sleep(1000)
            if (RootWifiControl.currentSsid() == ssid) return true
        }
        return false
    }
}
