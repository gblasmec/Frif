package com.gblas.aimbot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics

class AimBotService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var screenW = 1080
    private var screenH = 1920
    private var density = 320

    private var lastFireTime = 0L
    private val fireCooldownMs = 200L

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())

        val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        density = metrics.densityDpi

        captureThread = HandlerThread("AimCaptureThread").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)
        setupCapture()
        return START_STICKY
    }

    private fun setupCapture() {
        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AimCapture", screenW, screenH, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, captureHandler
        )
        captureHandler?.post(captureLoop)
    }

    private val captureLoop = object : Runnable {
        override fun run() {
            processFrame()
            captureHandler?.postDelayed(this, 33L) // ~30fps
        }
    }

    private fun processFrame() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * screenW
            val bmp = Bitmap.createBitmap(
                screenW + rowPadding / plane.pixelStride,
                screenH, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(plane.buffer)

            val target = detectEnemy(bmp)
            bmp.recycle()

            val now = System.currentTimeMillis()
            if (target != null && now - lastFireTime > fireCooldownMs) {
                lastFireTime = now
                AimBotAccessibilityService.aimAndFire(target.first, target.second, screenW, screenH)
            }
        } finally {
            image.close()
        }
    }

    /**
     * Varre a tela em blocos 16x16, encontra o cluster mais denso de pixels
     * vermelhos (barra de HP inimigo). Retorna o centro do bloco ajustado
     * para cima (~cabeça do inimigo). Varre apenas os 60% superiores da tela.
     */
    private fun detectEnemy(bmp: Bitmap): Pair<Float, Float>? {
        val maxY = (bmp.height * 0.60).toInt()
        val blockSize = 16
        val step = 3

        var bestScore = 0
        var bestBX = -1
        var bestBY = -1

        for (by in 0 until maxY / blockSize) {
            for (bx in 0 until bmp.width / blockSize) {
                var score = 0
                val x0 = bx * blockSize
                val y0 = by * blockSize
                val x1 = minOf(x0 + blockSize, bmp.width)
                val y1 = minOf(y0 + blockSize, maxY)

                var y = y0
                while (y < y1) {
                    var x = x0
                    while (x < x1) {
                        if (isEnemyRed(bmp.getPixel(x, y))) score++
                        x += step
                    }
                    y += step
                }

                if (score > bestScore) {
                    bestScore = score
                    bestBX = bx
                    bestBY = by
                }
            }
        }

        if (bestScore < 4 || bestBX < 0) return null

        val cx = (bestBX * blockSize + blockSize / 2).toFloat()
        val cy = (bestBY * blockSize - blockSize * 3).toFloat()

        return if (cy > 0f) Pair(cx, cy) else null
    }

    /**
     * Barra de HP inimigo: vermelho saturado.
     * r > 175, g < 55, b < 55 — filtra laranja/fogo e elementos de UI.
     */
    private fun isEnemyRed(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8)  and 0xFF
        val b =  pixel         and 0xFF
        return r > 175 && g < 55 && b < 55 && r > g * 3
    }

    private fun buildNotification(): Notification {
        val ch = NotificationChannel("aimbot", "AimBot", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        return Notification.Builder(this, "aimbot")
            .setContentTitle("Gblas AimBot — ativo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        captureHandler?.removeCallbacks(captureLoop)
        captureThread?.quitSafely()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
    }
}
