package com.iskol.wifioverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // История подключений ведётся всегда; галочка в настройках управляет только текстовым файлом.
            WifiLogService.start(context)
        }
    }
}
