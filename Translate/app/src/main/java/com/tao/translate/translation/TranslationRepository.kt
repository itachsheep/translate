package com.tao.translate.translation

class TranslationRepository {
    private val cloudEngine = GoogleCloudTranslationEngine()
    private val localEngine = MlKitTranslationEngine()

    suspend fun translate(
        text: String,
        requestedDirection: TranslationDirection,
    ): TranslationResult {
        val direction = LanguageDetector.resolveDirection(text, requestedDirection)
        val (sourceLanguage, targetLanguage) = when (direction) {
            TranslationDirection.EN_TO_ZH -> "en" to "zh"
            TranslationDirection.ZH_TO_EN -> "zh" to "en"
            TranslationDirection.AUTO -> LanguageDetector.toLanguageCodes(direction)
        }

        if (cloudEngine.isAvailable()) {
            runCatching {
                val translated = cloudEngine.translate(text, sourceLanguage, targetLanguage)
                return TranslationResult(
                    text = translated,
                    direction = direction,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    engine = TranslationEngineType.GOOGLE_CLOUD,
                )
            }
        }

        val translated = localEngine.translate(text, sourceLanguage, targetLanguage)
        return TranslationResult(
            text = translated,
            direction = direction,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            engine = TranslationEngineType.ML_KIT,
        )
    }

    fun close() {
        localEngine.close()
    }
}
