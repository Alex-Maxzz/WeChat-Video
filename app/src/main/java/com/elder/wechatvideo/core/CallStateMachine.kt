package com.elder.wechatvideo.core

/**
 * 纯 Kotlin 呼叫状态机，零 Android 依赖，可独立单元测试。
 *
 * 职责：管理状态转换逻辑 + 重试计数 + 超时判定。
 * 不持有任何 View / Context / Handler 引用。
 *
 * 线程安全（V1.3.2）：状态读写、计数器自增、重置与超时判断均通过内部锁 [lock] 同步，
 * 避免主线程流程回调与静态入口（startCall / cancelCall / isCallRunning）并发访问时的竞态。
 */
class CallStateMachine {

    enum class State {
        IDLE,
        SEARCHING_CONTACT,
        CLICKING_CONTACT,
        VERIFYING_LANDING,
        CLICKING_PLUS,
        CLICKING_VIDEO,
        CLICKING_VIDEO_CONFIRM,
        DONE
    }

    /** 内部锁对象：保护状态 / 计数器 / 标志位的原子读写 */
    private val lock = Any()

    var state: State = State.IDLE
        get() = synchronized(lock) { field }
        private set

    var stateEnterTime: Long = 0L
        get() = synchronized(lock) { field }
        private set

    var callStartTime: Long = 0L
        get() = synchronized(lock) { field }
        private set

    // 重试计数（读取经内部锁，保证与自增操作互斥）
    var searchClickRetries = 0
        get() = synchronized(lock) { field }
        private set
    var searchEditTextRetries = 0
        get() = synchronized(lock) { field }
        private set
    var searchResultAttempts = 0
        get() = synchronized(lock) { field }
        private set
    var landingVerifyAttempts = 0
        get() = synchronized(lock) { field }
        private set
    var plusButtonRetries = 0
        get() = synchronized(lock) { field }
        private set
    var clickAttempts = 0
        get() = synchronized(lock) { field }
        private set

    // 标志位
    var searchButtonClicked = false
        get() = synchronized(lock) { field }
        private set
    var searchTextChanged = false
        get() = synchronized(lock) { field }
        private set
    var usedAccessibilityFallback = false
        get() = synchronized(lock) { field }
        private set
    var plusButtonClicked = false
        get() = synchronized(lock) { field }
        private set
    var videoCallClicked = false
        get() = synchronized(lock) { field }
        private set
    var videoConfirmClicked = false
        get() = synchronized(lock) { field }
        private set

    /** 状态变更回调 */
    var onTransition: ((from: State, to: State) -> Unit)? = null

    val isActive: Boolean
        get() = synchronized(lock) { state != State.IDLE && state != State.DONE }

    fun start(timestamp: Long = System.currentTimeMillis()) {
        synchronized(lock) { callStartTime = timestamp }
        transitionTo(State.SEARCHING_CONTACT)
    }

    fun transitionTo(newState: State) {
        val from: State
        synchronized(lock) {
            from = state
            state = newState
            stateEnterTime = System.currentTimeMillis()
            clickAttempts = 0
        }
        // 回调放在锁外触发，避免外部逻辑持锁引发死锁
        onTransition?.invoke(from, newState)
    }

    fun resetToIdle() {
        synchronized(lock) {
            state = State.IDLE
            resetCounters()
        }
    }

    fun resetCounters() {
        synchronized(lock) {
            searchClickRetries = 0
            searchEditTextRetries = 0
            searchResultAttempts = 0
            landingVerifyAttempts = 0
            plusButtonRetries = 0
            clickAttempts = 0
            searchButtonClicked = false
            searchTextChanged = false
            usedAccessibilityFallback = false
            plusButtonClicked = false
            videoCallClicked = false
            videoConfirmClicked = false
            callStartTime = 0L
        }
    }

    /* ===================== 重试计数递增 ===================== */

    fun incrementSearchClick() { synchronized(lock) { searchClickRetries++ } }
    fun incrementSearchEditText() { synchronized(lock) { searchEditTextRetries++ } }
    fun incrementSearchResult() { synchronized(lock) { searchResultAttempts++ } }
    fun incrementLandingVerify() { synchronized(lock) { landingVerifyAttempts++ } }
    fun incrementPlusButton() { synchronized(lock) { plusButtonRetries++ } }
    fun incrementClickAttempts() { synchronized(lock) { clickAttempts++ } }

    fun markSearchButtonClicked() { synchronized(lock) { searchButtonClicked = true } }
    fun markSearchTextChanged() { synchronized(lock) { searchTextChanged = true } }
    fun markUsedFallback() { synchronized(lock) { usedAccessibilityFallback = true } }
    fun markPlusButtonClicked() { synchronized(lock) { plusButtonClicked = true } }
    fun markVideoCallClicked() { synchronized(lock) { videoCallClicked = true } }
    fun markVideoConfirmClicked() { synchronized(lock) { videoConfirmClicked = true } }

    fun resetSearchForRetry() {
        synchronized(lock) {
            searchButtonClicked = false
            usedAccessibilityFallback = false
            searchEditTextRetries = 0
        }
    }

    /**
     * 落点校验失败兜底：清空搜索相关计数与标志并回到搜索状态，
     * 保留 callStartTime（总超时继续累计，防止兜底重试绕过总时限）。
     *
     * P2 修复：同时重置 plusButtonClicked / videoCallClicked / videoConfirmClicked /
     * plusButtonRetries，确保从落点校验返回搜索后，后续 + 按钮/视频/确认流程从干净状态开始。
     * 当前流程中这些标志在 VERIFYING_LANDING 阶段尚未被设置，但补全重置可防止未来
     * 流程变更后遗漏导致的隐性 bug。
     */
    fun restartSearch() {
        synchronized(lock) {
            searchClickRetries = 0
            searchEditTextRetries = 0
            searchResultAttempts = 0
            landingVerifyAttempts = 0
            plusButtonRetries = 0
            searchButtonClicked = false
            searchTextChanged = false
            usedAccessibilityFallback = false
            plusButtonClicked = false
            videoCallClicked = false
            videoConfirmClicked = false
        }
        transitionTo(State.SEARCHING_CONTACT)
    }

    /* ===================== 超时判定 ===================== */

    fun isTotalTimeout(now: Long, timeoutMs: Long): Boolean {
        synchronized(lock) {
            return callStartTime > 0 && (now - callStartTime) > timeoutMs
        }
    }

    fun isStateTimeout(now: Long, timeoutMs: Long): Boolean {
        synchronized(lock) {
            return (now - stateEnterTime) > timeoutMs
        }
    }

    /**
     * 按状态返回对应的单步超时（V1.3.2 分状态超时）。
     *
     * 取值需覆盖各阶段「重试次数 × 重试间隔」所需时间：
     * - 搜索阶段：微信加载 [WECHAT_LOAD_DELAY] 4s + 搜索按钮最多 4 次重试 × [STEP_DELAY] 2s，
     *   建议值 10s 不够用，故取 15s；
     * - 落点校验：最多 [MAX_LANDING_VERIFY] 8 次轮询 × [LANDING_POLL_INTERVAL] 500ms = 4s，12s 富余；
     * - 点 + / 视频 / 确认：单击 + 面板动画（1~2s 级），8s 富余。
     */
    fun timeoutForState(state: State): Long = when (state) {
        State.SEARCHING_CONTACT -> SEARCH_STATE_TIMEOUT
        State.VERIFYING_LANDING -> LANDING_STATE_TIMEOUT
        State.CLICKING_CONTACT,
        State.CLICKING_PLUS,
        State.CLICKING_VIDEO,
        State.CLICKING_VIDEO_CONFIRM -> CLICK_STATE_TIMEOUT
        else -> STATE_TIMEOUT
    }

    companion object {
        // 统一超时常量（V1.3.1：适配 60Hz 屏幕，动画/渲染需要更多时间）
        const val STEP_DELAY = 2000L          // 60Hz 下 UI 转场需更长等待（原 1500）
        const val PLUS_PANEL_DELAY = 1500L    // +面板弹出动画（原 1000）
        const val SEARCH_RESULT_DELAY = 3500L // 搜索结果渲染（原 3000）
        const val WECHAT_LOAD_DELAY = 4000L   // 微信冷启动/切前台（原 3000）
        const val TIMEOUT_CHECK_INTERVAL = 1000L
        const val STATE_TIMEOUT = 18000L      // 默认单步超时（未细分状态的兜底值）
        // V1.3.2 分状态超时（见 timeoutForState）
        const val SEARCH_STATE_TIMEOUT = 15000L  // 搜索输入+搜索结果阶段
        const val LANDING_STATE_TIMEOUT = 12000L // 落点校验阶段
        const val CLICK_STATE_TIMEOUT = 8000L    // 点+/视频/确认阶段
        const val TOTAL_CALL_TIMEOUT = 60000L // 总超时收紧（V1.3.2：原 75000）
        const val MAX_SEARCH_EDITTEXT_RETRIES = 3
        const val MAX_SEARCH_CLICK_RETRIES = 4
        const val MAX_PLUS_RETRIES = 4
        const val MAX_SEARCH_RESULT_RETRIES = 5
        const val LANDING_POLL_INTERVAL = 500L
        const val MAX_LANDING_VERIFY = 8
    }
}
