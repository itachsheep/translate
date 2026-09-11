package com.tao.translate.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class TextRecognitionHelper {
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chineseRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build(),
    )

    suspend fun recognize(bitmap: Bitmap): RecognitionResult {
        val processed = BitmapPreprocessor.prepare(bitmap)
        val lines = coroutineScope {
            val latinDeferred = async { recognizeLatin(processed) }
            val chineseDeferred = async { recognizeChinese(processed) }
            TextPostProcessor.mergeLines(latinDeferred.await(), chineseDeferred.await())
        }
        processed.recycle()

        val sentences = SentenceGrouper.group(lines)
        return RecognitionResult(sentences = sentences)
    }

    private suspend fun recognizeLatin(bitmap: Bitmap): List<RecognizedLine> {
        return runCatching {
            val result = latinRecognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            TextPostProcessor.extractLines(result)
        }.getOrDefault(emptyList())
    }

    private suspend fun recognizeChinese(bitmap: Bitmap): List<RecognizedLine> {
        return runCatching {
            val result = chineseRecognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            TextPostProcessor.extractLines(result)
        }.getOrDefault(emptyList())
    }

    fun close() {
        latinRecognizer.close()
        chineseRecognizer.close()
    }
}
