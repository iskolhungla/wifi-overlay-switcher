package com.iskol.wifioverlay

import android.content.Context
import android.util.Log

object RootWifiControl {

    private const val TAG = "RootWifiControl"

    fun currentSsid(context: Context): String? {
        val out = safeExec(context, "cmd wifi status")
        val regex = Regex("connected to \"([^\"]+)\"")
        return regex.find(out)?.groupValues?.get(1)
    }

    fun listSavedNetworkNames(context: Context): Set<String> {
        val out = safeExec(context, "cmd wifi list-networks")
        val names = mutableSetOf<String>()
        out.lineSequence().drop(1).forEach { line ->
            val parts = line.trim().split(Regex("\\s{2,}"))
            if (parts.size >= 2 && parts[1].isNotBlank()) {
                names.add(parts[1])
            }
        }
        return names
    }

    fun connect(context: Context, ssid: String, password: String?): Boolean {
        val escapedSsid = ssid.replace("\"", "\\\"")
        val command = if (password.isNullOrEmpty()) {
            "cmd wifi connect-network \"$escapedSsid\" open"
        } else {
            val escapedPass = password.replace("\"", "\\\"")
            "cmd wifi connect-network \"$escapedSsid\" wpa2 \"$escapedPass\""
        }
        val out = safeExec(context, command)
        return out.isNotBlank() &&
            !out.contains("error", ignoreCase = true) &&
            !out.contains("Failure", ignoreCase = true) &&
            !out.contains("Exception", ignoreCase = true)
    }

    fun disconnect(context: Context): Boolean {
        val out = safeExec(context, "cmd wifi disconnect")
        return !out.contains("Failure", ignoreCase = true)
    }

    private fun safeExec(context: Context, command: String): String {
        return try {
            AdbShellBridge.execShell(context, command)
        } catch (e: Exception) {
            Log.e(TAG, "adb shell exec failed for [$command]: ${e.message}")
            ""
        }
    }

    /** Диагностика: выполнить произвольную команду через тот же ADB-мост. */
    fun rawDiagnostic(context: Context, command: String): String {
        return try {
            AdbShellBridge.execShell(context, command)
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }
}
