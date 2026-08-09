package com.iskol.wifioverlay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
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
 * собственный диалог-подтверждение поверх нашего оверлея.
 */
object WifiConnector {

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    fun connect(context: Context, ssid: String, password: String?, timeoutMs: Int = 20000): Boolean {
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

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                success = true
                if (latch.count > 0) latch.countDown()
            }

            override fun onUnavailable() {
                success = false
                if (latch.count > 0) latch.countDown()
            }
        }
        activeCallback = callback

        cm.requestNetwork(request, callback, timeoutMs)
        latch.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        return success
    }
}
