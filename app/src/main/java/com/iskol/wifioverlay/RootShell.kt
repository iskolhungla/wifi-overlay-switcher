package com.iskol.wifioverlay

import android.util.Log

/**
 * Голый su-exec для операций, которые НЕ трогают WifiService напрямую:
 * настройка adbd по TCP и запись файла доверенных ключей.
 * Сами WiFi-команды (`cmd wifi ...`) на этой прошивке через root не проходят —
 * см. AdbShellBridge.
 */
object RootShell {
    private const val TAG = "RootShell"

    fun exec(command: String): Pair<Boolean, String> {
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
}
