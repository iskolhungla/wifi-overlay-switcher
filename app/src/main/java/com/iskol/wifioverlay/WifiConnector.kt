package com.iskol.wifioverlay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Подключение через официальный API — WifiNetworkSpecifier (Android 10+).
 * Root не нужен. На этой прошивке `cmd wifi connect-network` в принципе
 * недоступен: read-команды разрешены uid=shell, а вот сами команды
 * подключения/отключения AOSP жёстко требует uid=root — а root, в свою
 * очередь, не может достучаться до WifiService из-за SELinux. Тупик.
 * WifiNetworkSpecifier — штатный путь в обход этого всего.
 *
 * Особенность: при первом подключении к сети система показывает
 * собственный диалог-подтверждение поверх нашего оверлея. Первая попытка
 * иногда не находит сеть, если внутренний кэш сканирования у системы
 * успел устареть — поэтому здесь есть один автоматический повтор,
 * с принудительным свежим сканом перед вторым заходом, чтобы пользователю
 * не приходилось вводить пароль дважды вручную.
 */
object WifiConnector {

    private const val TAG = "WifiOverlayConnect"

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    fun connect(context: Context, ssid: String, password: String?, secured: Boolean, timeoutMs: Int = 15000): Boolean {
        if (secured && password.isNullOrEmpty()) {
            Log.d(TAG, "connect() aborted: secured network without a password, would never match — ssid=$ssid")
            return false
        }

        repeat(2) { attempt ->
            if (attempt > 0) {
                Log.d(TAG, "connect() retrying after fresh scan: ssid=$ssid attempt=${attempt + 1}")
                triggerFreshScan(context)
                Thread.sleep(2000)
            }
            if (attemptConnect(context, ssid, password, timeoutMs)) {
                return true
            }
        }
        return false
    }

    private fun triggerFreshScan(context: Context) {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (_: Exception) {
        }
    }

    private fun attemptConnect(context: Context, ssid: String, password: String?, timeoutMs: Int): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        activeCallback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }

        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (!password.isNullOrEmpty()) {
            specifierBuilder.setWpa2Passphrase(password)
        }
        val specifier = specifierBuilder.build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val latch = CountDownLatch(1)
        var success = false
        var resultReason = "no-callback-fired (timed out waiting)"

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "onAvailable: network=$network ssid=$ssid")
                success = true
                resultReason = "onAvailable"
                if (latch.count > 0) latch.countDown()
            }

            override fun onUnavailable() {
                Log.d(TAG, "onUnavailable: ssid=$ssid (system gave up matching/connecting)")
                success = false
                resultReason = "onUnavailable"
                if (latch.count > 0) latch.countDown()
            }
        }
        activeCallback = callback

        Log.d(TAG, "requestNetwork start: ssid=$ssid hasPassword=${!password.isNullOrEmpty()} timeoutMs=$timeoutMs")
        cm.requestNetwork(request, callback, timeoutMs)
        latch.await(timeoutMs.toLong() + 2000, TimeUnit.MILLISECONDS)
        Log.d(TAG, "attemptConnect() returning: ssid=$ssid success=$success reason=$resultReason")
        return success
    }
}
