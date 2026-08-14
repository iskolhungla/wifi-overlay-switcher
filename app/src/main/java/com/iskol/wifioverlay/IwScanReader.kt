package com.iskol.wifioverlay

/**
 * Ширина Wi-Fi канала через `iw dev wlan0 scan` — этот путь идёт напрямую к
 * драйверу через netlink, минуя WifiService/binder, где у нас были проблемы
 * с урезанным `WifiManager.getScanResults()` даже под привилегиями.
 *
 * Формат вывода на BSS-блок:
 *   BSS <bssid>(on wlan0)
 *     freq: 2462
 *     SSID: Имя
 *     HT operation:
 *         * secondary channel offset: above|below|no secondary
 *     VHT operation:
 *         * channel width: 0 (20 or 40 MHz) | 1 (80 MHz) | 2 (160 MHz) | 3 (80+80 MHz)
 */
object IwScanReader {

    private val ssidRegex = Regex("""SSID:\s*(.*)""")
    private val vhtWidthRegex = Regex("""channel width:\s*(\d+)\s*\(""")
    private val secondaryOffsetRegex = Regex("""secondary channel offset:\s*(.*)""")

    /** SSID -> ширина канала в МГц. */
    fun readChannelWidths(): Map<String, Int> {
        val (ok, out) = RootShell.execAsRoot("iw dev wlan0 scan")
        if (!ok || out.isBlank()) return emptyMap()

        val blocks = out.split(Regex("""(?m)^BSS """)).drop(1)
        val result = mutableMapOf<String, Int>()

        blocks.forEach { block ->
            val ssid = ssidRegex.find(block)?.groupValues?.get(1)?.trim()
            if (ssid.isNullOrBlank() || result.containsKey(ssid)) return@forEach

            val secondaryOffset = secondaryOffsetRegex.find(block)?.groupValues?.get(1)?.trim()
            val is40MhzViaHt = secondaryOffset != null && !secondaryOffset.startsWith("no")

            val vhtCode = vhtWidthRegex.find(block)?.groupValues?.get(1)?.toIntOrNull()
            val width = when (vhtCode) {
                0 -> if (is40MhzViaHt) 40 else 20
                1 -> 80
                2 -> 160
                3 -> 160
                else -> if (is40MhzViaHt) 40 else 20
            }
            result[ssid] = width
        }
        return result
    }
}
