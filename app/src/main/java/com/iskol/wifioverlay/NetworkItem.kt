package com.iskol.wifioverlay

data class NetworkItem(
    val ssid: String,
    val isSecured: Boolean,
    val level: Int,
    val frequency: Int,
    val channelWidthMhz: Int?,
    val isConnected: Boolean,
    val isSaved: Boolean
)
