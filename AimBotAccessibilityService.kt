package com.gblas.aimbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class AimBotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AimBotAccessibilityService? = null

        fun aimAndFire(targetX: Float, targetY: Float, screenW: Int, screenH: Int) {
            instance?.performAimAndFire(targetX, targetY, screenW, screenH)
        }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun performAimAndFire(targetX: Float, targetY: Float, screenW: Int, screenH: Int) {
        // Joystick de mira — lado direito (Free Fire modo padrão)
        val aimJoyX = screenW * 0.75f
        val aimJoyY = screenH * 0.72f

        // Botão de atirar
        val fireX = screenW * 0.88f
        val fireY = screenH * 0.62f

        // Delta escalado: desvio do inimigo relativo ao centro da tela
        val sensitivity = 0.04f
        val dx = (targetX - screenW / 2f) * sensitivity
        val dy = (targetY - screenH / 2f) * sensitivity

        // Gesto de mira: swipe no joystick
        val aimPath = Path().apply {
            moveTo(aimJoyX, aimJoyY)
            lineTo(aimJoyX + dx, aimJoyY + dy)
        }
        val aimGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(aimPath, 0L, 80L))
            .build()

        // Gesto de tiro: tap
        val firePath = Path().apply { moveTo(fireX, fireY) }
        val fireGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(firePath, 0L, 40L))
            .build()

        dispatchGesture(aimGesture, null, null)
        Handler(Looper.getMainLooper()).postDelayed({
            dispatchGesture(fireGesture, null, null)
        }, 100L)
    }
}
