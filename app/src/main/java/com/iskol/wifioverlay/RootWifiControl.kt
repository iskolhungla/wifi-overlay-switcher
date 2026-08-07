package com.iskol.wifioverlay

import android.util.Log

/**
 * Управление WiFi через root-shell (su -c "cmd wifi ...").
 *
 * Команды "cmd wifi" — часть AOSP WifiShellCommand (доступны с Android 11).
 * На некоторых прошивках (HyperOS/MIUI) набор команд может отличаться —
 * см. README, раздел "Диагностика", если connect() всегда возвращает false.
 */
object RootWifiControl {

    private const val TAG = "RootWifiControl"

    private fun runAsRoot(command: String): Pair<Boolean, String> {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            Log.d(TAG, "cmd=[$command] exit=$exit out=[$output]")
            Pair(exit == 0, output)
        } catch (e: Exception) {
            Log.e(TAG, "root exec failed for [$command]: ${e.message}")
            Pair(false, e.message ?: "")
        }
    }

    fun currentSsid(): String? {
        val (_, out) = runAsRoot("cmd wifi status")
        val regex = Regex("connected to \"([^\"]+)\"")
        return regex.find(out)?.groupValues?.get(1)
    }

    fun listSavedNetworkNames(): Set<String> {
        val (_, out) = runAsRoot("cmd wifi list-networks")
        val names = mutableSetOf<String>()
        out.lineSequence().drop(1).forEach { line ->
            val parts = line.trim().split(Regex("\\s{2,}"))
            if (parts.size >= 2 && parts[1].isNotBlank()) {
                names.add(parts[1])
            }
        }
        return names
    }

    fun connect(ssid: String, password: String?): Boolean {
        val escapedSsid = ssid.replace("\"", "\\\"")
        val command = if (password.isNullOrEmpty()) {
            "cmd wifi connect-network \"$escapedSsid\" open"
        } else {
            val escapedPass = password.replace("\"", "\\\"")
            "cmd wifi connect-network \"$escapedSsid\" wpa2 \"$escapedPass\""
        }
        val (ok, _) = runAsRoot(command)
        return ok
    }

    fun disconnect(): Boolean {
        val (ok, _) = runAsRoot("cmd wifi disconnect")
        return ok
    }

    /** Диагностика: выполнить произвольную shell-команду от root и вернуть вывод. */
    fun rawDiagnostic(command: String): String {
        val (ok, out) = runAsRoot(command)
        return "ok=$ok\n$out"
    }
}
