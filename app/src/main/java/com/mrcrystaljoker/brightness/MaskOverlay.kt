package com.mrcrystaljoker.brightness

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 极暗档遮罩：一层全屏纯黑、不可触摸的悬浮窗。
 *
 * 背光到硬件下限（约 0.0102）之后，再往下只能靠压暗画面实现，
 * 这正是 EhViewer 在 0~100 档做的事情。
 */
class MaskOverlay(private val context: Context) {

    private val windowManager: WindowManager
        get() = context.getSystemService(WindowManager::class.java)

    private var maskView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun update(alpha: Float) {
        if (!canDraw()) return

        val a = alpha.coerceIn(0f, 0.9f)
        if (a <= 0.002f) {
            hide()
            return
        }

        val params = layoutParams ?: createParams(a).also { layoutParams = it }

        val view = maskView ?: View(context).apply {
            setBackgroundColor(Color.BLACK)
        }.also { v ->
            maskView = v
            runCatching { windowManager.addView(v, params) }
        }

        params.alpha = a
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    fun hide() {
        val v = maskView ?: return
        runCatching { windowManager.removeView(v) }
        maskView = null
        layoutParams = null
    }

    private fun createParams(alpha: Float) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.alpha = alpha
    }
}
