package com.iskol.wifioverlay

import android.content.Context
import android.util.Base64
import com.tananaev.adblib.AdbBase64
import com.tananaev.adblib.AdbCrypto
import java.io.File

object AdbKeyManager {

    private val base64Impl = object : AdbBase64 {
        override fun encodeToString(data: ByteArray): String =
            Base64.encodeToString(data, Base64.NO_WRAP)
    }

    fun loadOrCreate(context: Context): AdbCrypto {
        val dir = context.filesDir
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        return if (priv.exists() && pub.exists()) {
            try {
                AdbCrypto.loadAdbKeyPair(base64Impl, priv, pub)
            } catch (e: Exception) {
                generateAndSave(priv, pub)
            }
        } else {
            generateAndSave(priv, pub)
        }
    }

    private fun generateAndSave(priv: File, pub: File): AdbCrypto {
        val crypto = AdbCrypto.generateAdbKeyPair(base64Impl)
        crypto.saveAdbKeyPair(priv, pub)
        return crypto
    }

    /** Строка в формате adb_keys (то же самое, что отправляется в AUTH-пакете). */
    fun publicKeyLine(crypto: AdbCrypto): String {
        return String(crypto.getAdbPublicKeyPayload(), Charsets.UTF_8).trim()
    }
}
