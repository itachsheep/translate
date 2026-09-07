package com.tao.translate.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object BitmapPreprocessor {

    fun prepare(source: Bitmap): Bitmap {
        val scaled = scaleUp(source)
        if (scaled !== source) {
            source.recycle()
        }
        val enhanced = enhanceContrast(scaled)
        if (enhanced !== scaled) {
            scaled.recycle()
        }
        return enhanced
    }

    private fun scaleUp(source: Bitmap): Bitmap {
        if (source.width >= 1440) return source

        val width = source.width * 2
        val height = source.height * 2
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun enhanceContrast(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, -18f,
                        0f, 1.25f, 0f, 0f, -18f,
                        0f, 0f, 1.25f, 0f, -18f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
