package com.tao.translate.translation

import com.tao.translate.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GoogleCloudTranslationEngine {

    fun isAvailable(): Boolean = BuildConfig.GOOGLE_TRANSLATE_API_KEY.isNotBlank()

    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String {
        val apiKey = BuildConfig.GOOGLE_TRANSLATE_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException("未配置 Google 翻译 API Key")
        }

        return withContext(Dispatchers.IO) {
            val endpoint =
                "https://translation.googleapis.com/language/translate/v2?key=" +
                    URLEncoder.encode(apiKey, Charsets.UTF_8.name())

            val requestBody = JSONObject().apply {
                put("q", text)
                put("source", sourceLanguage)
                put("target", toGoogleTargetLanguage(targetLanguage))
                put("format", "text")
            }.toString()

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
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

                val json = JSONObject(body)
                val translations = json.getJSONObject("data").getJSONArray("translations")
                translations.getJSONObject(0).getString("translatedText")
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
