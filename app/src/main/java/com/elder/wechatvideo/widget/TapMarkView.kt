package com.elder.wechatvideo.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * 全屏点击标记视图（精确版）
 *
 * 校准时覆盖在微信界面上方，用户直接点击屏幕上看到的按钮位置。
 *
 * 关键改进：
 * 1. 使用 rawX/rawY 屏幕绝对坐标传给回调，避免状态栏偏移导致精度问题
 * 2. 遮罩透明度降低（60/255），确保用户能清晰看到微信 UI
 * 3. 标记点显示坐标数值，方便用户确认精确位置
 * 4. 中心点更小更精确，配合十字线定位
 */
class TapMarkView(context: Context) : View(context) {

    /** 用户点击的坐标回调（屏幕绝对坐标） */
    var onTap: ((screenX: Float, screenY: Float) -> Unit)? = null

    /** 已标记的位置（view 相对坐标，用于绘制） */
    private var markedX: Float? = null
    private var markedY: Float? = null

    /** 已标记的屏幕绝对坐标（用于显示） */
    private var screenX: Float = 0f
    private var screenY: Float = 0f

    // 遮罩画笔（降低透明度，让用户看清微信 UI）
    private val dimPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)  // 从 120 降到 60
        style = Paint.Style.FILL
    }

    // 挖洞画笔（在标记处挖透明区域，让用户看到下面的按钮）
    private val clearPaint = Paint().apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // 外圈画笔（大范围指示）
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 76, 175, 80)  // 半透明绿色
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    // 标记圆环画笔（精确指示）
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")  // 绿色
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    // 中心点画笔
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")  // 红色中心点
        style = Paint.Style.FILL
    }

    // 十字线画笔
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    // 坐标文字画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics
        )
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    // 坐标文字背景画笔
    private val textBgPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.FILL
    }

    /** 外圈半径（大范围触摸指示） */
    private val outerRadius = 120f

    /** 标记圆环半径（精确指示） */
    private val markerRadius = 45f

    /** 标记孔透明半径 */
    private val holeRadius = 50f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun clearMark() {
        markedX = null
        markedY = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // 使用 rawX/rawY 作为屏幕绝对坐标（传给回调用于 dispatchGesture）
            val rawX = event.rawX
            val rawY = event.rawY

            // 使用 x/y 作为 view 相对坐标（用于绘制标记）
            markedX = event.x
            markedY = event.y
            screenX = rawX
            screenY = rawY

            onTap?.invoke(rawX, rawY)
            invalidate()
            performClick()
            return true
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. 绘制半透明遮罩（低透明度，不遮挡微信 UI）
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        val mx = markedX
        val my = markedY

        if (mx != null && my != null) {
            // 2. 在标记处挖一个透明洞
            canvas.drawCircle(mx, my, holeRadius, clearPaint)

            // 3. 绘制外圈（大范围触摸区域指示）
            canvas.drawCircle(mx, my, outerRadius, outerRingPaint)

            // 4. 绘制精确标记圆环
            canvas.drawCircle(mx, my, markerRadius, ringPaint)

            // 5. 绘制十字线（精确定位）
            val crossLen = 25f
            canvas.drawLine(mx - crossLen, my, mx + crossLen, my, crossPaint)
            canvas.drawLine(mx, my - crossLen, mx, my + crossLen, crossPaint)

            // 6. 绘制红色中心点（点击的精确位置）
            canvas.drawCircle(mx, my, 4f, dotPaint)

            // 7. 显示坐标文字（方便调试和确认精度）
            val coordText = "(${screenX.toInt()}, ${screenY.toInt()})"
            val textWidth = textPaint.measureText(coordText)
            val textHeight = textPaint.textSize

            // 文字背景
            val textX = mx + 30f
            val textY = my - 30f
            val bgRect = Rect(
                (textX - 4).toInt(),
                (textY - textHeight).toInt(),
                (textX + textWidth + 4).toInt(),
                (textY + 4).toInt()
            )

            // 确保文字不超出屏幕
            val adjustedX = if (bgRect.right > width) {
                mx - 30f - textWidth - 8f
            } else {
                textX
            }
            val adjustedBgRect = Rect(
                (adjustedX - 4).toInt(),
                (textY - textHeight).toInt(),
                (adjustedX + textWidth + 4).toInt(),
                (textY + 4).toInt()
            )

            canvas.drawRect(adjustedBgRect, textBgPaint)
            canvas.drawText(coordText, adjustedX, textY - 2f, textPaint)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
