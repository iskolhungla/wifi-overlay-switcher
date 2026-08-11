package com.iskol.wifioverlay

import android.util.Log

/**
 * Читает пароли уже сохранённых системой сетей напрямую из файла
 * WifiConfigStore.xml через root (обычное чтение файла, не binder-вызов
 * к WifiService — та блокировка сюда не относится).
 *
 * Android исторически хранит PreSharedKey в этом файле обычным текстом,
 * полагаясь на шифрование диска целиком, а не на отдельное шифрование поля.
 * На этом устройстве кавычки в значении закодированы как &quot;, а не как
 * обычный символ " — регулярки учитывают оба варианта.
 */
object WifiConfigStoreReader {

    private const val TAG = "WifiOverlayConnect"
    private const val CONFIG_PATH = "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml"

    private const val QUOTE = """(?:"|&quot;)"""
    private val ssidRegex = Regex("""<string name="SSID">\s*$QUOTE(.*?)$QUOTE\s*</string>""")
    private val pskRegex = Regex("""<string name="PreSharedKey">\s*$QUOTE(.*?)$QUOTE\s*</string>""")

    /** SSID -> пароль, только для сетей, где пароль реально удалось извлечь. */
    fun readSavedPasswords(): Map<String, String> {
        val (ok, xml) = RootShell.execAsRoot("cat \"$CONFIG_PATH\"")
        Log.d(TAG, "config store read: ok=$ok length=${xml.length}")
        if (!ok || xml.isBlank()) return emptyMap()

        val blocks = xml.split("<Network>").drop(1)
        Log.d(TAG, "config store: found ${blocks.size} <Network> blocks")

        val result = mutableMapOf<String, String>()
        blocks.forEach { chunk ->
            val block = chunk.substringBefore("</Network>")
            val ssid = ssidRegex.find(block)?.groupValues?.get(1)?.let(::unescapeXml) ?: return@forEach
            val psk = pskRegex.find(block)?.groupValues?.get(1)?.let(::unescapeXml) ?: return@forEach
            if (ssid.isNotBlank() && psk.isNotBlank()) {
                result[ssid] = psk
            }
        }
        // Пароли в лог не пишем принципиально — только сами SSID, для диагностики.
        Log.d(TAG, "config store: parsed ${result.size} passwords for ssids=${result.keys}")
        return result
    }

    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    // Маскирует значение любого <string name="...Key...|...Password...|...Psk...|...Pin...">
    // — учитывает оба варианта кавычек (обычную и &quot;).
    private val sensitiveValueRegex = Regex(
        """(<string name="[^"]*(?:Key|Password|Psk|Pin)[^"]*">)$QUOTE(.*?)$QUOTE(</string>)""",
        RegexOption.IGNORE_CASE
    )

    /** Полное содержимое файла с замаскированными паролями — безопасно для передачи для диагностики. */
    fun readRawRedacted(): String {
        val (ok, xml) = RootShell.execAsRoot("cat \"$CONFIG_PATH\"")
        if (!ok) return "READ FAILED: $xml"
        return sensitiveValueRegex.replace(xml) { m ->
            "${m.groupValues[1]}\"***REDACTED***\"${m.groupValues[3]}"
        }
    }
}
