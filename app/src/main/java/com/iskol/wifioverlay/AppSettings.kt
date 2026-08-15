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

    private const val KEY_LOG_ENABLED = "connection_log_enabled"

    fun isConnectionLogEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOG_ENABLED, false)
    }

    fun setConnectionLogEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOG_ENABLED, enabled)
            .apply()
    }

    private const val KEY_DEBUG_DUMP_ENABLED = "debug_dump_enabled"

    fun isDebugDumpEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEBUG_DUMP_ENABLED, true)
    }

    fun setDebugDumpEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEBUG_DUMP_ENABLED, enabled)
            .apply()
    }

    private const val KEY_LOG_DIR = "log_directory"
    const val DEFAULT_LOG_DIR = "/storage/emulated/0/Documents"

    fun getLogDirectory(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOG_DIR, DEFAULT_LOG_DIR) ?: DEFAULT_LOG_DIR
    }

    fun setLogDirectory(context: Context, path: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOG_DIR, path)
            .apply()
    }
}
