package com.elder.wechatvideo.util

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs

/**
 * 按键坐标配置存储
 *
 * 用户手动校准后，将微信各按键的屏幕坐标持久化保存。
 * 后续拨号时由 [com.elder.wechatvideo.service.WeChatAccessibilityService]
 * 在这些坐标上模拟手势点击。
 *
 * 坐标归一化策略（修复 B21）：
 * - 内部统一存储**归一化坐标**（0..1，相对于当前屏幕宽高），
 *   不再保存校准时的绝对像素。这样换机型、改分辨率或调整字体缩放后，
 *   使用处只需把归一化值乘以当前 [android.util.DisplayMetrics] 即可还原为正确像素，
 *   避免点击位置错位。
 * - 对外 API（[ButtonPosition]、各 getXxx 方法）仍返回**像素坐标**，
 *   内部自动按当前屏幕尺寸换算，调用方无需感知归一化细节。
 * - 旧版本数据存的是绝对像素（值 > 1.0），读取时检测到即一次性换算为归一化并回写，
 *   实现平滑迁移，不会丢失已有校准。
 */
object PositionConfig {

    private const val PREFS_NAME = "button_positions"

    // 搜索按钮坐标（微信首页顶部搜索图标）
    private const val KEY_SEARCH_X = "search_x"
    private const val KEY_SEARCH_Y = "search_y"

    // 加号按钮坐标（聊天页右下角 "+" 按钮）
    private const val KEY_PLUS_X = "plus_x"
    private const val KEY_PLUS_Y = "plus_y"

    // 视频通话按钮坐标（点击 "+" 后弹出的面板中 "视频通话" 图标）
    private const val KEY_VIDEO_X = "video_x"
    private const val KEY_VIDEO_Y = "video_y"

    // 三级菜单视频通话按钮坐标（点击"视频通话"后弹出的菜单中的"视频通话"选项）
    private const val KEY_VIDEO_CONFIRM_X = "video_confirm_x"
    private const val KEY_VIDEO_CONFIRM_Y = "video_confirm_y"

    // 搜索结果行坐标（C 部分校准兜底：节点过滤连续失败时的最后手段强点位置）
    private const val KEY_SEARCH_RESULT_X = "search_result_x"
    private const val KEY_SEARCH_RESULT_Y = "search_result_y"

    // 校准时的屏幕尺寸（配合设备参数变化检测复用，见 saveDeviceParams）
    private const val KEY_SCREEN_WIDTH = "screen_width"
    private const val KEY_SCREEN_HEIGHT = "screen_height"

    // 校准时的设备显示参数（分辨率复用上面两个 key）：用于检测显示设置变化后提醒重新校准
    private const val KEY_CALIB_DENSITY_DPI = "calib_density_dpi"
    private const val KEY_CALIB_FONT_SCALE = "calib_font_scale"

    // 校准是否已完成
    private const val KEY_CALIBRATION_DONE = "calibration_done"

    // 自动拨打开关（true=全自动拨打视频通话，false=只搜索到聊天界面停止）
    private const val KEY_AUTO_DIAL = "auto_dial"

    /** 按键坐标数据类（对外一律为像素坐标） */
    data class ButtonPosition(val x: Float, val y: Float)

    /** 旧数据迁移锁：避免多线程并发读取时重复迁移/交叉覆写 */
    private val migrationLock = Any()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 当前屏幕尺寸（像素） */
    private fun currentScreen(context: Context): Pair<Float, Float> {
        val dm = context.resources.displayMetrics
        return dm.widthPixels.toFloat() to dm.heightPixels.toFloat()
    }

    /* ===================== 状态查询 ===================== */

    fun isCalibrated(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CALIBRATION_DONE, false)
    }

    fun getSearchButton(context: Context): ButtonPosition? {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_SEARCH_X)) return null
        // 读取归一化值；若为旧版绝对像素(>1.0)则一次性迁移
        val (nx, ny) = readNormalized(prefs, KEY_SEARCH_X, KEY_SEARCH_Y, context)
        val (w, h) = currentScreen(context)
        return ButtonPosition(nx * w, ny * h)
    }

    fun getPlusButton(context: Context): ButtonPosition? {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_PLUS_X)) return null
        val (nx, ny) = readNormalized(prefs, KEY_PLUS_X, KEY_PLUS_Y, context)
        val (w, h) = currentScreen(context)
        return ButtonPosition(nx * w, ny * h)
    }

    fun getVideoCallButton(context: Context): ButtonPosition? {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_VIDEO_X)) return null
        val (nx, ny) = readNormalized(prefs, KEY_VIDEO_X, KEY_VIDEO_Y, context)
        val (w, h) = currentScreen(context)
        return ButtonPosition(nx * w, ny * h)
    }

    /**
     * 获取三级菜单中"视频通话"选项的坐标。
     * 点击面板中的"视频通话"图标后会弹出选择菜单（视频通话/语音通话），
     * 此坐标用于点击该菜单中的"视频通话"。
     * 如果未校准此步骤，返回 null（兼容旧版校准数据）。
     */
    fun getVideoConfirmButton(context: Context): ButtonPosition? {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_VIDEO_CONFIRM_X)) return null
        val (nx, ny) = readNormalized(prefs, KEY_VIDEO_CONFIRM_X, KEY_VIDEO_CONFIRM_Y, context)
        val (w, h) = currentScreen(context)
        return ButtonPosition(nx * w, ny * h)
    }

    /**
     * 获取「搜索结果行」坐标（C 部分校准兜底用）。
     *
     * 仅在用户于校准流程中专门取过点时返回非 null；未校准则返回 null，
     * 此时无障碍服务在节点过滤失败时不兜底，保持「宁可拨不通也不拨错人」的安全底线。
     */
    fun getSearchResultCoord(context: Context): ButtonPosition? {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_SEARCH_RESULT_X)) return null
        val (nx, ny) = readNormalized(prefs, KEY_SEARCH_RESULT_X, KEY_SEARCH_RESULT_Y, context)
        val (w, h) = currentScreen(context)
        return ButtonPosition(nx * w, ny * h)
    }

    /**
     * 读取一对归一化坐标，兼容旧版绝对像素数据。
     * 若任一值 > 1.0，视为旧版绝对像素，按当前屏幕尺寸换算为归一化并回写（一次性迁移）。
     *
     * @return 归一化坐标 (nx, ny)，范围 [0,1]
     */
    private fun readNormalized(
        prefs: SharedPreferences,
        keyX: String,
        keyY: String,
        context: Context
    ): Pair<Float, Float> {
        val (w, h) = currentScreen(context)
        return readNormalized(prefs, keyX, keyY, w, h)
    }

    /** 同上，屏幕尺寸可注入的内部实现（供纯 JVM 单测直接调用，无需构造 Context） */
    internal fun readNormalized(
        prefs: SharedPreferences,
        keyX: String,
        keyY: String,
        screenW: Float,
        screenH: Float
    ): Pair<Float, Float> = synchronized(migrationLock) {
        val rx = prefs.getFloat(keyX, 0f)
        val ry = prefs.getFloat(keyY, 0f)
        return if (rx > 1.0f || ry > 1.0f) {
            // 旧版绝对像素 → 归一化（一次性迁移）；用 commit() 同步落盘，避免 apply() 异步期间重复迁移
            val nx = (rx / screenW).coerceIn(0f, 1f)
            val ny = (ry / screenH).coerceIn(0f, 1f)
            prefs.edit().putFloat(keyX, nx).putFloat(keyY, ny).commit()
            nx to ny
        } else {
            rx to ry
        }
    }

    /**
     * 将像素坐标写入为归一化值（0..1，相对于当前屏幕宽高）。
     */
    private fun writeNormalized(
        context: Context,
        editor: SharedPreferences.Editor,
        keyX: String,
        keyY: String,
        x: Float,
        y: Float
    ) {
        val (w, h) = currentScreen(context)
        writeNormalized(editor, keyX, keyY, x, y, w, h)
    }

    /** 同上，屏幕尺寸可注入的内部实现（供纯 JVM 单测直接调用） */
    internal fun writeNormalized(
        editor: SharedPreferences.Editor,
        keyX: String,
        keyY: String,
        x: Float,
        y: Float,
        screenW: Float,
        screenH: Float
    ) {
        editor.putFloat(keyX, (x / screenW).coerceIn(0f, 1f))
        editor.putFloat(keyY, (y / screenH).coerceIn(0f, 1f))
    }

    /**
     * 是否自动拨打视频通话。
     * true（默认）= 全自动：搜索→进聊天→点+→点视频通话→点确认→拨出
     * false = 只搜索到聊天界面，不自动拨打
     */
    fun isAutoDialEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_DIAL, true)
    }

    fun setAutoDialEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_DIAL, enabled).apply()
    }

    /* ===================== 保存坐标 ===================== */

    fun saveSearchButton(context: Context, x: Float, y: Float) {
        val editor = getPrefs(context).edit()
        writeNormalized(context, editor, KEY_SEARCH_X, KEY_SEARCH_Y, x, y)
        editor.apply()
    }

    fun savePlusButton(context: Context, x: Float, y: Float) {
        val editor = getPrefs(context).edit()
        writeNormalized(context, editor, KEY_PLUS_X, KEY_PLUS_Y, x, y)
        editor.apply()
    }

    fun saveVideoCallButton(context: Context, x: Float, y: Float) {
        val editor = getPrefs(context).edit()
        writeNormalized(context, editor, KEY_VIDEO_X, KEY_VIDEO_Y, x, y)
        editor.apply()
    }

    fun saveVideoConfirmButton(context: Context, x: Float, y: Float) {
        val editor = getPrefs(context).edit()
        writeNormalized(context, editor, KEY_VIDEO_CONFIRM_X, KEY_VIDEO_CONFIRM_Y, x, y)
        editor.apply()
    }

    /**
     * 保存「搜索结果行」坐标（C 部分校准兜底用）。
     * 沿用与搜索/加号等按钮一致的归一化存取约定（0..1，读取时按当前 DisplayMetrics 还原）。
     */
    fun saveSearchResultCoord(context: Context, x: Float, y: Float) {
        val editor = getPrefs(context).edit()
        writeNormalized(context, editor, KEY_SEARCH_RESULT_X, KEY_SEARCH_RESULT_Y, x, y)
        editor.apply()
    }

    fun saveScreenSize(context: Context, width: Int, height: Int) {
        getPrefs(context).edit()
            .putInt(KEY_SCREEN_WIDTH, width)
            .putInt(KEY_SCREEN_HEIGHT, height)
            .apply()
    }

    fun setCalibrationDone(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_CALIBRATION_DONE, true).apply()
    }

    /* ===================== 设备参数记录与变化检测 ===================== */

    /**
     * 记录校准时的设备显示参数：屏幕宽高像素、densityDpi、fontScale。
     * 在校准完成时调用；后续拨号前可用 [isDeviceParamsChanged] 检测显示设置是否变化。
     */
    fun saveDeviceParams(context: Context) {
        val dm = context.resources.displayMetrics
        val fontScale = context.resources.configuration.fontScale
        saveDeviceParams(getPrefs(context), dm.widthPixels, dm.heightPixels, dm.densityDpi, fontScale)
    }

    /** 同上，设备参数可注入的内部实现（供纯 JVM 单测直接调用） */
    internal fun saveDeviceParams(
        prefs: SharedPreferences,
        widthPixels: Int,
        heightPixels: Int,
        densityDpi: Int,
        fontScale: Float
    ) {
        prefs.edit()
            .putInt(KEY_SCREEN_WIDTH, widthPixels)
            .putInt(KEY_SCREEN_HEIGHT, heightPixels)
            .putInt(KEY_CALIB_DENSITY_DPI, densityDpi)
            .putFloat(KEY_CALIB_FONT_SCALE, fontScale)
            .apply()
    }

    /**
     * 设备显示参数是否相比校准时发生变化。
     *
     * 分辨率或 densityDpi 任一变化、或 |fontScale 差| > 0.1 时返回 true；
     * 无历史记录（旧版校准数据）时返回 false，不误报。
     */
    fun isDeviceParamsChanged(context: Context): Boolean {
        val dm = context.resources.displayMetrics
        return isDeviceParamsChanged(
            getPrefs(context), dm.widthPixels, dm.heightPixels, dm.densityDpi,
            context.resources.configuration.fontScale
        )
    }

    /** 同上，设备参数可注入的内部实现（供纯 JVM 单测直接调用） */
    internal fun isDeviceParamsChanged(
        prefs: SharedPreferences,
        widthPixels: Int,
        heightPixels: Int,
        densityDpi: Int,
        fontScale: Float
    ): Boolean {
        if (!prefs.contains(KEY_CALIB_DENSITY_DPI)) return false
        if (prefs.getInt(KEY_SCREEN_WIDTH, 0) != widthPixels) return true
        if (prefs.getInt(KEY_SCREEN_HEIGHT, 0) != heightPixels) return true
        if (prefs.getInt(KEY_CALIB_DENSITY_DPI, 0) != densityDpi) return true
        val savedFontScale = prefs.getFloat(KEY_CALIB_FONT_SCALE, 1f)
        return abs(fontScale - savedFontScale) > 0.1f
    }

    fun clearCalibration(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
