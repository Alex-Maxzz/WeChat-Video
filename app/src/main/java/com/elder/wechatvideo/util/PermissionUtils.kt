package com.elder.wechatvideo.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.pm.ShortcutManagerCompat
import com.elder.wechatvideo.keepalive.AutostartPrefs
import com.elder.wechatvideo.keepalive.KeepAliveService

/**
 * 权限与保活相关检测、跳转工具。
 *
 * 提供无障碍服务、电池优化豁免、通知权限等状态的检测，
 * 以及跳转到对应系统设置页的快捷方法。
 */
object PermissionUtils {

    /** 本应用无障碍服务的完整类名（与 AndroidManifest 中注册一致） */
    private const val ACCESSIBILITY_SERVICE_CLASS_NAME =
        "com.elder.wechatvideo.service.WeChatAccessibilityService"

    /* ===================== 状态检测 ===================== */

    /**
     * 检查指定无障碍服务是否已在系统设置中开启。
     *
     * @param context 上下文
     * @param serviceClass 无障碍服务的 Class，例如
     *        `WeChatAccessibilityService::class.java`
     * @return true 表示服务已开启
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        return isAccessibilityServiceEnabledByClassName(context, serviceClass.name)
    }

    /**
     * 检查本应用无障碍服务是否已开启（按类名匹配，无需依赖具体 Class）。
     *
     * @param context 上下文
     * @param serviceClassName 无障碍服务的完整类名
     * @return true 表示服务已开启
     */
    private fun isAccessibilityServiceEnabledByClassName(
        context: Context,
        serviceClassName: String = ACCESSIBILITY_SERVICE_CLASS_NAME
    ): Boolean {
        val expectedComponent = ComponentName(context.packageName, serviceClassName)
            .flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // 系统已开启的无障碍服务以 ":" 分隔，每项为 "packageName/className" 形式
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
        for (entry in splitter) {
            if (entry.equals(expectedComponent, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * 检查本应用是否已加入电池优化白名单（即已豁免电池优化）。
     *
     * @return true 表示已豁免，不会被系统限制后台
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 检查通知权限是否已授予。
     *
     * Android 13（API 33）及以上需运行时申请 POST_NOTIFICATIONS 权限；
     * 低于 33 时默认已授予（安装即有），返回 true。
     *
     * @return true 表示已授予通知权限
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /* ===================== 设置页跳转 ===================== */

    /**
     * 跳转到系统的无障碍服务设置页，引导用户开启服务。
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivitySafely(context, intent)
    }

    /**
     * 请求将本应用加入电池优化白名单。
     *
     * 需在 AndroidManifest 中声明
     * `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限。
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivitySafely(context, intent)
    }

    /**
     * 跳转到本应用的通知设置页。
     *
     * Android 8.0+ 使用应用通知设置页；低版本回退到应用详情页。
     */
    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivitySafely(context, intent)
    }

    /**
     * 跳转到本应用的应用详情设置页（权限、通知、电池等入口均在此）。
     */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivitySafely(context, intent)
    }

    /* ===================== 悬浮窗权限 ===================== */

    /**
     * 检查是否拥有「显示在其他应用上层」（SYSTEM_ALERT_WINDOW）权限。
     *
     * Android 6.0+ 需用户手动授予；低于 6.0 默认已授予。
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 跳转到系统的「显示在其他应用上层」权限设置页。
     */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivitySafely(context, intent)
        }
    }

    /* ===================== 桌面快捷方式权限 ===================== */

    /**
     * 检查是否支持创建桌面快捷方式（Pinned Shortcut）。
     *
     * Android 8.0+ 使用 ShortcutManagerCompat 检测默认启动器是否支持
     * requestPinShortcut；部分国产 ROM（MIUI/EMUI/ColorOS）可能需要额外的
     * 「桌面快捷方式」权限，此处仅检测基本支持能力。
     *
     * @return true 表示设备支持创建桌面快捷方式
     */
    fun isShortcutSupported(context: Context): Boolean {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    }

    /**
     * 跳转到应用的快捷方式设置页。
     *
     * Android 8.0+ 尝试打开快捷方式设置；无法直接打开时回退到应用详情页，
     * 引导用户在应用信息中手动授予「创建桌面快捷方式」权限。
     */
    fun openShortcutSettings(context: Context) {
        // 尝试打开快捷方式设置页（部分 ROM 支持）
        val shortcutIntent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS").apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivitySafely(context, shortcutIntent)
    }

    /* ===================== 综合状态 ===================== */

    /**
     * 获取当前各项保活权限的总体状态。
     *
     * @param context 上下文
     * @param serviceClass 无障碍服务的 Class，默认使用本应用的
     *        [WeChatAccessibilityService]
     * @return 封装各项状态的 [KeepAliveStatus]
     */
    @JvmOverloads
    fun getKeepAliveStatus(
        context: Context,
        serviceClass: Class<*>? = null
    ): KeepAliveStatus {
        val accessibilityEnabled = if (serviceClass != null) {
            isAccessibilityServiceEnabled(context, serviceClass)
        } else {
            isAccessibilityServiceEnabledByClassName(context)
        }
        return KeepAliveStatus(
            accessibilityEnabled = accessibilityEnabled,
            // B19：isIgnoringBatteryOptimizations 为 true 时表示已豁免电池优化，
            // 语义正确的字段名为 batteryExempt（原 batteryOptimized 命名相反）。
            batteryExempt = isIgnoringBatteryOptimizations(context),
            notificationGranted = isNotificationPermissionGranted(context),
            // 开机自启读取用户开关（SharedPreferences），AutostartPrefs 默认 true 即开机即保活。
            autostartEnabled = AutostartPrefs.isAutostartEnabled(context),
            // B19：后台运行反映保活前台服务是否真实在运行，不再硬编码 true。
            backgroundEnabled = isKeepAliveServiceRunning(context),
            shortcutPermissionGranted = isShortcutSupported(context)
        )
    }

    /**
     * 检测本应用的保活前台服务（[KeepAliveService]）当前是否正在运行。
     *
     * 作为「后台运行是否允许」的真实代理信号：服务存活即代表应用可在后台保持运行。
     *
     * 实现方式：直接读取 [KeepAliveService.isRunning] 静态标志位（onCreate 置 true、
     * onDestroy 置 false），替代已废弃的 [ActivityManager.getRunningServices]。
     *
     * @return true 表示保活服务正在运行
     */
    fun isKeepAliveServiceRunning(context: Context): Boolean {
        return KeepAliveService.isRunning
    }

    /* ===================== 内部工具 ===================== */

    /**
     * 安全地启动一个 Activity，捕获可能出现的 [android.content.ActivityNotFoundException]，
     * 避免在定制 ROM 上因缺少对应设置页而崩溃。
     */
    private fun startActivitySafely(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }
    }
}
