package com.iskol.wifioverlay

object RootWifiControl {

    private const val SHELL_UID = 2000

    data class ScanEntry(val ssid: String, val rssi: Int, val secured: Boolean)

    /**
     * Список видимых сетей через `cmd wifi list-scan-results` (обходит урезанный
     * на этой прошивке WifiManager.getScanResults()).
     *
     * Формат строки:
     *   BSSID              Frequency  RSSI   Age(sec)   SSID       Flags
     *   a4:7c:c9:cb:43:24    2462     -42   758,622    Gaspar     [WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]
     */
    fun scanNetworks(): List<ScanEntry> {
        val out = RootShell.execAsUid(SHELL_UID, "cmd wifi list-scan-results")
        val lineRegex = Regex("""^\s*(\S+)\s+(\d+)\s+(-?\d+)\s+([\d,]+)\s+(.*)$""")
        val entries = mutableListOf<ScanEntry>()

        out.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("BSSID")) return@forEach

            val match = lineRegex.find(line) ?: return@forEach
            val rssi = match.groupValues[3].toIntOrNull() ?: return@forEach
            val rest = match.groupValues[5]
            val flagsStart = rest.indexOf('[')
            val ssid = if (flagsStart >= 0) rest.substring(0, flagsStart).trim() else rest.trim()
            val flags = if (flagsStart >= 0) rest.substring(flagsStart) else ""

            if (ssid.isBlank()) return@forEach

            val secured = flags.contains("WPA") || flags.contains("RSN") ||
                flags.contains("WEP") || flags.contains("EAP") || flags.contains("SAE")

            entries.add(ScanEntry(ssid, rssi, secured))
        }
        return entries
    }

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

    fun connect(ssid: String, password: String?, secured: Boolean): Boolean {
        val escapedSsid = ssid.replace("\"", "\\\"")
        val securityType = if (secured) "wpa2" else "open"
        val command = if (password.isNullOrEmpty()) {
            // Пустой пароль для защищённой сети = переиспользовать уже сохранённый
            // (connect-network подбирает существующий конфиг по SSID+типу защиты).
            "cmd wifi connect-network \"$escapedSsid\" $securityType"
        } else {
            val escapedPass = password.replace("\"", "\\\"")
            "cmd wifi connect-network \"$escapedSsid\" $securityType \"$escapedPass\""
        }
        RootShell.execAsUid(SHELL_UID, command)

        // connect-network обычно ничего не печатает при успехе, поэтому вместо
        // разбора текста реально проверяем, подключились ли мы к нужной сети.
        repeat(8) {
            Thread.sleep(800)
            if (currentSsid() == ssid) return true
        }
        return false
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
