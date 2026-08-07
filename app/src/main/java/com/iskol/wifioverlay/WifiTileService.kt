package com.iskol.wifioverlay

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class WifiTileService : TileService() {

    override fun onClick() {
        super.onClick()
        OverlayService.show(this)
        qsTile?.let {
            it.state = Tile.STATE_ACTIVE
            it.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }
}
