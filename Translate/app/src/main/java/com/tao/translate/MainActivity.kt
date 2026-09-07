package com.tao.translate

import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
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
import com.tao.translate.ui.ScreenCaptureExplainerDialog
import com.tao.translate.ui.theme.TranslateTheme
import com.tao.translate.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private var isOverlayGranted by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
    private var showScreenCaptureExplainer by mutableStateOf(false)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshPermissionState()
        if (PermissionHelper.canDrawOverlays(this)) {
            showScreenCaptureExplainerDialog()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            OverlayService.start(this, result.resultCode, result.data!!)
            isServiceRunning = true
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        showScreenCaptureExplainerDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OverlayService.createNotificationChannel(this)
        enableEdgeToEdge()
        refreshPermissionState()

        setContent {
            TranslateTheme {
                MainScreen(
                    isOverlayGranted = isOverlayGranted,
                    isServiceRunning = isServiceRunning,
                    onOpenOverlaySettings = {
                        overlayPermissionLauncher.launch(
                            PermissionHelper.overlayPermissionIntent(this),
                        )
                    },
                    onStartService = { startServiceWithPermissions() },
                    onStopService = {
                        OverlayService.stop(this)
                        isServiceRunning = false
                    },
                )

                if (showScreenCaptureExplainer) {
                    ScreenCaptureExplainerDialog(
                        onDismiss = { showScreenCaptureExplainer = false },
                        onConfirm = {
                            showScreenCaptureExplainer = false
                            requestScreenCaptureAndStart()
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        isOverlayGranted = PermissionHelper.canDrawOverlays(this)
    }

    private fun startServiceWithPermissions() {
        if (!PermissionHelper.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(PermissionHelper.overlayPermissionIntent(this))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        showScreenCaptureExplainerDialog()
    }

    private fun showScreenCaptureExplainerDialog() {
        showScreenCaptureExplainer = true
    }

    private fun requestScreenCaptureAndStart() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
