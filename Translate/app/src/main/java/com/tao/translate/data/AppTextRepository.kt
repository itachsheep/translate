package com.tao.translate.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppTextRepository {
    private val _capturedText = MutableStateFlow("")
    val capturedText: StateFlow<String> = _capturedText.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    fun updateCapturedText(text: String) {
        _capturedText.value = text
    }

    fun setOverlayVisible(visible: Boolean) {
        _isOverlayVisible.value = visible
    }
}
