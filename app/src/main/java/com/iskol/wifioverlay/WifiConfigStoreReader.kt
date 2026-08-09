package com.iskol.wifioverlay

/**
 * Читает пароли уже сохранённых системой сетей напрямую из файла
 * WifiConfigStore.xml через root (обычное чтение файла, не binder-вызов
 * к WifiService — та блокировка сюда не относится).
 *
 * Android исторически хранит PreSharedKey в этом файле обычным текстом,
 * полагаясь на шифрование диска целиком, а не на отдельное шифрование поля.
 */
object WifiConfigStoreReader {

    private const val CONFIG_PATH = "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml"

    private val ssidRegex = Regex("""<string name="SSID">"(.*?)"</string>""")
    private val pskRegex = Regex("""<string name="PreSharedKey">"(.*?)"</string>""")

    /** SSID -> пароль, только для сетей, где пароль реально удалось извлечь. */
    fun readSavedPasswords(): Map<String, String> {
        val (ok, xml) = RootShell.execAsRoot("cat \"$CONFIG_PATH\"")
        if (!ok || xml.isBlank()) return emptyMap()

        val result = mutableMapOf<String, String>()
        xml.split("<Network>").drop(1).forEach { chunk ->
            val block = chunk.substringBefore("</Network>")
            val ssid = ssidRegex.find(block)?.groupValues?.get(1)?.let(::unescapeXml) ?: return@forEach
            val psk = pskRegex.find(block)?.groupValues?.get(1)?.let(::unescapeXml) ?: return@forEach
            if (ssid.isNotBlank() && psk.isNotBlank()) {
                result[ssid] = psk
            }
        }
        return result
    }

    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
