package com.tao.translate.data

import com.tao.translate.translation.SentenceTranslation
import com.tao.translate.translation.TranslationDirection
import com.tao.translate.translation.TranslationEngineType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppTextRepository {
    private val _sentenceTranslations = MutableStateFlow<List<SentenceTranslation>>(emptyList())
    val sentenceTranslations: StateFlow<List<SentenceTranslation>> = _sentenceTranslations.asStateFlow()

    private val _translationDirection = MutableStateFlow(TranslationDirection.AUTO)
    val translationDirection: StateFlow<TranslationDirection> = _translationDirection.asStateFlow()

    private val _translationEngine = MutableStateFlow<TranslationEngineType?>(null)
    val translationEngine: StateFlow<TranslationEngineType?> = _translationEngine.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _recognitionHint = MutableStateFlow<String?>(null)
    val recognitionHint: StateFlow<String?> = _recognitionHint.asStateFlow()

    private val _translationHint = MutableStateFlow<String?>(null)
    val translationHint: StateFlow<String?> = _translationHint.asStateFlow()

    fun updateSentenceTranslations(sentences: List<SentenceTranslation>) {
        _sentenceTranslations.value = sentences
    }

    fun setTranslationDirection(direction: TranslationDirection) {
        _translationDirection.value = direction
    }

    fun setTranslationEngine(engine: TranslationEngineType?) {
        _translationEngine.value = engine
    }

    fun setRecognitionHint(hint: String?) {
        _recognitionHint.value = hint
    }

    fun setTranslationHint(hint: String?) {
        _translationHint.value = hint
    }

    fun setOverlayVisible(visible: Boolean) {
        _isOverlayVisible.value = visible
    }

    fun setRecognizing(recognizing: Boolean) {
        _isRecognizing.value = recognizing
    }

    fun setTranslating(translating: Boolean) {
        _isTranslating.value = translating
    }

    fun clearTranslation() {
        _sentenceTranslations.value = emptyList()
        _translationEngine.value = null
        _translationHint.value = null
    }
}
