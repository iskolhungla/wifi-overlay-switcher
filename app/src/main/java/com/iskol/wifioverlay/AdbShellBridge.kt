package com.iskol.wifioverlay

import android.content.Context
import android.util.Log
import com.tananaev.adblib.AdbConnection
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Выполняет shell-команды через локальный ADB по TCP (127.0.0.1:5555).
 *
 * Зачем: на этой прошивке (HyperOS) root-процесс не может вызвать `cmd wifi ...`
 * напрямую — SELinux не даёт домену `magisk` перейти в домен `shell`, которому
 * такие вызовы разрешены. Обычный `adb shell` с компьютера работает, потому что
 * настоящий adbd запускает шелл именно в домене `shell`.
 *
 * Поэтому: root поднимает adbd по TCP и прописывает публичный ключ приложения
 * в /data/misc/adb/adb_keys (доверенный список) — дальше приложение само
 * подключается к 127.0.0.1:5555 как обычный adb-клиент и выполняет команды
 * уже в правильном домене, без единого диалога подтверждения.
 */
object AdbShellBridge {

    private const val TAG = "AdbShellBridge"
    private const val PORT = 5555
    private const val CONNECT_TIMEOUT_MS = 2000

    @Volatile
    private var verifiedReady = false

    fun ensureReady(context: Context): Boolean {
        if (verifiedReady && tryPing()) return true
        if (tryPing()) {
            verifiedReady = true
            return true
        }

        enableAdbTcpViaRoot()
        trustOwnKeyViaRoot(context)

        repeat(15) {
            if (tryPing()) {
                verifiedReady = true
                return true
            }
            Thread.sleep(300)
        }
        return tryPing().also { verifiedReady = it }
    }

    private fun tryPing(): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 500) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun enableAdbTcpViaRoot() {
        RootShell.exec("setprop service.adb.tcp.port $PORT")
        RootShell.exec("stop adbd")
        Thread.sleep(300)
        RootShell.exec("start adbd")
    }

    private fun trustOwnKeyViaRoot(context: Context) {
        val crypto = AdbKeyManager.loadOrCreate(context)
        val line = AdbKeyManager.publicKeyLine(crypto)
        val escaped = line.replace("'", "'\\''")

        RootShell.exec("mkdir -p /data/misc/adb")
        RootShell.exec("touch /data/misc/adb/adb_keys")
        RootShell.exec(
            "grep -qxF '$escaped' /data/misc/adb/adb_keys || echo '$escaped' >> /data/misc/adb/adb_keys"
        )
        RootShell.exec("chown system:shell /data/misc/adb/adb_keys")
        RootShell.exec("chmod 640 /data/misc/adb/adb_keys")
        RootShell.exec("restorecon /data/misc/adb/adb_keys")
    }

    /** Выполняет одну shell-команду в настоящем adb-shell домене и возвращает её вывод. */
    fun execShell(context: Context, command: String): String {
        if (!ensureReady(context)) {
            throw IOException("adbd недоступен по TCP на 127.0.0.1:$PORT")
        }
        val crypto = AdbKeyManager.loadOrCreate(context)
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", PORT), CONNECT_TIMEOUT_MS)
            val connection = AdbConnection.create(socket, crypto)
            connection.connect()
            val stream = connection.open("shell:$command")
            val out = StringBuilder()
            try {
                while (true) {
                    val data = stream.read() ?: break
                    out.append(String(data, Charsets.UTF_8))
                }
            } catch (e: IOException) {
                // поток закрылся с той стороны — обычное завершение команды
                Log.d(TAG, "stream closed for [$command]: ${e.message}")
            }
            try {
                connection.close()
            } catch (_: Exception) {
            }
            return out.toString()
        }
    }
}
