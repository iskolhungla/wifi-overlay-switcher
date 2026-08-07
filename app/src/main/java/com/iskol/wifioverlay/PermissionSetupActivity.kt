package com.iskol.wifioverlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionSetupActivity : AppCompatActivity() {

    private val permissionsToRequest: Array<String> by lazy {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_setup)

        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnGrantRuntime).setOnClickListener {
            ActivityCompat.requestPermissions(this, permissionsToRequest, 100)
        }

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                OverlayService.show(this)
            }
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val status = findViewById<TextView>(R.id.tvStatus)
        val overlayOk = Settings.canDrawOverlays(this)
        val runtimeOk = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        status.text = getString(
            R.string.permission_status,
            if (overlayOk) "OK" else getString(R.string.status_missing),
            if (runtimeOk) "OK" else getString(R.string.status_missing)
        )
    }
}
