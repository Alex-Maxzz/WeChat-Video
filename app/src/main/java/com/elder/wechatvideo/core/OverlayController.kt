package com.elder.wechatvideo.core

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 拨号进度悬浮窗控制器（v2 双窗口胶囊条）。
 *
 * 窗口 A（显示层）：胶囊条视觉（图标+文字+badge），FLAG_NOT_TOUCHABLE 完全穿透。
 * 窗口 B（按钮层）：取消按钮（64×36dp），单击取消，长按拖动整体，位置记忆。
 */
class OverlayController(private val context: Context) {

    companion object {
        private const val TAG = "OverlayController"
        private const val PREFS_NAME = "overlay_prefs"
        private const val KEY_Y = "overlay_y"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val screenWidth = context.resources.displayMetrics.widthPixels

    private var displayView: LinearLayout? = null
    private var displayParams: WindowManager.LayoutParams? = null
    private var iconView: TextView? = null
    private var progressText: TextView? = null
    private var badgeView: TextView? = null

    private var buttonView: TextView? = null
    private var buttonParams: WindowManager.LayoutParams? = null

    var onCancelClick: (() -> Unit)? = null

    fun show(message: String) {
        handler.post {
            ensureCreated()
            updateState(message)
            displayView?.visibility = View.VISIBLE
            buttonView?.visibility = View.VISIBLE
        }
    }

    fun hide() {
        handler.post {
            displayView?.visibility = View.GONE
            buttonView?.visibility = View.GONE
        }
    }

    fun destroy() {
        onCancelClick = null
        handler.removeCallbacksAndMessages(null)
        handler.post {
            displayView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
            buttonView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
            displayView = null
            buttonView = null
            iconView = null
            progressText = null
            badgeView = null
        }
    }

    private fun updateState(message: String) {
        val icon = iconView ?: return
        val text = progressText ?: return
        val badge = badgeView ?: return

        when {
            message.startsWith("✓") -> {
                icon.text = "✓"
                icon.setTextColor(Color.parseColor("#10B59A"))
                (icon.background as? GradientDrawable)?.setColor(Color.argb(51, 16, 181, 154))
                text.text = message.removePrefix("✓").trim()
                text.setTextColor(Color.parseColor("#5FE3C9"))
                badge.visibility = View.GONE
            }
            message.startsWith("✗") -> {
                icon.text = "✗"
                icon.setTextColor(Color.parseColor("#FFB4A2"))
                (icon.background as? GradientDrawable)?.setColor(Color.argb(51, 255, 180, 162))
                text.text = message.removePrefix("✗").trim()
                text.setTextColor(Color.parseColor("#FFB4A2"))
                badge.visibility = View.GONE
            }
            else -> {
                icon.text = "●"
                icon.setTextColor(Color.parseColor("#9B8CFF"))
                (icon.background as? GradientDrawable)?.setColor(Color.argb(51, 155, 140, 255))
                text.text = message
                text.setTextColor(Color.parseColor("#E8E6F5"))
                val stepMatch = Regex("([①②③④⑤⑥⑦⑧⑨⑩])").find(message)
                if (stepMatch != null) {
                    val stepNum = "①②③④⑤⑥⑦⑧⑨⑩".indexOf(stepMatch.value) + 1
                    badge.text = "$stepNum/6"
                    badge.visibility = View.VISIBLE
                    text.text = message.replace(Regex("[①②③④⑤⑥⑦⑧⑨⑩]\\s*"), "")
                } else {
                    badge.visibility = View.GONE
                }
            }
        }
    }

    private fun ensureCreated() {
        if (displayView != null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedY = prefs.getInt(KEY_Y, 0)
        createDisplayWindow(savedY)
        createButtonWindow(savedY, prefs)
    }

    /** 窗口 A：显示层（FLAG_NOT_TOUCHABLE，完全穿透） */
    private fun createDisplayWindow(savedY: Int) {
        val pillBg = GradientDrawable().apply {
            setColor(Color.argb(224, 15, 14, 26))
            cornerRadius = 99 * density
            setStroke((1 * density).toInt(), Color.argb(20, 255, 255, 255))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pillBg
            setPadding(
                (16 * density).toInt(), (10 * density).toInt(),
                (80 * density).toInt(), (10 * density).toInt()
            )
        }

        val iconBg = GradientDrawable().apply {
            setColor(Color.argb(51, 155, 140, 255))
            cornerRadius = 99 * density
        }
        iconView = TextView(context).apply {
            text = "●"
            background = iconBg
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9B8CFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val size = (22 * density).toInt()
            minWidth = size
            minHeight = size
        }
        container.addView(iconView, LinearLayout.LayoutParams(
            (22 * density).toInt(), (22 * density).toInt()
        ))

        progressText = TextView(context).apply {
            setTextColor(Color.parseColor("#E8E6F5"))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            val lp = LinearLayout.LayoutParams(
                (screenWidth * 0.42f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (10 * density).toInt() }
            layoutParams = lp
        }
        container.addView(progressText)

        val badgeBg = GradientDrawable().apply {
            setColor(Color.argb(31, 155, 140, 255))
            cornerRadius = 99 * density
        }
        badgeView = TextView(context).apply {
            background = badgeBg
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9B8CFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (6 * density).toInt() }
            layoutParams = lp
        }
        container.addView(badgeView)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            y = savedY
        }
        displayParams = lp
        displayView = container
        try { windowManager.addView(container, lp) } catch (e: Exception) { Log.w(TAG, "display addView failed", e) }
    }

    /** 窗口 B：取消按钮（单击取消，长按/拖动移动整体） */
    private fun createButtonWindow(savedY: Int, prefs: android.content.SharedPreferences) {
        val cancelBg = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#EF4444"), Color.parseColor("#DC2626"))
        ).apply {
            cornerRadius = 99 * density
        }

        val btn = TextView(context).apply {
            text = "取消"
            background = cancelBg
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        buttonView = btn

        val btnW = (64 * density).toInt()
        val btnH = (48 * density).toInt()
        val btnOffsetX = (screenWidth * 0.30f).toInt()

        val lp = WindowManager.LayoutParams(
            btnW, btnH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            y = savedY
            x = btnOffsetX
        }
        buttonParams = lp
        try { windowManager.addView(btn, lp) } catch (e: Exception) { Log.w(TAG, "button addView failed", e) }

        // 交互：单击取消（固定位置，不可拖动）
        btn.setOnClickListener { onCancelClick?.invoke() }
    }
}
