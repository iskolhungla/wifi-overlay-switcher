package com.iskol.wifioverlay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Полная история подключений/отключений по всем сетям — не только через наше
 * приложение, ловится фоновым WifiStateReceiver для любого источника
 * переключения. Ограничена 512 записями (старые вытесняются новыми).
 */
object ConnectionEventLog {
    private const val PREFS = "wifi_overlay_event_log"
    private const val KEY_EVENTS = "events"
    private const val KEY_LAST_SSID = "last_connected_ssid"
    const val MAX_ENTRIES = 512

    const val TYPE_CONNECTED = "connected"
    const val TYPE_DISCONNECTED = "disconnected"

    data class Entry(
        val timestamp: Long,
        val ssid: String,
        val eventType: String,
        val frequency: Int?,
        val channelWidthMhz: Int?
    )

    fun addEvent(context: Context, entry: Entry) {
        val events = readAll(context).toMutableList()
        events.add(entry)
        while (events.size > MAX_ENTRIES) {
            events.removeAt(0)
        }
        saveAll(context, events)
    }

    fun readAll(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    timestamp = o.optLong("t"),
                    ssid = o.optString("s"),
                    eventType = o.optString("e"),
                    frequency = if (o.has("f")) o.getInt("f") else null,
                    channelWidthMhz = if (o.has("w")) o.getInt("w") else null
                )
            }
        } catch (ex: Exception) {
            emptyList()
        }
    }

    fun readForSsid(context: Context, ssid: String): List<Entry> =
        readAll(context).filter { it.ssid == ssid }.sortedByDescending { it.timestamp }

    private fun saveAll(context: Context, events: List<Entry>) {
        val arr = JSONArray()
        events.forEach { e ->
            val o = JSONObject()
            o.put("t", e.timestamp)
            o.put("s", e.ssid)
            o.put("e", e.eventType)
            e.frequency?.let { o.put("f", it) }
            e.channelWidthMhz?.let { o.put("w", it) }
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS, arr.toString())
            .apply()
    }

    fun setLastConnectedSsid(context: Context, ssid: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SSID, ssid)
            .apply()
    }

    fun getLastConnectedSsid(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SSID, null)
    }
}
