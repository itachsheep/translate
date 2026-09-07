package com.tao.translate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    isServiceRunning: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "屏幕翻译助手",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "从屏幕侧边唤出可拖动的悬浮圆球，点击展开半屏翻译面板，自动识别当前 App 文字。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(
            title = "无障碍服务",
            description = if (isAccessibilityEnabled) "已开启，可读取前台 App 文字" else "未开启，请前往系统设置授权",
            isGranted = isAccessibilityEnabled,
            buttonText = if (isAccessibilityEnabled) "已开启" else "去开启",
            onClick = onOpenAccessibilitySettings,
            enabled = !isAccessibilityEnabled,
        )

        PermissionCard(
            title = "悬浮窗权限",
            description = if (isOverlayGranted) "已授权，可显示悬浮面板" else "未授权，请允许显示在其他应用上层",
            isGranted = isOverlayGranted,
            buttonText = if (isOverlayGranted) "已授权" else "去授权",
            onClick = onOpenOverlaySettings,
            enabled = !isOverlayGranted,
        )

        val canStart = isAccessibilityEnabled && isOverlayGranted

        Button(
            onClick = onStartService,
            enabled = canStart && !isServiceRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("启动悬浮服务")
        }

        OutlinedButton(
            onClick = onStopService,
            enabled = isServiceRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("停止悬浮服务")
        }

        if (isServiceRunning) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "服务运行中",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "切换到任意 App，点击悬浮圆球展开翻译面板，拖动圆球可移动位置。关闭面板后圆球会重新出现。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onClick, enabled = enabled) {
                Text(buttonText)
            }
        }
    }
}
