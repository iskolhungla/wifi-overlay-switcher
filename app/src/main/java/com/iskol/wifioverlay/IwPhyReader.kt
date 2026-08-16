package com.iskol.wifioverlay

/**
 * Статические возможности WiFi-адаптера (не меняются от сети к сети) —
 * максимальная поддерживаемая ширина канала на каждом диапазоне, из `iw phy`.
 *
 * На этом устройстве/прошивке поле "Supported Channel Width" в VHT Capabilities
 * само по себе не расшифровывается (`iw` показывает "(reserved)" — баг
 * декодирования именно на этой связке чип+HyperOS), поэтому ширину для 5 ГГц
 * вычисляем косвенно по паспортной скорости "VHT RX highest supported".
 */
object IwPhyReader {

    data class AdapterCapability(
        val band24MaxWidthMhz: Int?,
        val band5MaxWidthMhz: Int?,
        val vhtHighestMbps: Int?
    )

    private val vhtMbpsRegex = Regex("""VHT RX highest supported:\s*(\d+)\s*Mbps""")

    fun readCapability(): AdapterCapability {
        val (ok, out) = RootShell.execAsRoot("iw phy")
        if (!ok || out.isBlank()) {
            return AdapterCapability(null, null, null)
        }

        val supportsHt40 = out.contains("HT20/HT40")
        val band24Width = if (supportsHt40) 40 else 20

        val vhtMbps = vhtMbpsRegex.find(out)?.groupValues?.get(1)?.toIntOrNull()
        val band5Width = when {
            vhtMbps == null -> null
            vhtMbps >= 1600 -> 160
            vhtMbps >= 800 -> 80
            vhtMbps >= 350 -> 40
            else -> 20
        }

        return AdapterCapability(band24Width, band5Width, vhtMbps)
    }
}
