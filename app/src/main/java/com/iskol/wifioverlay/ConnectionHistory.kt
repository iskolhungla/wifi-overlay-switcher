package com.iskol.wifioverlay

import android.content.Context
import org.json.JSONObject

/**
 * Запоминает моменты подключения/отключения по каждому SSID — но только то,
 * что реально видели, пока был открыт оверлей (мы не фоновый сервис,
 * непрерывной истории за всё время у нас нет, только лучшее из доступного).
 */
object ConnectionHistory {
    private const val PREFS = "wifi_overlay_history"

    data class Entry(
        val connectedAt: Long? = null,
        val disconnectedAt: Long? = null,
        val frequency: Int? = null,
        val secured: Boolean? = null
    )

    fun get(context: Context, ssid: String): Entry? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ssid, null)
            ?: return null
        return try {
            val json = JSONObject(raw)
            Entry(
                connectedAt = if (json.has("connectedAt")) json.getLong("connectedAt") else null,
                disconnectedAt = if (json.has("disconnectedAt")) json.getLong("disconnectedAt") else null,
                frequency = if (json.has("frequency")) json.getInt("frequency") else null,
                secured = if (json.has("secured")) json.getBoolean("secured") else null
            )
        } catch (e: Exception) {
            null
        }
    }

    fun recordConnected(context: Context, ssid: String, atMillis: Long, frequency: Int?, secured: Boolean?) {
        val prev = get(context, ssid)
        val json = JSONObject().apply {
            put("connectedAt", atMillis)
            if (prev?.disconnectedAt != null) put("disconnectedAt", prev.disconnectedAt)
            if (frequency != null) put("frequency", frequency) else if (prev?.frequency != null) put("frequency", prev.frequency)
            if (secured != null) put("secured", secured) else if (prev?.secured != null) put("secured", prev.secured)
        }
        save(context, ssid, json)
    }

    fun recordDisconnected(context: Context, ssid: String, atMillis: Long) {
        val prev = get(context, ssid)
        val json = JSONObject().apply {
            if (prev?.connectedAt != null) put("connectedAt", prev.connectedAt)
            put("disconnectedAt", atMillis)
            if (prev?.frequency != null) put("frequency", prev.frequency)
            if (prev?.secured != null) put("secured", prev.secured)
        }
        save(context, ssid, json)
    }

    private fun save(context: Context, ssid: String, json: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ssid, json.toString())
            .apply()
    }
}
