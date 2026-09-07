package com.tao.translate.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.tao.translate.service.TranslateAccessibilityService

object PermissionHelper {

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun overlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        )
    }

    fun accessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (TranslateAccessibilityService.isRunning()) return true

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val expected = "${context.packageName}/${TranslateAccessibilityService::class.java.name}"
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun areAllPermissionsGranted(context: Context): Boolean {
        return canDrawOverlays(context) && isAccessibilityServiceEnabled(context)
    }

    fun notificationPermissionIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            null
        }
    }
}
