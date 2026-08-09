package com.iskol.wifioverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.Formatter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "wifi_overlay_channel"
        private const val NOTIF_ID = 1
        private const val OVERLAY_WIDTH_FRACTION = 0.8

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

        val overlayWidth = (resources.displayMetrics.widthPixels * OVERLAY_WIDTH_FRACTION).toInt()

        val params = WindowManager.LayoutParams(
            overlayWidth,
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
        adapter = NetworkAdapter(
            onNetworkClick = { network -> onNetworkSelected(network) },
            onNetworkLongClick = { network -> showNetworkDetail(network) }
        )
        recycler.adapter = adapter

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismissOverlay() }
        view.findViewById<View>(R.id.btnRefresh).setOnClickListener { triggerScan() }
        view.findViewById<View>(R.id.btnDetailClose).setOnClickListener { hideNetworkDetail() }
        view.findViewById<View>(R.id.btnDetailCopyAll).setOnClickListener { copyAllDetailFields() }

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

    private fun setBusy(busy: Boolean) {
        overlayView?.findViewById<ProgressBar>(R.id.scanProgress)?.visibility =
            if (busy) View.VISIBLE else View.GONE
    }

    private fun triggerScan() {
        @Suppress("DEPRECATION")
        wifiManager.startScan()
        setStatus(getString(R.string.scanning))
        setBusy(true)
    }

    private fun setStatus(text: String) {
        overlayView?.findViewById<TextView>(R.id.tvStatus)?.text = text
    }

    private fun refreshNetworks() {
        Thread {
            val scanEntries = RootWifiControl.scanNetworks()
            val currentSsid = RootWifiControl.currentSsid()
            val savedNames = RootWifiControl.listSavedNetworkNames()

            val items = scanEntries
                .distinctBy { it.ssid }
                .map { entry ->
                    NetworkItem(
                        ssid = entry.ssid,
                        isSecured = entry.secured,
                        level = entry.rssi,
                        isConnected = entry.ssid == currentSsid,
                        isSaved = savedNames.contains(entry.ssid)
                    )
                }
                .sortedWith(
                    compareByDescending<NetworkItem> { it.isConnected }
                        .thenByDescending { it.level }
                )

            mainHandler.post {
                adapter.submit(items)
                setBusy(false)
                setStatus(
                    if (items.isEmpty()) getString(R.string.no_networks)
                    else getString(R.string.networks_found, items.size)
                )
            }
        }.start()
    }

    private fun onNetworkSelected(network: NetworkItem) {
        if (network.isConnected) return
        if (!network.isSecured) {
            connectAndReport(network, null)
            return
        }
        val cached = PasswordStore.get(this, network.ssid)
        if (cached != null) {
            connectAndReport(network, cached)
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
        input.hint = if (network.isSaved) {
            getString(R.string.password_hint_saved)
        } else {
            getString(R.string.password_hint)
        }
        promptContainer.visibility = View.VISIBLE
        recycler.visibility = View.GONE

        view.findViewById<View>(R.id.btnPasswordCancel).setOnClickListener {
            promptContainer.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }
        view.findViewById<View>(R.id.btnPasswordConnect).setOnClickListener {
            val pass = input.text.toString().ifBlank { null }
            promptContainer.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            connectAndReport(network, pass)
        }
    }

    private fun connectAndReport(network: NetworkItem, password: String?) {
        setStatus(getString(R.string.connecting, network.ssid))
        setBusy(true)
        Thread {
            val ok = WifiConnector.connect(this, network.ssid, password)
            if (ok && !password.isNullOrEmpty()) {
                PasswordStore.put(this, network.ssid, password)
            }
            mainHandler.post {
                setStatus(
                    if (ok) getString(R.string.connected, network.ssid)
                    else getString(R.string.connect_failed, network.ssid)
                )
                triggerScan()
            }
        }.start()
    }

    // ---- Детали сети (долгий тап) ----

    private fun showNetworkDetail(network: NetworkItem) {
        val view = overlayView ?: return
        val detailContainer = view.findViewById<LinearLayout>(R.id.networkDetailContainer)
        val recycler = view.findViewById<View>(R.id.recyclerNetworks)
        val fieldsContainer = view.findViewById<LinearLayout>(R.id.detailFieldsContainer)
        val title = view.findViewById<TextView>(R.id.tvDetailTitle)

        title.text = network.ssid
        fieldsContainer.removeAllViews()

        when {
            network.isConnected -> {
                collectLiveConnectionInfo().forEach { (label, value) ->
                    addDetailRow(fieldsContainer, label, value)
                }
            }
            network.isSaved -> {
                addDetailRow(fieldsContainer, getString(R.string.detail_archived_title), getString(R.string.detail_archived_body))
            }
            else -> {
                addDetailRow(fieldsContainer, getString(R.string.detail_unknown_title), getString(R.string.detail_unknown_body))
            }
        }

        recycler.visibility = View.GONE
        detailContainer.visibility = View.VISIBLE
    }

    private fun hideNetworkDetail() {
        val view = overlayView ?: return
        view.findViewById<View>(R.id.networkDetailContainer).visibility = View.GONE
        view.findViewById<View>(R.id.recyclerNetworks).visibility = View.VISIBLE
    }

    private fun collectLiveConnectionInfo(): List<Pair<String, String>> {
        @Suppress("DEPRECATION")
        val dhcp = wifiManager.dhcpInfo ?: return emptyList()
        fun fmt(ip: Int): String = Formatter.formatIpAddress(ip)

        val list = mutableListOf<Pair<String, String>>()
        list.add(getString(R.string.detail_ip) to fmt(dhcp.ipAddress))
        list.add(getString(R.string.detail_gateway) to fmt(dhcp.gateway))
        list.add(getString(R.string.detail_netmask) to fmt(dhcp.netmask))
        if (dhcp.dns1 != 0) list.add(getString(R.string.detail_dns1) to fmt(dhcp.dns1))
        if (dhcp.dns2 != 0) list.add(getString(R.string.detail_dns2) to fmt(dhcp.dns2))
        if (dhcp.serverAddress != 0) list.add(getString(R.string.detail_dhcp_server) to fmt(dhcp.serverAddress))
        return list
    }

    private fun addDetailRow(container: LinearLayout, label: String, value: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_detail_row, container, false)
        row.findViewById<TextView>(R.id.tvLabel).text = label
        row.findViewById<TextView>(R.id.tvValue).text = value
        row.findViewById<View>(R.id.btnCopy).setOnClickListener {
            copyToClipboard(value)
        }
        container.addView(row)
    }

    private fun copyAllDetailFields() {
        val view = overlayView ?: return
        val fieldsContainer = view.findViewById<LinearLayout>(R.id.detailFieldsContainer)
        val builder = StringBuilder()
        for (i in 0 until fieldsContainer.childCount) {
            val row = fieldsContainer.getChildAt(i)
            val label = row.findViewById<TextView>(R.id.tvLabel).text
            val value = row.findViewById<TextView>(R.id.tvValue).text
            builder.append(label).append(": ").append(value).append('\n')
        }
        copyToClipboard(builder.toString().trim())
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("wifi-overlay", text))
        setStatus(getString(R.string.copied))
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
