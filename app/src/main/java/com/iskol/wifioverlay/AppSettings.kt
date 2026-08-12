package com.iskol.wifioverlay

import android.content.Context

object AppSettings {
    private const val PREFS = "wifi_overlay_settings"
    private const val KEY_CONNECT_MODE = "connect_mode"

    enum class ConnectMode { PRIVILEGED, SPECIFIER, SUGGESTION }

    fun getConnectMode(context: Context): ConnectMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CONNECT_MODE, null)
        return when (raw) {
            ConnectMode.SPECIFIER.name -> ConnectMode.SPECIFIER
            ConnectMode.SUGGESTION.name -> ConnectMode.SUGGESTION
            else -> ConnectMode.PRIVILEGED
        }
    }

    fun setConnectMode(context: Context, mode: ConnectMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONNECT_MODE, mode.name)
            .apply()
    }
}
