package com.elder.wechatvideo.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * 十字准星视图
 *
 * 用于手动校准：用户拖动十字准星到微信按键位置，松手后点击"确认"保存坐标。
 * 视图本身只负责绘制和接收拖拽手势，实际的窗口移动由
 * [com.elder.wechatvideo.service.CalibrationOverlayService] 通过
 * WindowManager.updateViewLayout 完成。
 */
class CrosshairView(context: Context) : View(context) {

    /** 拖拽回调，参数为本次移动的增量（像素） */
    var onDrag: ((dx: Float, dy: Float) -> Unit)? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 0, 0)
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    /** 十字准星视图的边长（像素） */
    private val viewSize = 240

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewSize, viewSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        // 横竖线
        canvas.drawLine(0f, cy, width.toFloat(), cy, linePaint)
        canvas.drawLine(cx, 0f, cx, height.toFloat(), linePaint)

        // 半透明圆 + 圆环
        canvas.drawCircle(cx, cy, 70f, fillPaint)
        canvas.drawCircle(cx, cy, 70f, circlePaint)

        // 中心点
        canvas.drawCircle(cx, cy, 6f, dotPaint)
    }

    /* ===================== 拖拽处理 ===================== */

    private var lastRawX = 0f
    private var lastRawY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                onDrag?.invoke(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return false
    }
}
