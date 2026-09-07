package com.tao.translate.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.tao.translate.data.AppTextRepository

class TranslateAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 文字在面板打开时按需抓取，避免频繁刷新
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun captureForegroundText(): String {
        val root = rootInActiveWindow ?: return ""
        return try {
            extractText(root)
        } finally {
            root.recycle()
        }
    }

    private fun extractText(root: AccessibilityNodeInfo): String {
        val texts = linkedSetOf<String>()
        traverseNode(root, texts)
        return texts.joinToString(separator = "\n\n")
    }

    private fun traverseNode(node: AccessibilityNodeInfo, texts: LinkedHashSet<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                traverseNode(child, texts)
            } finally {
                child.recycle()
            }
        }
    }

    companion object {
        @Volatile
        var instance: TranslateAccessibilityService? = null
            private set

        fun captureText(): String {
            val text = instance?.captureForegroundText().orEmpty()
            AppTextRepository.updateCapturedText(text)
            return text
        }

        fun isRunning(): Boolean = instance != null
    }
}
