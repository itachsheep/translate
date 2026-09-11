package com.tao.translate.translation

enum class TranslationDirection {
    AUTO,
    EN_TO_ZH,
    ZH_TO_EN,
}

enum class TranslationEngineType {
    GOOGLE_CLOUD,
    ML_KIT,
}

data class TranslationResult(
    val text: String,
    val direction: TranslationDirection,
    val sourceLanguage: String,
    val targetLanguage: String,
    val engine: TranslationEngineType,
)
