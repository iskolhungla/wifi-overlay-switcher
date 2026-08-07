package com.iskol.wifioverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "wifi_overlay_channel"
        private const val NOTIF_ID = 1

        fun show(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var wifiManager: WifiManager
    private lateinit var adapter: NetworkAdapter
    private var overlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshNetworks()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        startForegroundNotification()
        registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        showOverlay()
        triggerScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            showOverlay()
        }
        triggerScan()
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_wifi_tile)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_wifi_list, null)
        overlayView = view

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 100

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerNetworks)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = NetworkAdapter { network -> onNetworkSelected(network) }
        recycler.adapter = adapter

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismissOverlay() }
        view.findViewById<View>(R.id.btnRefresh).setOnClickListener { triggerScan() }

        windowManager.addView(view, params)
    }

    private fun dismissOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        stopSelf()
    }

    private fun triggerScan() {
        @Suppress("DEPRECATION")
        wifiManager.startScan()
        setStatus(getString(R.string.scanning))
    }

    private fun setStatus(text: String) {
        overlayView?.findViewById<TextView>(R.id.tvStatus)?.text = text
    }

    private fun refreshNetworks() {
        val results: List<ScanResult> = try {
            @Suppress("MissingPermission")
            wifiManager.scanResults
        } catch (e: SecurityException) {
            setStatus(getString(R.string.no_permission))
            emptyList()
        }

        Thread {
            val currentSsid = RootWifiControl.currentSsid()
            val savedNames = RootWifiControl.listSavedNetworkNames()

            val items = results
                .distinctBy { it.SSID }
                .filter { it.SSID.isNotBlank() }
                .sortedByDescending { it.level }
                .map { sr ->
                    NetworkItem(
                        ssid = sr.SSID,
                        isSecured = sr.capabilities.contains("WPA") ||
                            sr.capabilities.contains("WEP") ||
                            sr.capabilities.contains("EAP") ||
                            sr.capabilities.contains("SAE"),
                        level = sr.level,
                        isConnected = sr.SSID == currentSsid,
                        isSaved = savedNames.contains(sr.SSID)
                    )
                }

            mainHandler.post {
                adapter.submit(items)
                setStatus(
                    if (items.isEmpty()) getString(R.string.no_networks)
                    else getString(R.string.networks_found, items.size)
                )
            }
        }.start()
    }

    private fun onNetworkSelected(network: NetworkItem) {
        if (network.isConnected) return
        if (network.isSaved || !network.isSecured) {
            connectAndReport(network.ssid, null)
        } else {
            showPasswordPrompt(network)
        }
    }

    private fun showPasswordPrompt(network: NetworkItem) {
        val view = overlayView ?: return
        val promptContainer = view.findViewById<View>(R.id.passwordPromptContainer)
        val recycler = view.findViewById<View>(R.id.recyclerNetworks)
        val ssidLabel = view.findViewById<TextView>(R.id.tvPasswordSsid)
        val input = view.findViewById<EditText>(R.id.etPassword)

        ssidLabel.text = network.ssid
        input.setText("")
        promptContainer.visibility = View.VISIBLE
        recycler.visibility = View.GONE

        view.findViewById<View>(R.id.btnPasswordCancel).setOnClickListener {
            promptContainer.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }
        view.findViewById<View>(R.id.btnPasswordConnect).setOnClickListener {
            val pass = input.text.toString()
            promptContainer.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            connectAndReport(network.ssid, pass)
        }
    }

    private fun connectAndReport(ssid: String, password: String?) {
        setStatus(getString(R.string.connecting, ssid))
        Thread {
            val ok = RootWifiControl.connect(ssid, password)
            mainHandler.post {
                setStatus(
                    if (ok) getString(R.string.connected, ssid)
                    else getString(R.string.connect_failed, ssid)
                )
                triggerScan()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scanReceiver)
        } catch (_: Exception) {
        }
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
    }
}
