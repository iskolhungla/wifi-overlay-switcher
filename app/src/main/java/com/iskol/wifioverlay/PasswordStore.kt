package com.iskol.wifioverlay

import android.content.Context

/**
 * Хранит пароли только тех сетей, к которым мы САМИ успешно подключились
 * через это приложение (Android не отдаёт пароли уже сохранённых системой
 * сетей — ни через API, ни через root). Обычный private SharedPreferences,
 * без доп. шифрования — файл доступен только нашему приложению.
 */
object PasswordStore {
    private const val PREFS = "wifi_overlay_passwords"

    fun get(context: Context, ssid: String): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ssid, null)
    }

    fun put(context: Context, ssid: String, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ssid, password)
            .apply()
    }
}
