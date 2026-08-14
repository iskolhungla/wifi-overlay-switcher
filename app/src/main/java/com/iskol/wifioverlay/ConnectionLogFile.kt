package com.iskol.wifioverlay

import android.content.Context
import java.io.File

/**
 * Журнал подключений в формате: YYYY.MM.DD HH:mm:ss - <SSID> - <ширина> <канал> <скорость>
 * Источник истины — приватный файл приложения (туда можно дописывать без прав),
 * который после каждой записи полностью копируется в удобную папку через root
 * (прямая запись туда невозможна из-за scoped storage).
 */
object ConnectionLogFile {
    private const val FILE_NAME = "connection_log.txt"
    private const val PUBLIC_DIR =
        "/storage/A6D2-2FD0/ATV_Sync_ALLnew/Soft/Claude/Claude-HU-wrk_ISKOLA-HU/WiFi switch - overlay"

    fun appendLine(context: Context, line: String) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.appendText(line + "\n")
            syncToPublicDir(file)
        } catch (_: Exception) {
        }
    }

    private fun syncToPublicDir(privateFile: File) {
        RootShell.execAsRoot("mkdir -p \"$PUBLIC_DIR\"")
        RootShell.execAsRoot("cp \"${privateFile.absolutePath}\" \"$PUBLIC_DIR/$FILE_NAME\"")
        RootShell.execAsRoot("chmod 666 \"$PUBLIC_DIR/$FILE_NAME\"")
    }
}
