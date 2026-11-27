package com.iskol.wifioverlay

/**
 * Сети и ширина канала из кэша драйвера: `iw dev wlan0 scan dump` (netlink напрямую к драйверу,
 * минуя WifiService). `dump` только читает кэш и ничего не запускает; кэш ядра живёт недолго,
 * поэтому перед чтением в режиме Deep вызывается [trigger] (`scan trigger` возвращается сразу).
 *
 * Формат BSS-блока:
 *   BSS <bssid>(on wlan0)
 *     last seen: 1234 ms ago
 *     freq: 2462
 *     signal: -52.00 dBm
 *     SSID: Имя
 *     HT operation: * secondary channel offset: above|below|no secondary
 *     VHT operation: * channel width: 0 (20 or 40 MHz) | 1 (80 MHz) | 2 (160 MHz) | 3 (80+80 MHz)
 */
object IwScanReader {

    data class IwNet(val ssid: String, val frequency: Int, val rssi: Int, val secured: Boolean, val widthMhz: Int)

    private val ssidRegex = Regex("""SSID:\s*(.*)""")
    private val freqRegex = Regex("""freq:\s*(\d+)""")
    private val signalRegex = Regex("""signal:\s*(-?\d+)""")
    private val lastSeenRegex = Regex("""last seen:\s*(\d+)\s*ms""")
    private val vhtWidthRegex = Regex("""channel width:\s*(\d+)\s*\(""")
    private val secondaryOffsetRegex = Regex("""secondary channel offset:\s*(.*)""")
    private val privacyRegex = Regex("""capability:.*\bPrivacy\b""")

    /** Запуск активного сканирования всех каналов; не ждёт завершения (-16 «busy» — нормально). */
    fun trigger() {
        RootShell.execAsRoot("iw dev wlan0 scan trigger", 4000)
    }

    fun readNetworks(): List<IwNet> {
        val (ok, out) = RootShell.execAsRoot("iw dev wlan0 scan dump", 6000)
        if (!ok || out.isBlank()) return emptyList()

        val best = mutableMapOf<String, IwNet>()
        out.split(Regex("""(?m)^BSS """)).drop(1).forEach { block ->
            val ssid = ssidRegex.find(block)?.groupValues?.get(1)?.trim()
            if (ssid.isNullOrBlank() || ssid.startsWith("\\x00")) return@forEach
            val seen = lastSeenRegex.find(block)?.groupValues?.get(1)?.toLongOrNull()
            if (seen != null && seen > 30_000) return@forEach // устаревшая запись
            val freq = freqRegex.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            val rssi = signalRegex.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach

            val secondaryOffset = secondaryOffsetRegex.find(block)?.groupValues?.get(1)?.trim()
            val is40MhzViaHt = secondaryOffset != null && !secondaryOffset.startsWith("no")
            val width = when (vhtWidthRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()) {
                1 -> 80
                2, 3 -> 160
                else -> if (is40MhzViaHt) 40 else 20
            }
            val secured = block.contains("RSN:") || block.contains("WPA:") || privacyRegex.containsMatchIn(block)

            val net = IwNet(ssid, freq, rssi, secured, width)
            val prev = best[ssid]
            if (prev == null || net.rssi > prev.rssi) best[ssid] = net
        }
        return best.values.toList()
    }

    /** SSID -> ширина канала в МГц. */
    fun readChannelWidths(): Map<String, Int> = readNetworks().associate { it.ssid to it.widthMhz }
}
