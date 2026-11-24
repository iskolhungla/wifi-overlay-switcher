package com.iskol.wifioverlay

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * На этой прошивке (HyperOS) `su <uid> -c "команда"` не выполняет полноценный
 * переход в нужный SELinux-домен — падает с той же ошибкой, что и под root.
 * А вот интерактивная сессия `su <uid>` (без -c), в которую команда подаётся
 * через stdin (как будто её печатают в терминале), проходит полный переход
 * через демон Magisk и работает. Поэтому все команды идут именно так.
 */
object RootShell {
    private const val TAG = "RootShell"

    /** Все su-сессии идут строго по одной: параллельные `su 2000` на этой прошивке мешают друг другу. */
    private val suLock = ReentrantLock(true)

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Тайминги su-вызовов (для диагностики «вечного сканирования»); только если включён отладочный дамп. */
    private fun timingLog(line: String) {
        val ctx = appContext ?: return
        try {
            if (!AppSettings.isDebugDumpEnabled(ctx)) return
            val f = File(ctx.filesDir, "root_timing.txt")
            if (f.length() > 200_000) f.writeText("")
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            f.appendText("$ts $line\n")
        } catch (_: Exception) {
        }
    }

    /** Прямой root (домен magisk) — для операций, не завязанных на системные binder-сервисы. */
    fun execAsRoot(command: String, timeoutMs: Long = 10000): Pair<Boolean, String> {
        val t0 = System.currentTimeMillis()
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { output.append(it).append('\n') }
                } catch (_: Exception) {
                }
            }
            reader.start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()
            reader.join(1000)
            val exit = if (finished) process.exitValue() else -1
            timingLog("[root: ${command.take(60)}] took=${System.currentTimeMillis() - t0}ms exit=$exit len=${output.length}${if (finished) "" else " TIMEOUT"}")
            Pair(finished && exit == 0, output.toString())
        } catch (e: Exception) {
            Log.e(TAG, "root exec failed for [$command]: ${e.message}")
            Pair(false, e.message ?: "")
        }
    }

    /** Псевдо-интерактивная su-сессия от имени указанного uid (для uid=2000 — "shell"). */
    fun execAsUid(uid: Int, command: String, timeoutMs: Long = 8000): String {
        val queuedAt = System.currentTimeMillis()
        if (!suLock.tryLock(40, TimeUnit.SECONDS)) {
            timingLog("[$command] SKIPPED: su queue busy >40s")
            return ""
        }
        try {
            val waited = System.currentTimeMillis() - queuedAt
            val t0 = System.currentTimeMillis()
            var out = execAsUidOnce(uid, command, timeoutMs)
            var took = System.currentTimeMillis() - t0
            timingLog("[$command] waited=${waited}ms took=${took}ms len=${out.length}")
            // Холодный старт: если ответ пустой ИЗ-ЗА БЫСТРОГО сбоя (не таймаута), повторяем один раз
            // с тем же таймаутом. Медленный пустой ответ не повторяем — это только удваивало зависание.
            if (out.isBlank() && took < timeoutMs / 2) {
                val t1 = System.currentTimeMillis()
                out = execAsUidOnce(uid, command, timeoutMs)
                took = System.currentTimeMillis() - t1
                timingLog("[$command] RETRY took=${took}ms len=${out.length}")
            }
            return out
        } finally {
            suLock.unlock()
        }
    }

    private fun execAsUidOnce(uid: Int, command: String, timeoutMs: Long): String {
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
