package com.iskol.wifioverlay

import android.content.Context
import java.io.File

/**
 * Журнал подключений в формате: YYYY.MM.DD HH:mm:ss - <SSID> - <ширина> <канал> <скорость>
 * Источник истины — приватный файл приложения (туда можно дописывать без прав),
 * который после каждой записи полностью копируется в папку из настроек через
 * root (прямая запись туда невозможна из-за scoped storage).
 */
object ConnectionLogFile {
    private const val FILE_NAME = "connection_log.txt"

    fun appendLine(context: Context, line: String) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.appendText(line + "\n")
            syncToDir(context, file)
        } catch (_: Exception) {
        }
    }

    private fun syncToDir(context: Context, privateFile: File) {
        val dir = AppSettings.getLogDirectory(context)
        RootShell.execAsRoot("mkdir -p \"$dir\"")
        RootShell.execAsRoot("cp \"${privateFile.absolutePath}\" \"$dir/$FILE_NAME\"")
        RootShell.execAsRoot("chmod 666 \"$dir/$FILE_NAME\"")
    }
}
