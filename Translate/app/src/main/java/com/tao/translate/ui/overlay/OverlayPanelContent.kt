package com.tao.translate.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tao.translate.translation.TranslationDirection
import com.tao.translate.translation.TranslationEngineType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlayPanelContent(
    capturedText: String,
    translatedText: String,
    isRecognizing: Boolean,
    isTranslating: Boolean,
    translationDirection: TranslationDirection,
    translationEngine: TranslationEngineType?,
    recognitionHint: String? = null,
    translationHint: String? = null,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onDirectionChange: (TranslationDirection) -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> },
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanelHeader(
                onRefresh = onRefresh,
                onClose = onClose,
                onDragStart = onDragStart,
                onDrag = onDrag,
            )

            HorizontalDivider()

            DirectionSelector(
                selected = translationDirection,
                enabled = !isRecognizing && !isTranslating,
                onDirectionChange = onDirectionChange,
            )

            HorizontalDivider()

            when {
                isRecognizing -> LoadingState("正在识别屏幕文字…")
                capturedText.isBlank() -> EmptyState(
                    recognitionHint ?: "未识别到文字\n请切换到目标内容后点击刷新",
                )
                else -> TranslationContent(
                    capturedText = capturedText,
                    translatedText = translatedText,
                    isTranslating = isTranslating,
                    translationEngine = translationEngine,
                    translationHint = translationHint,
                )
            }
        }
    }
}

@Composable
private fun PanelHeader(
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(onDragStart = onDragStart, onDrag = onDrag)
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        },
                    )
                },
        ) {
            Text(
                text = "屏幕翻译",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "关闭")
        }
    }
}

@Composable
private fun DragHandle(
    onDragStart: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(24.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionSelector(
    selected: TranslationDirection,
    enabled: Boolean,
    onDirectionChange: (TranslationDirection) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DirectionChip("自动", TranslationDirection.AUTO, selected, enabled, onDirectionChange)
        DirectionChip("英译中", TranslationDirection.EN_TO_ZH, selected, enabled, onDirectionChange)
        DirectionChip("中译英", TranslationDirection.ZH_TO_EN, selected, enabled, onDirectionChange)
    }
}

@Composable
private fun DirectionChip(
    label: String,
    direction: TranslationDirection,
    selected: TranslationDirection,
    enabled: Boolean,
    onDirectionChange: (TranslationDirection) -> Unit,
) {
    FilterChip(
        selected = selected == direction,
        onClick = { onDirectionChange(direction) },
        enabled = enabled,
        label = { Text(label) },
    )
}

@Composable
private fun LoadingState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslationContent(
    capturedText: String,
    translatedText: String,
    isTranslating: Boolean,
    translationEngine: TranslationEngineType?,
    translationHint: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "原文",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = capturedText,
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "译文",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (translationEngine != null && !isTranslating) {
                Text(
                    text = when (translationEngine) {
                        TranslationEngineType.GOOGLE_CLOUD -> "云端翻译"
                        TranslationEngineType.ML_KIT -> "本地翻译"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isTranslating) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "正在翻译…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (translatedText.isNotBlank()) {
            Text(
                text = translatedText,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(
                text = translationHint ?: "翻译失败，请切换方向后重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
