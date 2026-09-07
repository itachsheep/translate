package com.tao.translate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.tao.translate.service.OverlayService
import com.tao.translate.ui.MainScreen
import com.tao.translate.ui.theme.TranslateTheme
import com.tao.translate.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private var isAccessibilityEnabled by mutableStateOf(false)
    private var isOverlayGranted by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshPermissionState()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        startOverlayServiceIfReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OverlayService.createNotificationChannel(this)
        enableEdgeToEdge()
        refreshPermissionState()

        setContent {
            TranslateTheme {
                MainScreen(
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isOverlayGranted = isOverlayGranted,
                    isServiceRunning = isServiceRunning,
                    onOpenAccessibilitySettings = {
                        startActivity(PermissionHelper.accessibilitySettingsIntent())
                    },
                    onOpenOverlaySettings = {
                        overlayPermissionLauncher.launch(
                            PermissionHelper.overlayPermissionIntent(this),
                        )
                    },
                    onStartService = { requestNotificationAndStart() },
                    onStopService = {
                        OverlayService.stop(this)
                        isServiceRunning = false
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        isAccessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(this)
        isOverlayGranted = PermissionHelper.canDrawOverlays(this)
    }

    private fun requestNotificationAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startOverlayServiceIfReady()
        }
    }

    private fun startOverlayServiceIfReady() {
        if (!PermissionHelper.areAllPermissionsGranted(this)) return
        OverlayService.start(this)
        isServiceRunning = true
    }
}
