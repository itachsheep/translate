package com.tao.translate.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenCaptureManager(
    private val context: Context,
) {
    private var mediaProjection: MediaProjection? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val windowManager: WindowManager
        get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun init(resultCode: Int, data: Intent) {
        release()
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)?.also { projection ->
            projection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        release()
                    }
                },
                mainHandler,
            )
        }
    }

    fun isReady(): Boolean = mediaProjection != null

    suspend fun captureScreen(): Bitmap {
        val projection = mediaProjection
            ?: throw IllegalStateException("截图权限未就绪，请重新启动悬浮服务")

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        repeat(MAX_CAPTURE_ATTEMPTS) { attempt ->
            val bitmap = captureOnce(projection, width, height, density)
            if (!isMostlyBlank(bitmap)) {
                return bitmap
            }
            if (attempt < MAX_CAPTURE_ATTEMPTS - 1) {
                delay(CAPTURE_RETRY_DELAY_MS)
            }
        }

        throw IllegalStateException("截屏内容为空，请确认目标应用未禁止截屏后重试")
    }

    private suspend fun captureOnce(
        projection: MediaProjection,
        width: Int,
        height: Int,
        density: Int,
    ): Bitmap {
        return suspendCancellableCoroutine { continuation ->
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            var virtualDisplay: VirtualDisplay? = null

            val cleanup = {
                virtualDisplay?.release()
                imageReader.close()
            }

            continuation.invokeOnCancellation { cleanup() }

            virtualDisplay = projection.createVirtualDisplay(
                "TranslateScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                mainHandler,
            )

            mainHandler.postDelayed({
                if (!continuation.isActive) {
                    cleanup()
                    return@postDelayed
                }

                val image = imageReader.acquireLatestImage()
                if (image == null) {
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("截屏失败"))
                    }
                    return@postDelayed
                }

                try {
                    val bitmap = image.toBitmap()
                    if (continuation.isActive) {
                        continuation.resume(bitmap)
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                } finally {
                    image.close()
                    cleanup()
                }
            }, CAPTURE_FRAME_DELAY_MS)
        }
    }

    fun release() {
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun isMostlyBlank(bitmap: Bitmap): Boolean {
        val sampleSize = 12
        val stepX = (bitmap.width / sampleSize).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleSize).coerceAtLeast(1)
        var darkPixels = 0
        var totalPixels = 0

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (brightness < 16) darkPixels++
                totalPixels++
                x += stepX
            }
            y += stepY
        }

        return totalPixels > 0 && darkPixels.toFloat() / totalPixels > 0.95f
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    companion object {
        private const val CAPTURE_FRAME_DELAY_MS = 350L
        private const val CAPTURE_RETRY_DELAY_MS = 200L
        private const val MAX_CAPTURE_ATTEMPTS = 2
    }
}
