package com.tao.translate.translation

class TranslationRepository {
    private val cloudEngine = GoogleCloudTranslationEngine()
    private val localEngine = MlKitTranslationEngine()

    suspend fun translateSentences(
        sentences: List<String>,
        requestedDirection: TranslationDirection,
    ): TranslationBatchResult {
        if (sentences.isEmpty()) {
            return TranslationBatchResult(
                sentences = emptyList(),
                direction = requestedDirection,
                sourceLanguage = "en",
                targetLanguage = "zh",
                engine = TranslationEngineType.ML_KIT,
            )
        }

        val combinedText = sentences.joinToString("\n")
        val direction = LanguageDetector.resolveDirection(combinedText, requestedDirection)
        val (sourceLanguage, targetLanguage) = when (direction) {
            TranslationDirection.EN_TO_ZH -> "en" to "zh"
            TranslationDirection.ZH_TO_EN -> "zh" to "en"
            TranslationDirection.AUTO -> LanguageDetector.toLanguageCodes(direction)
        }

        if (cloudEngine.isAvailable()) {
            runCatching {
                val translated = cloudEngine.translateBatch(sentences, sourceLanguage, targetLanguage)
                return buildBatchResult(sentences, translated, direction, sourceLanguage, targetLanguage, TranslationEngineType.GOOGLE_CLOUD)
            }
        }

        val translated = localEngine.translateBatch(sentences, sourceLanguage, targetLanguage)
        return buildBatchResult(sentences, translated, direction, sourceLanguage, targetLanguage, TranslationEngineType.ML_KIT)
    }

    suspend fun translate(
        text: String,
        requestedDirection: TranslationDirection,
    ): TranslationResult {
        val batch = translateSentences(listOf(text), requestedDirection)
        val translated = batch.sentences.firstOrNull()?.translated ?: ""
        return TranslationResult(
            text = translated,
            direction = batch.direction,
            sourceLanguage = batch.sourceLanguage,
            targetLanguage = batch.targetLanguage,
            engine = batch.engine,
        )
    }

    private fun buildBatchResult(
        originals: List<String>,
        translated: List<String>,
        direction: TranslationDirection,
        sourceLanguage: String,
        targetLanguage: String,
        engine: TranslationEngineType,
    ): TranslationBatchResult {
        val pairs = originals.mapIndexed { index, original ->
            SentenceTranslation(
                original = original,
                translated = translated.getOrElse(index) { "" },
            )
        }
        return TranslationBatchResult(
            sentences = pairs,
            direction = direction,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            engine = engine,
        )
    }

    fun close() {
        localEngine.close()
    }
}
