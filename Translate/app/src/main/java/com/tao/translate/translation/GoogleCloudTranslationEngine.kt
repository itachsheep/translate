package com.tao.translate.translation

import com.tao.translate.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GoogleCloudTranslationEngine {

    fun isAvailable(): Boolean = BuildConfig.GOOGLE_TRANSLATE_API_KEY.isNotBlank()

    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String {
        return translateBatch(listOf(text), sourceLanguage, targetLanguage).first()
    }

    suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<String> {
        if (texts.isEmpty()) return emptyList()

        val apiKey = BuildConfig.GOOGLE_TRANSLATE_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException("未配置 Google 翻译 API Key")
        }

        return withContext(Dispatchers.IO) {
            val endpoint =
                "https://translation.googleapis.com/language/translate/v2?key=" +
                    URLEncoder.encode(apiKey, Charsets.UTF_8.name())

            val requestBody = JSONObject().apply {
                put("q", JSONArray(texts))
                put("source", sourceLanguage)
                put("target", toGoogleTargetLanguage(targetLanguage))
                put("format", "text")
            }.toString()

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 20_000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                }

                val responseCode = connection.responseCode
                val body = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText()
                        ?: "HTTP $responseCode"
                }

                if (responseCode !in 200..299) {
                    throw IllegalStateException(parseErrorMessage(body))
                }

                val translations = JSONObject(body)
                    .getJSONObject("data")
                    .getJSONArray("translations")

                buildList {
                    for (index in 0 until translations.length()) {
                        add(translations.getJSONObject(index).getString("translatedText"))
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun toGoogleTargetLanguage(code: String): String {
        return when (code) {
            "zh" -> "zh-CN"
            "en" -> "en"
            else -> code
        }
    }

    private fun parseErrorMessage(body: String): String {
        return runCatching {
            JSONObject(body).getJSONObject("error").getString("message")
        }.getOrDefault("云端翻译失败")
    }
}
