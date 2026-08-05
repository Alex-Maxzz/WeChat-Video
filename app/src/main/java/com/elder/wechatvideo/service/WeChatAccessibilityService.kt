package com.elder.wechatvideo.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.elder.wechatvideo.core.CallStateMachine
import com.elder.wechatvideo.core.CallStateMachine.Companion.LANDING_POLL_INTERVAL
import com.elder.wechatvideo.core.CallStateMachine.Companion.MAX_LANDING_VERIFY
import com.elder.wechatvideo.core.CallStateMachine.Companion.MAX_PLUS_RETRIES
import com.elder.wechatvideo.core.CallStateMachine.Companion.MAX_SEARCH_CLICK_RETRIES
import com.elder.wechatvideo.core.CallStateMachine.Companion.MAX_SEARCH_EDITTEXT_RETRIES
import com.elder.wechatvideo.core.CallStateMachine.Companion.MAX_SEARCH_RESULT_RETRIES
import com.elder.wechatvideo.core.CallStateMachine.Companion.PLUS_PANEL_DELAY
import com.elder.wechatvideo.core.CallStateMachine.Companion.SEARCH_RESULT_DELAY
import com.elder.wechatvideo.core.CallStateMachine.Companion.STEP_DELAY
import com.elder.wechatvideo.core.CallStateMachine.Companion.TIMEOUT_CHECK_INTERVAL
import com.elder.wechatvideo.core.CallStateMachine.Companion.TOTAL_CALL_TIMEOUT
import com.elder.wechatvideo.core.CallStateMachine.Companion.WECHAT_LOAD_DELAY
import com.elder.wechatvideo.core.CallStateMachine.State
import com.elder.wechatvideo.core.NodeFinder
import com.elder.wechatvideo.core.OcrHelper
import com.elder.wechatvideo.core.OverlayController
import com.elder.wechatvideo.util.PositionConfig
import com.elder.wechatvideo.util.WeChatConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 微信无障碍服务 —— 薄胶水层（V1.3.0 重构）。
 *
 * 职责仅限于：
 * 1. 接收系统 AccessibilityEvent → 转发给 Engine 逻辑
 * 2. 管理 CallStateMachine / NodeFinder / OverlayController 的生命周期
 * 3. 暴露静态入口（startCall / cancelCall / isCallRunning）
 *
 * 所有业务逻辑委托给 core/ 包下的三个组件：
 * - [CallStateMachine]：状态转换 + 重试计数 + 超时判定（纯 Kotlin，可单测）
 * - [NodeFinder]：节点查找 / 过滤 / 匹配（四道防线）
 * - [OverlayController]：悬浮窗进度提示 + 取消按钮
 */
class WeChatAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WeChatA11y"

        @Volatile
        private var pendingCall = false

        @Volatile
        private var targetContactName = ""

        @Volatile
        private var instance: WeChatAccessibilityService? = null

        @Volatile
        var isConnected = false
            private set

        /**
         * 发起呼叫请求。
         *
         * @param contactName 联系人搜索名称
         * @return true 表示请求已被接受并开始执行；false 表示服务未连接或已有呼叫在运行
         */
        fun startCall(contactName: String = ""): Boolean {
            val svc = instance
            if (svc == null) {
                Log.w(TAG, "无障碍服务未连接，无法发起呼叫: $contactName")
                return false
            }
            if (svc.sm.isActive || pendingCall) {
                Log.w(TAG, "已有呼叫在运行，拒绝新请求: $contactName")
                return false
            }
            targetContactName = contactName
            pendingCall = true
            Log.i(TAG, "收到呼叫请求，联系人: $contactName")
            svc.beginCallFlow()
            return true
        }

        fun cancelCall() {
            instance?.handler?.post {
                instance?.let { svc ->
                    if (svc.sm.isActive) {
                        Log.i(TAG, "用户取消呼叫")
                        svc.handler.removeCallbacks(svc.timeoutChecker)
                        svc.overlay.show("已取消呼叫")
                        svc.showToast("已取消")
                        svc.handler.postDelayed({ svc.overlay.hide() }, 2000)
                        svc.sm.resetToIdle()
                        pendingCall = false
                        targetContactName = ""
                    }
                }
            }
        }

        fun isCallRunning(): Boolean = instance?.sm?.isActive ?: false
    }

    private val sm = CallStateMachine()
    private val nodeFinder = NodeFinder()
    private lateinit var overlay: OverlayController
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 落点校验失败后是否已用过一次「返回并重搜」兜底（每次呼叫只允许 1 次，防死循环） */
    @Volatile
    private var landingRetryUsed = false

    /**
     * OCR 进行中标志：防止 timeoutChecker 在 OCR 期间触发搜索重试导致竞态。
     *
     * 竞态场景：OCR 耗时超过 SEARCH_STATE_TIMEOUT(15s) 时，timeoutChecker 触发
     * resetSearchForRetry + trySearchContact，新搜索开始后 OCR 协程完成，
     * sm.isActive 仍为 true → OCR 点击旧搜索结果坐标 → 点错联系人。
     *
     * 修复：OCR 期间置 true，timeoutChecker 检测到此标志时跳过搜索重试
     * （总超时仍可触发 fail，确保不会无限等待）。
     */
    @Volatile
    private var ocrInProgress = false

    /**
     * 统一存活检查的延迟调度：回调执行前先确认服务仍连接、实例未被替换、状态机仍活跃，
     * 不满足则直接丢弃，避免服务销毁/呼叫结束后残留回调触碰已失效对象。
     * 仅用于「流程续接」类回调；收尾清理类回调（overlay.hide + resetToIdle）不走此方法，
     * 因为它们需要在 DONE（非活跃）状态下执行。
     */
    private fun postDelayedSafely(delay: Long, action: () -> Unit) {
        handler.postDelayed({
            if (!isConnected || instance !== this || !sm.isActive) return@postDelayed
            action()
        }, delay)
    }

    /* ===================== 生命周期 ===================== */

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        instance = this
        overlay = OverlayController(this)
        overlay.onCancelClick = { cancelCall() }
        // P0 修复：服务重连时清除可能残留的 pendingCall。
        // 场景：服务在呼叫过程中崩溃，Android 不保证调用 onDestroy()，
        // pendingCall 永久为 true → 服务重启后所有新呼叫被 startCall() 拒绝（死锁）。
        // onServiceConnected 是服务重建后的第一个回调，在此重置可保证恢复到干净状态。
        pendingCall = false
        targetContactName = ""
        Log.i(TAG, "无障碍服务已连接，pendingCall 已重置")
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        instance = null
        pendingCall = false
        targetContactName = ""
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.destroy()
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
        if (::overlay.isInitialized) overlay.hide()
        sm.resetToIdle()
        pendingCall = false
        targetContactName = ""
    }

    /* ===================== 事件入口 ===================== */

    private fun beginCallFlow() {
        handler.post {
            if (pendingCall && sm.state == State.IDLE) {
                handler.removeCallbacksAndMessages(null)
                sm.resetCounters()
                landingRetryUsed = false
                sm.start()
                showProgress("正在准备微信…")
                postDelayedSafely(WECHAT_LOAD_DELAY) {
                    if (sm.state == State.SEARCHING_CONTACT) trySearchContact()
                }
                startTimeoutChecker()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != WeChatConstants.WECHAT_PACKAGE) return
        if (!sm.isActive) return
        if (sm.state == State.DONE) return

        val className = event.className?.toString() ?: ""
        if (isVideoCallStarted(className)) {
            Log.i(TAG, "视频通话已发起 ✓")
            handler.removeCallbacks(timeoutChecker)
            showProgress("✓ 视频通话已发起")
            showToast("视频通话已发起")
            sm.transitionTo(State.DONE)
            handler.postDelayed({ overlay.hide(); sm.resetToIdle(); pendingCall = false }, 2000)
        }
    }

    /* ===================== 搜索联系人 ===================== */

    private fun trySearchContact() {
        val root = rootInActiveWindow ?: run {
            postDelayedSafely(800) { if (sm.state == State.SEARCHING_CONTACT) trySearchContact() }
            return
        }
        try {

        if (!sm.searchButtonClicked) {
            sm.incrementSearchClick()
            showProgress("① 正在打开搜索… (第${sm.searchClickRetries}次)")

            if (!sm.usedAccessibilityFallback) {
                val pos = PositionConfig.getSearchButton(applicationContext)
                if (pos != null) {
                    gestureClickAt(pos.x, pos.y)
                    sm.markSearchButtonClicked()
                    postDelayedSafely(STEP_DELAY) {
                        if (sm.state == State.SEARCHING_CONTACT && !sm.searchTextChanged) trySearchContact()
                    }
                    return
                }
            }

            if (nodeFinder.findSearchButton(root)?.let { clickNode(it); true } == true) {
                sm.markUsedFallback()
                sm.markSearchButtonClicked()
                postDelayedSafely(STEP_DELAY) {
                    if (sm.state == State.SEARCHING_CONTACT && !sm.searchTextChanged) trySearchContact()
                }
                return
            }

            if (sm.searchClickRetries >= MAX_SEARCH_CLICK_RETRIES) {
                fail("搜索按钮无法打开，请重新校准")
                return
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
            postDelayedSafely(STEP_DELAY) {
                if (sm.state == State.SEARCHING_CONTACT) {
                    sm.resetSearchForRetry()
                    trySearchContact()
                }
            }
            return
        }

        if (!sm.searchTextChanged) {
            val editText = nodeFinder.findSearchEditText(root)
            if (editText != null) {
                val bundle = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetContactName)
                }
                val success = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                try { editText.recycle() } catch (_: Exception) {}
                if (success) {
                    sm.markSearchTextChanged()
                    showProgress("② 正在搜索: $targetContactName")
                    postDelayedSafely(SEARCH_RESULT_DELAY) {
                        if (sm.state == State.SEARCHING_CONTACT) tryClickSearchResult()
                    }
                    return
                }
            }
            sm.incrementSearchEditText()
            if (sm.searchEditTextRetries >= MAX_SEARCH_EDITTEXT_RETRIES) {
                sm.resetSearchForRetry()
                sm.markUsedFallback()
                postDelayedSafely(500) { if (sm.state == State.SEARCHING_CONTACT) trySearchContact() }
                return
            }
            postDelayedSafely(800) { if (sm.state == State.SEARCHING_CONTACT) trySearchContact() }
            return
        }

        tryClickSearchResult()

        } finally {
            root.recycle()
        }
    }

    private fun tryClickSearchResult() {
        val root = rootInActiveWindow ?: run {
            postDelayedSafely(800) { if (sm.state == State.SEARCHING_CONTACT) tryClickSearchResult() }
            return
        }
        try {
        val target = nodeFinder.findContactInSearchResults(root, targetContactName)
        if (target != null) {
            clickNode(target)
            showProgress("③ 已点击联系人，校验落点…")
            sm.transitionTo(State.VERIFYING_LANDING)
            startLandingVerification()
            return
        }
        sm.incrementSearchResult()
        if (sm.searchResultAttempts < MAX_SEARCH_RESULT_RETRIES) {
            postDelayedSafely(1000) { if (sm.state == State.SEARCHING_CONTACT) tryClickSearchResult() }
            return
        }
        // 第2层：OCR 精准识别（按名字找人，不会点错）
        handler.removeCallbacksAndMessages(null) // 清空搜索重试回调，防止抢先触发
        startTimeoutChecker() // 恢复超时保护（OCR 期间仍需超时兜底）
        ocrInProgress = true // P0 修复：阻止 timeoutChecker 在 OCR 期间触发搜索重试
        scope.launch {
            try {
            val ocrEnabled = com.elder.wechatvideo.util.SettingsPrefs.isOcrEnabled(applicationContext)
            val strictMode = com.elder.wechatvideo.util.SettingsPrefs.isOcrStrictMode(applicationContext)

            if (ocrEnabled) showProgress("③ 正在智能识别联系人…")
            else showProgress("③ 正在定位联系人…")

            var ocrFound = false
            if (ocrEnabled) {
                // P1 修复：OCR 截图处理和文本匹配在 Default 线程执行，
                // 避免 allLines 遍历在主线程造成卡顿（搜索结果多时尤其明显）。
                val pos = withContext(Dispatchers.Default) {
                    OcrHelper.findContactPosition(
                        this@WeChatAccessibilityService,
                        targetContactName
                    )
                }
                // P0 修复：OCR 完成后校验状态未变（未被 timeoutChecker/fail 重置）
                if (pos != null && sm.isActive && sm.state == State.SEARCHING_CONTACT) {
                    ocrFound = true
                    gestureClickAt(pos.first, pos.second)
                    showProgress("③ 已点击联系人（OCR），校验落点…")
                    sm.transitionTo(State.VERIFYING_LANDING)
                    startLandingVerification()
                }
            }

            if (!ocrFound && sm.isActive && sm.state == State.SEARCHING_CONTACT) {
                if (ocrEnabled && strictMode) {
                    fail("未找到联系人「$targetContactName」，已安全中止")
                    return@launch
                }
                // 第3层：校准坐标盲戳（最后手段）
                val coord = PositionConfig.getSearchResultCoord(applicationContext)
                if (coord != null) {
                    gestureClickAt(coord.x, coord.y)
                    sm.transitionTo(State.VERIFYING_LANDING)
                    startLandingVerification()
                } else {
                    fail("未找到联系人「$targetContactName」，已安全中止")
                }
            }
            } finally {
                ocrInProgress = false
            }
        }
        } finally {
            root.recycle()
        }
    }

    /* ===================== 落点校验 ===================== */

    private fun startLandingVerification() {
        runLandingVerification()
    }

    private fun runLandingVerification() {
        if (sm.state != State.VERIFYING_LANDING) return
        val root = rootInActiveWindow ?: run { scheduleLandingPoll(); return }
        try {
        when {
            nodeFinder.isOfficialAccountPage(root) ->
                fail("误入公众号，已中止（未误拨）")
            nodeFinder.isNonChatPage(root) ->
                fail("进入了非聊天页面，已中止")
            nodeFinder.hasChatSessionIndicator(root) -> {
                sm.transitionTo(State.CLICKING_CONTACT)
                postDelayedSafely(STEP_DELAY) { if (sm.state == State.CLICKING_CONTACT) tryClickPlusButton() }
            }
            !nodeFinder.isOnSearchResultsPage(root) -> {
                // 离开了搜索结果页但没有聊天页特征，继续等一帧（可能页面还在加载）
                scheduleLandingPoll()
            }
            else -> scheduleLandingPoll()
        }
        } finally {
            root.recycle()
        }
    }

    private fun scheduleLandingPoll() {
        sm.incrementLandingVerify()
        if (sm.landingVerifyAttempts >= MAX_LANDING_VERIFY) {
            // 兜底：返回并重新发起一次搜索（整个流程仅限 1 次自动重试，防止死循环）
            if (!landingRetryUsed) {
                landingRetryUsed = true
                Log.w(TAG, "落点校验重试达上限，返回并自动重搜一次")
                showProgress("没有进入聊天，正在再试一次…")
                performGlobalAction(GLOBAL_ACTION_BACK)
                postDelayedSafely(STEP_DELAY) {
                    if (sm.state == State.VERIFYING_LANDING) {
                        sm.restartSearch()
                        trySearchContact()
                    }
                }
                return
            }
            fail("没有拨通，请重试一次")
            return
        }
        postDelayedSafely(LANDING_POLL_INTERVAL) { runLandingVerification() }
    }

    /* ===================== + 按钮 ===================== */

    private fun tryClickPlusButton() {
        if (sm.plusButtonClicked) return
        sm.incrementPlusButton()
        showProgress("④ 正在点击 + 按钮… (第${sm.plusButtonRetries}次)")

        if (sm.plusButtonRetries > MAX_PLUS_RETRIES) {
            fail("+ 按钮无法点击，请重新校准")
            return
        }

        val pos = PositionConfig.getPlusButton(applicationContext)
        if (pos != null) {
            gestureClickAt(pos.x, pos.y)
            sm.markPlusButtonClicked()
            sm.transitionTo(State.CLICKING_PLUS)
            postDelayedSafely(PLUS_PANEL_DELAY) { if (sm.state == State.CLICKING_PLUS) tryClickVideoCall() }
            return
        }

        val root = rootInActiveWindow ?: run {
            postDelayedSafely(800) { if (sm.state == State.CLICKING_CONTACT) tryClickPlusButton() }
            return
        }
        try {
        val screenHeight = resources.displayMetrics.heightPixels
        val plusNode = nodeFinder.findPlusButton(root, screenHeight)
        if (plusNode != null) {
            clickNode(plusNode)
            sm.markPlusButtonClicked()
            sm.transitionTo(State.CLICKING_PLUS)
            postDelayedSafely(PLUS_PANEL_DELAY) { if (sm.state == State.CLICKING_PLUS) tryClickVideoCall() }
            return
        }

        postDelayedSafely(1000) { if (sm.state == State.CLICKING_CONTACT) tryClickPlusButton() }
        } finally {
            root.recycle()
        }
    }

    /* ===================== 视频通话 ===================== */

    private fun tryClickVideoCall() {
        if (sm.videoCallClicked) return

        val root = rootInActiveWindow ?: run {
            postDelayedSafely(800) { if (sm.state == State.CLICKING_PLUS) tryClickVideoCall() }
            return
        }
        try {
            val videoNode = nodeFinder.findVideoCallButton(root)
            if (videoNode != null) {
                sm.markVideoCallClicked()
                showProgress("⑤ 正在点击视频通话图标…")
                clickNode(videoNode)
                sm.transitionTo(State.CLICKING_VIDEO)
                postDelayedSafely(STEP_DELAY) { if (sm.state == State.CLICKING_VIDEO) tryClickVideoConfirm() }
                return
            }
        } finally {
            root.recycle()
        }

        val pos = PositionConfig.getVideoCallButton(applicationContext)
        if (pos != null) {
            sm.markVideoCallClicked()
            showProgress("⑤ 正在点击视频通话图标…")
            gestureClickAt(pos.x, pos.y)
            sm.transitionTo(State.CLICKING_VIDEO)
            postDelayedSafely(STEP_DELAY) { if (sm.state == State.CLICKING_VIDEO) tryClickVideoConfirm() }
            return
        }

        postDelayedSafely(1000) { if (sm.state == State.CLICKING_PLUS) tryClickVideoCall() }
    }

    private fun tryClickVideoConfirm() {
        if (sm.videoConfirmClicked) return

        // 自动拨打关闭：验证视频/语音菜单已弹出后停在菜单
        if (!PositionConfig.isAutoDialEnabled(applicationContext)) {
            val root = rootInActiveWindow
            if (root != null) {
                try {
                    val hasMenu = nodeFinder.findVideoCallButton(root) != null ||
                        WeChatConstants.VIDEO_CALL_TEXTS.any { text ->
                            root.findAccessibilityNodeInfosByText(text).isNotEmpty()
                        } ||
                        root.findAccessibilityNodeInfosByText("语音通话").isNotEmpty()
                    if (hasMenu) {
                        showProgress("✓ 已打开通话菜单，请选择视频或语音")
                        showToast("请选择视频通话或语音通话")
                        sm.transitionTo(State.DONE)
                        handler.postDelayed({ overlay.hide(); sm.resetToIdle(); pendingCall = false }, 3000)
                        return
                    }
                } finally {
                    root.recycle()
                }
            }
            fail("未打开视频通话菜单，请重新校准视频通话按钮坐标")
            return
        }

        val pos = PositionConfig.getVideoConfirmButton(applicationContext)
        if (pos != null) {
            sm.markVideoConfirmClicked()
            showProgress("⑥ 正在确认视频通话…")
            gestureClickAt(pos.x, pos.y)
            sm.transitionTo(State.CLICKING_VIDEO_CONFIRM)
            return
        }

        // 无三级菜单坐标 → 明确告知用户需要校准，不静默 DONE
        fail("未校准「视频通话确认」按钮，请进入校准完成第⑥步")
    }

    /* ===================== 超时检查 ===================== */

    private val timeoutChecker = object : Runnable {
        override fun run() {
            if (!isConnected || instance !== this@WeChatAccessibilityService || !sm.isActive) return
            val now = System.currentTimeMillis()
            if (sm.isTotalTimeout(now, TOTAL_CALL_TIMEOUT)) {
                // 总超时：给出明确中文提示，不静默结束
                fail("没有拨通，请重新试一次")
                return
            }
            // V1.3.2 分状态超时：按当前状态取对应超时值
            if (sm.isStateTimeout(now, sm.timeoutForState(sm.state))) {
                sm.incrementClickAttempts()
                if (sm.clickAttempts >= 5) {
                    fail("操作没有成功，请重新试一次")
                    return
                }
                when (sm.state) {
                    // P0 修复：OCR 进行中时跳过搜索重试，防止竞态导致点错联系人
                    State.SEARCHING_CONTACT -> {
                        if (!ocrInProgress) { sm.resetSearchForRetry(); trySearchContact() }
                    }
                    State.CLICKING_CONTACT -> tryClickPlusButton()
                    State.CLICKING_PLUS -> tryClickVideoCall()
                    State.CLICKING_VIDEO -> tryClickVideoConfirm()
                    else -> {}
                }
            }
            handler.postDelayed(this, TIMEOUT_CHECK_INTERVAL)
        }
    }

    private fun startTimeoutChecker() {
        handler.removeCallbacks(timeoutChecker)
        handler.postDelayed(timeoutChecker, TIMEOUT_CHECK_INTERVAL)
    }

    /* ===================== 手势与点击 ===================== */

    private fun gestureClickAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 120))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        try {
            // 沿 parent 链上溯找可点击节点，中间节点及时 recycle
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) {
                val parent = target.parent
                if (target !== node) target.recycle()
                target = parent
            }
            if (target != null) {
                val ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!ok) {
                    Log.w(TAG, "clickNode: performAction(ACTION_CLICK) 返回 false，改用坐标点击")
                    val rect = Rect()
                    target.getBoundsInScreen(rect)
                    gestureClickAt(rect.exactCenterX(), rect.exactCenterY())
                }
                if (target !== node) target.recycle()
            } else {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                gestureClickAt(rect.exactCenterX(), rect.exactCenterY())
            }
        } catch (e: Exception) {
            Log.w(TAG, "clickNode 失败", e)
        } finally {
            // 统一回收传入节点（调用方不再负责）
            try { node.recycle() } catch (_: Exception) {}
        }
    }

    /* ===================== 辅助 ===================== */

    private fun isVideoCallStarted(className: String): Boolean {
        return className.contains("VOIP", ignoreCase = true) ||
               className.contains("opengl", ignoreCase = true) ||
               (className.contains("SurfaceView", ignoreCase = true) &&
                (sm.state == State.CLICKING_VIDEO || sm.state == State.CLICKING_VIDEO_CONFIRM))
    }

    private fun showProgress(msg: String) {
        Log.i(TAG, "进度: $msg")
        overlay.show(msg)
    }

    private fun showToast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun fail(reason: String) {
        Log.e(TAG, "失败: $reason")
        showProgress("✗ $reason")
        showToast(reason)
        handler.removeCallbacks(timeoutChecker)
        // V1.6.5 修复：立即清除 pendingCall，允许用户重试。
        // 之前 pendingCall 延迟 3 秒才清除，期间 CallBridgeActivity 的 6 次重试全部被拒，
        // 报"无障碍服务启动中，请稍后再试"。
        pendingCall = false
        targetContactName = ""
        sm.resetToIdle()
        handler.postDelayed({ overlay.hide() }, 3000)
    }
}
