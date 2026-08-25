package com.iskol.wifioverlay

import android.net.wifi.WifiManager

/**
 * Экспериментальный фикс HT40/80 для MediaTek gen4m-драйвера (wlan_drv_gen4m).
 * Специфично для устройств с /proc/net/wlan/cfg — обязательно проверяем
 * совместимость перед установкой, ничего не трогаем вслепую.
 */
object HtFixManager {
    private const val SCRIPT_PATH = "/data/adb/service.d/wifi_ht40.sh"
    private const val CFG_PATH = "/proc/net/wlan/cfg"
    private const val MARKER = "# installed-by-wifioverlay-ht40fix"

    data class DiagnosticResult(
        val driverPresent: Boolean,
        val currentSta2gBw: String?,
        val currentSta5gBw: String?,
        val scriptExists: Boolean,
        val scriptIsOurs: Boolean,
        val currentLinkSpeed: String?
    )

    fun diagnose(): DiagnosticResult {
        val (cfgOk, cfgOut) = RootShell.execAsRoot("cat $CFG_PATH 2>/dev/null")
        val driverPresent = cfgOk && cfgOut.isNotBlank()
        val sta2g = Regex("""Sta2gBw\|(\S+)""").find(cfgOut)?.groupValues?.get(1)
        val sta5g = Regex("""Sta5gBw\|(\S+)""").find(cfgOut)?.groupValues?.get(1)

        val (scriptOk, scriptOut) = RootShell.execAsRoot("cat $SCRIPT_PATH 2>/dev/null")
        val scriptExists = scriptOk && scriptOut.isNotBlank()
        val scriptIsOurs = scriptOut.contains(MARKER)

        val (_, speedOut) = RootShell.execAsRoot("dumpsys wifi | grep mWifiInfo | head -1")
        val linkSpeed = Regex("""Link speed:\s*(\S+)""").find(speedOut)?.groupValues?.get(1)

        return DiagnosticResult(driverPresent, sta2g, sta5g, scriptExists, scriptIsOurs, linkSpeed)
    }

    fun installScript(): Boolean {
        val script = """
            #!/system/bin/sh
            $MARKER
            WAIT=0
            until [ "${'$'}(getprop sys.boot_completed)" = "1" ] || [ ${'$'}WAIT -ge 48 ]; do
                sleep 5
                WAIT=${'$'}((WAIT + 1))
            done
            sleep 10
            echo "Sta2gBw 1" > $CFG_PATH
            echo "Sta5gBw 2" > $CFG_PATH
        """.trimIndent()

        val tmpFile = "/data/local/tmp/wifi_ht40_tmp.sh"
        val (ok1, _) = RootShell.execAsRoot("cat > $tmpFile << 'SCRIPTEOF'\n$script\nSCRIPTEOF")
        val (ok2, _) = RootShell.execAsRoot(
            "mkdir -p /data/adb/service.d && cp $tmpFile $SCRIPT_PATH && chmod 755 $SCRIPT_PATH && rm -f $tmpFile"
        )
        return ok1 && ok2
    }

    /** Применяет параметры прямо сейчас (без перезагрузки) и переключает WiFi, чтобы они подхватились. */
    @Suppress("DEPRECATION")
    fun applyNow(wifiManager: WifiManager): Boolean {
        val (ok1, _) = RootShell.execAsRoot("echo 'Sta2gBw 1' > $CFG_PATH")
        val (ok2, _) = RootShell.execAsRoot("echo 'Sta5gBw 2' > $CFG_PATH")
        wifiManager.isWifiEnabled = false
        Thread.sleep(3000)
        wifiManager.isWifiEnabled = true
        return ok1 && ok2
    }
}
