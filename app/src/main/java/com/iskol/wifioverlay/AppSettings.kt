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

    private const val KEY_HISTORY_MAX = "history_max_entries"
    const val DEFAULT_HISTORY_MAX = 512
    const val HISTORY_MAX_LIMIT = 5000

    fun getHistoryMaxEntries(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_HISTORY_MAX, DEFAULT_HISTORY_MAX).coerceIn(20, HISTORY_MAX_LIMIT)
    }

    fun setHistoryMaxEntries(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_HISTORY_MAX, value.coerceIn(20, HISTORY_MAX_LIMIT)).apply()
    }

    enum class ScanMode { FAST, NORMAL, DEEP }
    private const val KEY_SCAN_MODE = "scan_mode"
    private const val KEY_SCAN_WAIT = "scan_wait_seconds"

    fun getScanMode(context: Context): ScanMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCAN_MODE, null)
        return ScanMode.values().firstOrNull { it.name == raw } ?: ScanMode.NORMAL
    }

    fun setScanMode(context: Context, mode: ScanMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SCAN_MODE, mode.name).apply()
    }

    /** Сколько секунд ждать результатов после запуска сканирования (Deep добавляет ещё 2 с). */
    fun getScanWaitSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SCAN_WAIT, 4).coerceIn(1, 20)

    fun setScanWaitSeconds(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_SCAN_WAIT, value.coerceIn(1, 20)).apply()
    }
}
