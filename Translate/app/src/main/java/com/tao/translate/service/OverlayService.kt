package com.tao.translate.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tao.translate.MainActivity
import com.tao.translate.R
import com.tao.translate.capture.ScreenCaptureManager
import com.tao.translate.capture.TextRecognitionHelper
import com.tao.translate.data.AppTextRepository
import com.tao.translate.ui.overlay.FloatingBallContent
import com.tao.translate.ui.overlay.OverlayPanelContent
import com.tao.translate.ui.theme.TranslateTheme
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var savedStateRegistryController: SavedStateRegistryController

    private var floatingBallView: ComposeView? = null
    private var floatingBallLayoutParams: WindowManager.LayoutParams? = null

    private var panelView: ComposeView? = null
    private var panelLayoutParams: WindowManager.LayoutParams? = null

    private var screenCaptureManager: ScreenCaptureManager? = null
    private val textRecognitionHelper = TextRecognitionHelper()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var captureJob: Job? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var ballSizePx = 0
    private var touchSlop = 0

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this)
        savedStateRegistryController = SavedStateRegistryController.create(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        ballSizePx = (FLOATING_BALL_SIZE_DP * metrics.density).toInt()
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        showFloatingBall()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_PANEL -> showPanel()
            ACTION_HIDE_PANEL -> hidePanel()
            else -> initProjectionIfNeeded(intent)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        captureJob?.cancel()
        hidePanel()
        removeFloatingBall()
        screenCaptureManager?.release()
        textRecognitionHelper.close()
        serviceScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun initProjectionIfNeeded(intent: Intent?) {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val data = readProjectionData(intent)
        if (resultCode == Int.MIN_VALUE || data == null) return

        screenCaptureManager?.release()
        screenCaptureManager = ScreenCaptureManager(this).also {
            it.init(resultCode, data)
        }
    }

    private fun readProjectionData(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun overlayLayoutParams(
        width: Int,
        height: Int,
        gravity: Int,
        x: Int = 0,
        y: Int = 0,
        focusable: Boolean = false,
    ): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        return WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    private fun showFloatingBall() {
        if (floatingBallView != null) return

        val params = overlayLayoutParams(
            width = ballSizePx,
            height = ballSizePx,
            gravity = Gravity.TOP or Gravity.START,
            x = 0,
            y = screenHeight / 3,
        )

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                TranslateTheme {
                    FloatingBallContent()
                }
            }
            setOnTouchListener(FloatingBallTouchListener(params, this))
        }

        windowManager.addView(composeView, params)
        floatingBallView = composeView
        floatingBallLayoutParams = params
    }

    private fun removeFloatingBall() {
        floatingBallView?.let { windowManager.removeView(it) }
        floatingBallView = null
        floatingBallLayoutParams = null
    }

    private fun hideFloatingBall() {
        floatingBallView?.visibility = View.GONE
    }

    private fun revealFloatingBall() {
        floatingBallView?.visibility = View.VISIBLE
    }

    private fun showPanel() {
        if (panelView != null) return
        performCapture(showPanelOnComplete = true)
    }

    private fun attachPanel() {
        if (panelView != null) return

        val panelHeight = screenHeight / 2
        val params = overlayLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = panelHeight,
            gravity = Gravity.BOTTOM or Gravity.START,
            focusable = true,
        )

        val composeView = ComposeView(this)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                TranslateTheme {
                    val capturedText by AppTextRepository.capturedText.collectAsState()
                    val isRecognizing by AppTextRepository.isRecognizing.collectAsState()
                    val recognitionHint by AppTextRepository.recognitionHint.collectAsState()
                    OverlayPanelContent(
                        capturedText = capturedText,
                        isRecognizing = isRecognizing,
                        recognitionHint = recognitionHint,
                        onRefresh = { refreshCapturedText() },
                        onClose = { hidePanel() },
                        onDragStart = { normalizePanelPosition(params) },
                        onDrag = { dx, dy -> movePanel(composeView, params, dx, dy) },
                    )
                }
            }
        }

        windowManager.addView(composeView, params)
        panelView = composeView
        panelLayoutParams = params
        AppTextRepository.setOverlayVisible(true)
    }

    private fun hidePanel() {
        captureJob?.cancel()
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        panelLayoutParams = null
        AppTextRepository.setOverlayVisible(false)
        AppTextRepository.setRecognizing(false)
        revealFloatingBall()
    }

    private fun refreshCapturedText() {
        performCapture(panelAlreadyVisible = true)
    }

    private fun performCapture(
        showPanelOnComplete: Boolean = false,
        panelAlreadyVisible: Boolean = false,
    ) {
        if (captureJob?.isActive == true) return

        captureJob = serviceScope.launch {
            AppTextRepository.setRecognizing(true)
            AppTextRepository.setRecognitionHint(null)
            hideFloatingBall()
            if (panelAlreadyVisible) {
                panelView?.visibility = View.GONE
            }
            delay(CAPTURE_DELAY_MS)

            try {
                val text = captureTextFromScreen()
                AppTextRepository.updateCapturedText(text)
                if (text.isBlank()) {
                    AppTextRepository.setRecognitionHint("未识别到文字，请确认目标内容清晰可见后点击刷新")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppTextRepository.updateCapturedText("")
                AppTextRepository.setRecognitionHint(error.message ?: "识别失败，请重试")
            } finally {
                AppTextRepository.setRecognizing(false)
                when {
                    showPanelOnComplete -> attachPanel()
                    panelAlreadyVisible -> panelView?.visibility = View.VISIBLE
                    else -> revealFloatingBall()
                }
            }
        }
    }

    private suspend fun captureTextFromScreen(): String {
        val manager = screenCaptureManager
        if (manager == null || !manager.isReady()) {
            throw IllegalStateException("录屏权限未就绪")
        }
        val bitmap = withContext(Dispatchers.IO) { manager.captureScreen() }
        return try {
            withContext(Dispatchers.IO) { textRecognitionHelper.recognize(bitmap) }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun normalizePanelPosition(params: WindowManager.LayoutParams) {
        if (params.gravity and Gravity.BOTTOM == Gravity.BOTTOM) {
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = screenHeight - params.height
        }
    }

    private fun movePanel(
        view: View,
        params: WindowManager.LayoutParams,
        dx: Float,
        dy: Float,
    ) {
        params.x = (params.x + dx).toInt()
            .coerceIn(0, screenWidth - view.width.coerceAtLeast(1))
        params.y = (params.y + dy).toInt()
            .coerceIn(0, screenHeight - view.height.coerceAtLeast(1))
        windowManager.updateViewLayout(view, params)
    }

    private fun snapBallToEdge(params: WindowManager.LayoutParams, view: View) {
        val centerX = params.x + view.width / 2
        params.x = if (centerX < screenWidth / 2) {
            0
        } else {
            screenWidth - view.width
        }
        params.y = params.y.coerceIn(0, screenHeight - view.height)
        windowManager.updateViewLayout(view, params)
    }

    private inner class FloatingBallTouchListener(
        private val params: WindowManager.LayoutParams,
        private val view: View,
    ) : View.OnTouchListener {

        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = (initialX + dx).toInt()
                            .coerceIn(0, screenWidth - view.width)
                        params.y = (initialY + dy).toInt()
                            .coerceIn(0, screenHeight - view.height)
                        windowManager.updateViewLayout(view, params)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        showPanel()
                    } else {
                        snapBallToEdge(params, view)
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        snapBallToEdge(params, view)
                    }
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 1001
        private const val FLOATING_BALL_SIZE_DP = 52f
        private const val CAPTURE_DELAY_MS = 300L

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        const val ACTION_STOP = "com.tao.translate.action.STOP_OVERLAY"
        const val ACTION_SHOW_PANEL = "com.tao.translate.action.SHOW_PANEL"
        const val ACTION_HIDE_PANEL = "com.tao.translate.action.HIDE_PANEL"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.overlay_notification_channel),
                    android.app.NotificationManager.IMPORTANCE_LOW,
                )
                val manager = context.getSystemService(android.app.NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }
    }
}
