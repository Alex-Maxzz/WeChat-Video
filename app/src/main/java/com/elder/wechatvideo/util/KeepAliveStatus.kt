package com.elder.wechatvideo.util

/**
 * 保活与权限项的总体状态。
 *
 * 各字段统一表示「良好状态」：true 代表该项已就绪，应用可正常保活。
 * 所有字段均由 [PermissionUtils.getKeepAliveStatus] 通过真实检测填充，
 * 不再硬编码 true（修复 B19 的假进度项）。
 *
 * @property accessibilityEnabled 无障碍服务是否已开启（核心功能，必须开启）
 * @property batteryExempt 是否已豁免电池优化（true 表示已加入白名单，不会被系统限制后台）。
 *        语义修正：原字段名 batteryOptimized 与语义相反，Correct naming = batteryExempt。
 * @property notificationGranted 通知权限是否已授予（Android 13+ 需运行时申请）
 * @property autostartEnabled 开机自启是否已开启。读取自 SharedPreferences 用户开关
 *        （[com.elder.wechatvideo.keepalive.AutostartPrefs]，默认 true），
 *        此处 data class 字段默认 false 仅为安全初始值，实际由检测逻辑填充。
 * @property backgroundEnabled 后台运行是否允许。反映保活前台服务是否实际在运行
 *        （[com.elder.wechatvideo.keepalive.KeepAliveService]），不再默认 true。
 * @property shortcutPermissionGranted 桌面快捷方式权限是否已授予
 */
data class KeepAliveStatus(
    val accessibilityEnabled: Boolean = false,
    val batteryExempt: Boolean = false,
    val notificationGranted: Boolean = false,
    val autostartEnabled: Boolean = false,
    val backgroundEnabled: Boolean = false,
    val shortcutPermissionGranted: Boolean = false
) {
    /** 保活项总数 */
    val totalCount: Int = 6

    /**
     * 已就绪的保活项数量（共 6 项）。
     */
    val completedCount: Int
        get() = listOf(
            accessibilityEnabled,
            batteryExempt,
            notificationGranted,
            autostartEnabled,
            backgroundEnabled,
            shortcutPermissionGranted
        ).count { it }
}
