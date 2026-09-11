package com.tao.translate.translation

object LanguageDetector {

    fun resolveDirection(text: String, requested: TranslationDirection): TranslationDirection {
        if (requested != TranslationDirection.AUTO) return requested
        return if (countCjk(text) >= countLatinLetters(text)) {
            TranslationDirection.ZH_TO_EN
        } else {
            TranslationDirection.EN_TO_ZH
        }
    }

    fun toLanguageCodes(direction: TranslationDirection): Pair<String, String> {
        return when (direction) {
            TranslationDirection.EN_TO_ZH -> "en" to "zh"
            TranslationDirection.ZH_TO_EN -> "zh" to "en"
            TranslationDirection.AUTO -> "en" to "zh"
        }
    }

    private fun countCjk(text: String): Int {
        return text.count { it in '\u4e00'..'\u9fff' }
    }

    private fun countLatinLetters(text: String): Int {
        return text.count { it.isLetter() && it.code < 128 }
    }
}
