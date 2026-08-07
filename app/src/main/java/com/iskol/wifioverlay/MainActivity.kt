package com.iskol.wifioverlay

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

/**
 * Невидимая точка входа. Запускается ярлыком с рабочего стола.
 * Если разрешение на оверлей уже выдано — сразу показывает оверлей и закрывается.
 * Если нет — открывает экран настройки разрешений.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            OverlayService.show(this)
        } else {
            startActivity(Intent(this, PermissionSetupActivity::class.java))
        }
        finish()
    }
}
