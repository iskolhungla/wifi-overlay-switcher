package com.iskol.wifioverlay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
 * собственный диалог-подтверждение поверх нашего оверлея.
 */
object WifiConnector {

    private const val TAG = "WifiOverlayConnect"

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    fun connect(context: Context, ssid: String, password: String?, secured: Boolean, timeoutMs: Int = 20000): Boolean {
        if (secured && password.isNullOrEmpty()) {
            Log.d(TAG, "connect() aborted: secured network without a password, would never match — ssid=$ssid")
            return false
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        activeCallback?.let {
            try {
                cm.unregisterNetworkCallback(it)
                Log.d(TAG, "unregistered previous callback before new request")
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

            override fun onLosing(network: Network, maxMsToLive: Int) {
                Log.d(TAG, "onLosing: network=$network maxMsToLive=$maxMsToLive ssid=$ssid")
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "onLost: network=$network ssid=$ssid")
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                Log.d(TAG, "onCapabilitiesChanged: network=$network caps=$caps ssid=$ssid")
            }
        }
        activeCallback = callback

        Log.d(TAG, "requestNetwork start: ssid=$ssid hasPassword=${!password.isNullOrEmpty()} timeoutMs=$timeoutMs")
        cm.requestNetwork(request, callback, timeoutMs)
        val latchOk = latch.await(timeoutMs.toLong() + 2000, TimeUnit.MILLISECONDS)
        Log.d(TAG, "connect() returning: ssid=$ssid success=$success reason=$resultReason latchTimedOut=${!latchOk}")
        return success
    }
}
