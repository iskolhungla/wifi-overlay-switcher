package com.iskol.wifioverlay

import android.content.Context
import org.json.JSONObject

/**
 * Постоянный (переживает перезагрузку) снимок последнего известного состояния
 * подключения по каждому SSID — IP, шлюз, DNS, канал, ширина, скорость.
 * Обновляется в фоне через WifiStateReceiver при ЛЮБОМ подключении, не только
 * инициированном нашим приложением (система не хранит эти данные сама).
 */
object NetworkSnapshotStore {
    private const val PREFS = "wifi_overlay_snapshots"

    data class Snapshot(
        val connectedAt: Long,
        val ip: String?,
        val gateway: String?,
        val dns1: String?,
        val dns2: String?,
        val netmask: String?,
        val frequency: Int?,
        val channelWidthMhz: Int?,
        val linkSpeedMbps: Int?
    )

    fun get(context: Context, ssid: String): Snapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ssid, null)
            ?: return null
        return try {
            val j = JSONObject(raw)
            Snapshot(
                connectedAt = j.getLong("connectedAt"),
                ip = j.optString("ip", null),
                gateway = j.optString("gateway", null),
                dns1 = j.optString("dns1", null),
                dns2 = j.optString("dns2", null),
                netmask = j.optString("netmask", null),
                frequency = if (j.has("frequency")) j.getInt("frequency") else null,
                channelWidthMhz = if (j.has("channelWidthMhz")) j.getInt("channelWidthMhz") else null,
                linkSpeedMbps = if (j.has("linkSpeedMbps")) j.getInt("linkSpeedMbps") else null
            )
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, ssid: String, snapshot: Snapshot) {
        val j = JSONObject().apply {
            put("connectedAt", snapshot.connectedAt)
            snapshot.ip?.let { put("ip", it) }
            snapshot.gateway?.let { put("gateway", it) }
            snapshot.dns1?.let { put("dns1", it) }
            snapshot.dns2?.let { put("dns2", it) }
            snapshot.netmask?.let { put("netmask", it) }
            snapshot.frequency?.let { put("frequency", it) }
            snapshot.channelWidthMhz?.let { put("channelWidthMhz", it) }
            snapshot.linkSpeedMbps?.let { put("linkSpeedMbps", it) }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ssid, j.toString())
            .apply()
    }
}
