package com.tao.translate.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class MlKitTranslationEngine {

    private val translators = mutableMapOf<Pair<String, String>, Translator>()

    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String {
        val translator = getTranslator(sourceLanguage, targetLanguage)
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()
        return translator.translate(text).await()
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }

    private fun getTranslator(sourceLanguage: String, targetLanguage: String): Translator {
        val key = sourceLanguage to targetLanguage
        return translators.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(toMlKitLanguage(sourceLanguage))
                .setTargetLanguage(toMlKitLanguage(targetLanguage))
                .build()
            Translation.getClient(options)
        }
    }

    private fun toMlKitLanguage(code: String): String {
        return when (code) {
            "en" -> TranslateLanguage.ENGLISH
            "zh" -> TranslateLanguage.CHINESE
            else -> throw IllegalArgumentException("不支持的语言: $code")
        }
    }
}
