package com.iskol.wifioverlay

import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * На этой прошивке (HyperOS) `su <uid> -c "команда"` не выполняет полноценный
 * переход в нужный SELinux-домен — падает с той же ошибкой, что и под root.
 * А вот интерактивная сессия `su <uid>` (без -c), в которую команда подаётся
 * через stdin (как будто её печатают в терминале), проходит полный переход
 * через демон Magisk и работает. Поэтому все команды идут именно так.
 */
object RootShell {
    private const val TAG = "RootShell"

    /** Прямой root (домен magisk) — для операций, не завязанных на системные binder-сервисы. */
    fun execAsRoot(command: String): Pair<Boolean, String> {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            Pair(exit == 0, output)
        } catch (e: Exception) {
            Log.e(TAG, "root exec failed for [$command]: ${e.message}")
            Pair(false, e.message ?: "")
        }
    }

    /** Псевдо-интерактивная su-сессия от имени указанного uid (для uid=2000 — "shell"). */
    fun execAsUid(uid: Int, command: String, timeoutMs: Long = 8000): String {
        return try {
            val process = ProcessBuilder("su", uid.toString())
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val readerThread = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine {
                        output.append(it).append('\n')
                    }
                } catch (_: Exception) {
                }
            }
            readerThread.start()

            process.outputStream.bufferedWriter().use { writer ->
                writer.write(command)
                writer.newLine()
                writer.write("exit")
                writer.newLine()
                writer.flush()
            }

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            readerThread.join(1000)
            if (!finished) {
                process.destroyForcibly()
            }
            Log.d(TAG, "uid=$uid cmd=[$command] out=[$output]")
            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "uid exec failed for [$command]: ${e.message}")
            ""
        }
    }
}
