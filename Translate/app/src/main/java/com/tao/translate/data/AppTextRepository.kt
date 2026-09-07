package com.tao.translate.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppTextRepository {
    private val _capturedText = MutableStateFlow("")
    val capturedText: StateFlow<String> = _capturedText.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing.asStateFlow()

    private val _recognitionHint = MutableStateFlow<String?>(null)
    val recognitionHint: StateFlow<String?> = _recognitionHint.asStateFlow()

    fun updateCapturedText(text: String) {
        _capturedText.value = text
    }

    fun setRecognitionHint(hint: String?) {
        _recognitionHint.value = hint
    }

    fun setOverlayVisible(visible: Boolean) {
        _isOverlayVisible.value = visible
    }

    fun setRecognizing(recognizing: Boolean) {
        _isRecognizing.value = recognizing
    }
}
