package com.iskol.wifioverlay

object RootWifiControl {

    private const val SHELL_UID = 2000

    fun currentSsid(): String? {
        val out = RootShell.execAsUid(SHELL_UID, "cmd wifi status")
        val regex = Regex("connected to \"([^\"]+)\"")
        return regex.find(out)?.groupValues?.get(1)
    }

    fun listSavedNetworkNames(): Set<String> {
        val out = RootShell.execAsUid(SHELL_UID, "cmd wifi list-networks")
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
        val out = RootShell.execAsUid(SHELL_UID, command)
        return out.isNotBlank() &&
            !out.contains("error", ignoreCase = true) &&
            !out.contains("Failure", ignoreCase = true) &&
            !out.contains("Exception", ignoreCase = true)
    }

    fun disconnect(): Boolean {
        val out = RootShell.execAsUid(SHELL_UID, "cmd wifi disconnect")
        return !out.contains("Failure", ignoreCase = true)
    }

    /** Диагностика: выполнить произвольную команду от shell-uid. */
    fun rawDiagnostic(command: String): String {
        return RootShell.execAsUid(SHELL_UID, command)
    }
}
