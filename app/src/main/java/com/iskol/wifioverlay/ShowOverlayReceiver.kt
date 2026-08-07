package com.iskol.wifioverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Позволяет вызывать/скрывать оверлей извне, например через adb:
 *   adb shell am broadcast -a com.iskol.wifioverlay.SHOW_OVERLAY -p com.iskol.wifioverlay
 *   adb shell am broadcast -a com.iskol.wifioverlay.HIDE_OVERLAY -p com.iskol.wifioverlay
 */
class ShowOverlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.iskol.wifioverlay.SHOW_OVERLAY" -> OverlayService.show(context)
            "com.iskol.wifioverlay.HIDE_OVERLAY" -> OverlayService.hide(context)
        }
    }
}
