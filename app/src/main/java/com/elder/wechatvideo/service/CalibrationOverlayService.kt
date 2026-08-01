package com.elder.wechatvideo.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.elder.wechatvideo.ElderWeChatApp
import com.elder.wechatvideo.MainActivity
import com.elder.wechatvideo.R
import com.elder.wechatvideo.util.PositionConfig
import com.elder.wechatvideo.widget.TapMarkView

/**
 * 悬浮窗校准服务（点击标记模式 · 10步精确版）
 *
 * 校准流程交替使用两种悬浮层：
 * - 引导层（顶部栏）：不遮挡微信，用户按引导操作微信
 * - 标记层（全屏半透明）：用户直接点击微信按钮位置来标记坐标
 *
 * 10 步流程：
 * ① 引导：进入聊天页
 * ② 标记：点击「+」号按钮位置
 * ③ 引导：手动点「+」打开面板
 * ④ 标记：点击面板中「视频通话」图标位置
 * ⑤ 引导：手动点「视频通话」打开选择菜单
 * ⑥ 标记：点击菜单中「视频通话」选项位置
 * ⑦ 引导：返回微信首页
 * ⑧ 标记：点击「搜索」按钮位置
 * ⑨ 引导：打开搜索并输入联系人名字
 * ⑩ 标记：点击「搜索结果行」位置（C 部分校准兜底用）
 */
class CalibrationOverlayService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1002
    }

    private lateinit var windowManager: WindowManager
    private var guideBarView: LinearLayout? = null
    private var tapMarkView: TapMarkView? = null
    private var bottomBarView: LinearLayout? = null

    private lateinit var stepText: TextView
    private lateinit var instructionText: TextView
    private lateinit var primaryButton: TextView
    private lateinit var cancelButton: TextView

    private var pendingX: Float = 0f
    private var pendingY: Float = 0f
    private var hasMark: Boolean = false

    private var currentStep = CalStep.GUIDE_ENTER_CHAT
    private var screenWidth = 0
    private var screenHeight = 0

    private enum class CalStep {
        GUIDE_ENTER_CHAT,      // ① 引导：进入聊天页
        MARK_PLUS,             // ② 标记：「+」号按钮
        GUIDE_OPEN_PANEL,      // ③ 引导：手动点「+」打开面板
        MARK_VIDEO_CALL,       // ④ 标记：面板中「视频通话」图标
        GUIDE_OPEN_SUBMENU,    // ⑤ 引导：手动点「视频通话」打开选择菜单
        MARK_VIDEO_CONFIRM,    // ⑥ 标记：菜单中「视频通话」选项
        GUIDE_GO_HOME,         // ⑦ 引导：返回首页
        MARK_SEARCH,           // ⑧ 标记：「搜索」按钮
        GUIDE_SEARCH_RESULT,   // ⑨ 引导：打开搜索并点选联系人
        MARK_SEARCH_RESULT,    // ⑩ 标记：「搜索结果行」位置（C 部分校准兜底用）
        DONE
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        PositionConfig.saveScreenSize(this, screenWidth, screenHeight)

        createGuideBar()
        updateStepUI()
    }

    /* ===================== 引导栏（顶部，不遮挡） ===================== */

    private fun createGuideBar() {
        val density = resources.displayMetrics.density

        // 圆角卡片背景（v2：半透深色 + 16dp 圆角 + 1dp hair-line 描边）
        val cardBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.argb(209, 15, 14, 26)) // #0F0E1A @ 82%
            cornerRadius = 16 * density
            setStroke((1 * density).toInt(), Color.argb(18, 255, 255, 255)) // hair-line
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(
                (12 * density).toInt(), (8 * density).toInt(),
                (12 * density).toInt(), (8 * density).toInt()
            )
        }

        // 顶部行：步骤 badge + 标签
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badgeBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#9B8CFF"))
            cornerRadius = 99 * density
        }
        val badgeView = TextView(this).apply {
            background = badgeBg
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((10 * density).toInt(), (2 * density).toInt(), (10 * density).toInt(), (2 * density).toInt())
            tag = "badge"
        }
        stepText = badgeView
        topRow.addView(badgeView)

        val stepLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#A0A0BC"))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
            text = " 引导操作"
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * density).toInt() }
            layoutParams = lp
        }
        topRow.addView(stepLabel)
        container.addView(topRow)

        // 指令文字
        instructionText = TextView(this).apply {
            setTextColor(Color.parseColor("#E8E6F5"))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            setPadding(0, (6 * density).toInt(), 0, (8 * density).toInt())
            setLineSpacing(1f, 1.1f)
        }
        container.addView(instructionText)

        // 按钮行
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val primaryBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#9B8CFF"))
            cornerRadius = 99 * density
        }
        primaryButton = TextView(this).apply {
            background = primaryBg
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((14 * density).toInt(), (7 * density).toInt(), (14 * density).toInt(), (7 * density).toInt())
        }
        buttonRow.addView(primaryButton)

        val ghostBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 99 * density
            setStroke((1 * density).toInt(), Color.argb(38, 255, 255, 255)) // 15% white border
        }
        cancelButton = TextView(this).apply {
            text = "取消"
            background = ghostBg
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            setTextColor(Color.parseColor("#A0A0BC"))
            setPadding((14 * density).toInt(), (7 * density).toInt(), (14 * density).toInt(), (7 * density).toInt())
            setOnClickListener { cancelCalibration() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * density).toInt() }
            layoutParams = lp
        }
        buttonRow.addView(cancelButton)
        container.addView(buttonRow)

        // 窗口参数：屏幕居中浮动卡片，最小遮挡
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        guideBarView = container
        windowManager.addView(container, params)

        // 拖动支持（保留，万一需要微调位置）
        var initialY = 0
        var initialTouchY = 0f
        var isDragging = false
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dy) > 10) isDragging = true
                    if (isDragging) {
                        params.y = initialY + dy
                        windowManager.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    /* ===================== 全屏点击标记层 ===================== */

    private fun showTapMarkLayer(hint: String = "点击屏幕上按钮的位置") {
        if (tapMarkView != null) return

        val markView = TapMarkView(this)
        markView.onTap = { x, y ->
            pendingX = x
            pendingY = y
            hasMark = true
            updateMarkInstruction(x, y)
        }

        val markParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        tapMarkView = markView
        windowManager.addView(markView, markParams)

        guideBarView?.visibility = View.GONE
        createBottomBar(hint)
    }

    private fun updateMarkInstruction(x: Float, y: Float) {
        bottomBarView?.let { bar ->
            val tv = bar.findViewWithTag<TextView>("hint")
            tv?.text = "已标记: (${x.toInt()}, ${y.toInt()})\n请确认位置是否准确"
        }
    }

    private fun hideTapMarkLayer() {
        tapMarkView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        tapMarkView = null
        removeBottomBar()
        guideBarView?.visibility = View.VISIBLE
    }

    /* ===================== 底部操作栏 ===================== */

    private fun createBottomBar(hint: String) {
        if (bottomBarView != null) return
        val density = resources.displayMetrics.density

        // v2 圆角卡片背景
        val cardBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.argb(209, 15, 14, 26))
            cornerRadius = 16 * density
            setStroke((1 * density).toInt(), Color.argb(18, 255, 255, 255))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(
                (12 * density).toInt(), (8 * density).toInt(),
                (12 * density).toInt(), (8 * density).toInt()
            )
        }

        val hintTv = TextView(this).apply {
            setTextColor(Color.parseColor("#10B59A")) // v2 teal
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            text = hint
            tag = "hint"
        }
        container.addView(hintTv)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        val confirmBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#9B8CFF"))
            cornerRadius = 99 * density
        }
        val confirmBtn = TextView(this).apply {
            text = "确认位置"
            background = confirmBg
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((14 * density).toInt(), (7 * density).toInt(), (14 * density).toInt(), (7 * density).toInt())
            setOnClickListener {
                if (!hasMark) {
                    Toast.makeText(this@CalibrationOverlayService,
                        "请先点击按钮位置", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                confirmMark()
            }
        }
        buttonRow.addView(confirmBtn)

        val retryBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 99 * density
            setStroke((1 * density).toInt(), Color.argb(38, 255, 255, 255))
        }
        val retryBtn = TextView(this).apply {
            text = "重新点选"
            background = retryBg
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            setTextColor(Color.parseColor("#A0A0BC"))
            setPadding((14 * density).toInt(), (7 * density).toInt(), (14 * density).toInt(), (7 * density).toInt())
            setOnClickListener {
                hasMark = false
                tapMarkView?.clearMark()
                hintTv.text = "点击屏幕上按钮的位置"
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * density).toInt() }
            layoutParams = lp
        }
        buttonRow.addView(retryBtn)

        container.addView(buttonRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        bottomBarView = container
        windowManager.addView(container, params)

        // 拖动支持：标记模式下可能需要挪开卡片以点击被遮挡的区域
        var initialY = 0
        var initialTouchY = 0f
        var isDragging = false
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dy) > 10) isDragging = true
                    if (isDragging) {
                        params.y = initialY + dy
                        windowManager.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun removeBottomBar() {
        bottomBarView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        bottomBarView = null
    }

    /* ===================== 步骤管理（8步，每步明确提示） ===================== */

    private fun updateStepUI() {
        when (currentStep) {
            // ① 引导：进入聊天页
            CalStep.GUIDE_ENTER_CHAT -> {
                hideTapMarkLayer()
                stepText.text = "1/10"
                instructionText.text = "请在微信中进入任意一个聊天对话\n（点击任意联系人进入聊天页面）"
                primaryButton.text = "我已进入聊天"
                primaryButton.setOnClickListener { goToStep(CalStep.MARK_PLUS) }
                cancelButton.visibility = View.VISIBLE
            }
            // ② 标记：「+」号按钮
            CalStep.MARK_PLUS -> {
                stepText.text = "2/10"
                instructionText.text = "请点击屏幕上「+」号按钮的位置\n（聊天页右下角的加号按钮）"
                primaryButton.text = "（使用底部按钮确认）"
                primaryButton.setOnClickListener {}
                cancelButton.visibility = View.GONE
                hasMark = false
                showTapMarkLayer("请点击「+」号按钮的位置（聊天页右下角加号）")
            }
            // ③ 引导：手动点「+」打开面板
            CalStep.GUIDE_OPEN_PANEL -> {
                hideTapMarkLayer()
                stepText.text = "3/10"
                instructionText.text = "请手动点击聊天页的「+」号按钮\n打开功能面板（显示视频通话、图片等图标）"
                primaryButton.text = "面板已打开"
                primaryButton.setOnClickListener { goToStep(CalStep.MARK_VIDEO_CALL) }
                cancelButton.visibility = View.VISIBLE
            }
            // ④ 标记：面板中「视频通话」图标
            CalStep.MARK_VIDEO_CALL -> {
                stepText.text = "4/10"
                instructionText.text = "请点击功能面板中「视频通话」图标的位置\n（面板里的摄像机图标）"
                primaryButton.text = "（使用底部按钮确认）"
                primaryButton.setOnClickListener {}
                cancelButton.visibility = View.GONE
                hasMark = false
                showTapMarkLayer("请点击功能面板中「视频通话」图标的位置（摄像机图标）")
            }
            // ⑤ 引导：手动点「视频通话」打开选择菜单
            CalStep.GUIDE_OPEN_SUBMENU -> {
                hideTapMarkLayer()
                stepText.text = "5/10"
                instructionText.text = "请手动点击「视频通话」图标\n打开选择菜单（显示「视频通话」和「语音通话」两个选项）"
                primaryButton.text = "菜单已出现"
                primaryButton.setOnClickListener { goToStep(CalStep.MARK_VIDEO_CONFIRM) }
                cancelButton.visibility = View.VISIBLE
            }
            // ⑥ 标记：菜单中「视频通话」选项
            CalStep.MARK_VIDEO_CONFIRM -> {
                stepText.text = "6/10"
                instructionText.text = "请点击菜单中「视频通话」选项的位置\n（弹出菜单里左边的视频通话选项）"
                primaryButton.text = "（使用底部按钮确认）"
                primaryButton.setOnClickListener {}
                cancelButton.visibility = View.GONE
                hasMark = false
                showTapMarkLayer("请点击弹出菜单中「视频通话」选项的位置（不是语音通话）")
            }
            // ⑦ 引导：返回首页
            CalStep.GUIDE_GO_HOME -> {
                hideTapMarkLayer()
                stepText.text = "7/10"
                instructionText.text = "请返回微信首页\n（按返回键退出聊天，回到有「微信」「通讯录」标签的页面）"
                primaryButton.text = "我已回到首页"
                primaryButton.setOnClickListener { goToStep(CalStep.MARK_SEARCH) }
                cancelButton.visibility = View.VISIBLE
            }
            // ⑧ 标记：「搜索」按钮
            CalStep.MARK_SEARCH -> {
                stepText.text = "8/10"
                instructionText.text = "请点击屏幕顶部「搜索」按钮的位置\n（首页顶部放大镜图标或搜索栏）"
                primaryButton.text = "（使用底部按钮确认）"
                primaryButton.setOnClickListener {}
                cancelButton.visibility = View.GONE
                hasMark = false
                showTapMarkLayer("请点击屏幕顶部「搜索」按钮的位置（放大镜图标）")
            }
            // ⑨ 引导：打开搜索并点选联系人
            CalStep.GUIDE_SEARCH_RESULT -> {
                hideTapMarkLayer()
                stepText.text = "9/10"
                instructionText.text = "请在微信首页点击「搜索」，输入一个联系人名字，\n在搜索结果中让【联系人】列表显示出来（不要点开公众号）"
                primaryButton.text = "结果已出现"
                primaryButton.setOnClickListener { goToStep(CalStep.MARK_SEARCH_RESULT) }
                cancelButton.visibility = View.VISIBLE
            }
            // ⑩ 标记：「搜索结果行」位置（C 部分校准兜底用）
            CalStep.MARK_SEARCH_RESULT -> {
                stepText.text = "10/10"
                instructionText.text = "请点击屏幕上【联系人结果行】的位置\n（搜索结果列表中任意一行的位置，不是公众号）"
                primaryButton.text = "（使用底部按钮确认）"
                primaryButton.setOnClickListener {}
                cancelButton.visibility = View.GONE
                hasMark = false
                showTapMarkLayer("请点击搜索结果中【联系人行】的位置（不是公众号）")
            }
            // 完成
            CalStep.DONE -> {
                hideTapMarkLayer()
                stepText.text = ""
                showCalibrationSummary()
                primaryButton.text = "完成，返回应用"
                primaryButton.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#9B8CFF"))
                    cornerRadius = 99 * resources.displayMetrics.density
                }
                primaryButton.setOnClickListener { finishCalibration() }
                cancelButton.visibility = View.GONE
            }
        }
    }

    private fun showCalibrationSummary() {
        val search = PositionConfig.getSearchButton(this)
        val plus = PositionConfig.getPlusButton(this)
        val video = PositionConfig.getVideoCallButton(this)
        val videoConfirm = PositionConfig.getVideoConfirmButton(this)

        instructionText.text = buildString {
            appendLine("校准完成！")
            appendLine()
            appendLine("已保存的按键坐标：")
            if (plus != null) appendLine("  + 号按钮: (${plus.x.toInt()}, ${plus.y.toInt()})")
            if (video != null) appendLine("  视频通话图标: (${video.x.toInt()}, ${video.y.toInt()})")
            if (videoConfirm != null) appendLine("  视频通话选项: (${videoConfirm.x.toInt()}, ${videoConfirm.y.toInt()})")
            if (search != null) appendLine("  搜索按钮: (${search.x.toInt()}, ${search.y.toInt()})")
            val searchResult = PositionConfig.getSearchResultCoord(this@CalibrationOverlayService)
            if (searchResult != null) appendLine("  搜索结果行: (${searchResult.x.toInt()}, ${searchResult.y.toInt()})")
            appendLine()
            append("以后拨号将自动使用以上坐标。")
        }
    }

    private fun goToStep(step: CalStep) {
        currentStep = step
        primaryButton.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#9B8CFF"))
            cornerRadius = 99 * resources.displayMetrics.density
        }
        updateStepUI()
    }

    /* ===================== 保存坐标 ===================== */

    private fun confirmMark() {
        when (currentStep) {
            CalStep.MARK_PLUS -> {
                PositionConfig.savePlusButton(this, pendingX, pendingY)
                Toast.makeText(this, "已保存 + 号按钮位置: (${pendingX.toInt()}, ${pendingY.toInt()})", Toast.LENGTH_SHORT).show()
                goToStep(CalStep.GUIDE_OPEN_PANEL)
            }
            CalStep.MARK_VIDEO_CALL -> {
                PositionConfig.saveVideoCallButton(this, pendingX, pendingY)
                Toast.makeText(this, "已保存视频通话图标位置: (${pendingX.toInt()}, ${pendingY.toInt()})", Toast.LENGTH_SHORT).show()
                goToStep(CalStep.GUIDE_OPEN_SUBMENU)
            }
            CalStep.MARK_VIDEO_CONFIRM -> {
                PositionConfig.saveVideoConfirmButton(this, pendingX, pendingY)
                Toast.makeText(this, "已保存视频通话选项位置: (${pendingX.toInt()}, ${pendingY.toInt()})", Toast.LENGTH_SHORT).show()
                goToStep(CalStep.GUIDE_GO_HOME)
            }
            CalStep.MARK_SEARCH -> {
                PositionConfig.saveSearchButton(this, pendingX, pendingY)
                Toast.makeText(this, "已保存搜索按钮位置: (${pendingX.toInt()}, ${pendingY.toInt()})", Toast.LENGTH_SHORT).show()
                goToStep(CalStep.GUIDE_SEARCH_RESULT)
            }
            CalStep.MARK_SEARCH_RESULT -> {
                PositionConfig.saveSearchResultCoord(this, pendingX, pendingY)
                Toast.makeText(this, "已保存搜索结果行位置: (${pendingX.toInt()}, ${pendingY.toInt()})", Toast.LENGTH_SHORT).show()
                goToStep(CalStep.DONE)
            }
            else -> {}
        }
    }

    /* ===================== 结束 ===================== */

    private fun finishCalibration() {
        PositionConfig.setCalibrationDone(this)
        // 记录校准时的设备显示参数（分辨率/DPI/字体缩放），显示设置变化后可提示重新校准
        PositionConfig.saveDeviceParams(this)
        // 记录校准时的微信版本，升级后可提示重新校准
        com.elder.wechatvideo.util.WeChatVersionDetector.saveCalibratedVersion(this)
        returnToApp()
    }

    private fun cancelCalibration() {
        Toast.makeText(this, "校准已取消", Toast.LENGTH_SHORT).show()
        returnToApp()
    }

    private fun returnToApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        stopForeground(true)
        stopSelf()
    }

    /* ===================== 前台通知 ===================== */

    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, ElderWeChatApp.CHANNEL_ID)
            .setContentTitle("正在校准按键位置")
            .setContentText("校准完成后此通知自动消失")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideTapMarkLayer()
        guideBarView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        guideBarView = null
    }
}
