package com.elder.wechatvideo.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * 微信版本检测器。
 * 校准后记录微信版本号，升级后主动提示重新校准。
 */
object WeChatVersionDetector {

    private const val PREFS = "wechat_version_prefs"
    private const val KEY_VERSION = "wechat_version_at_calibration"

    fun getVersion(context: Context): String? {
        return try {
            context.packageManager.getPackageInfo(WeChatConstants.WECHAT_PACKAGE, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun isInstalled(context: Context): Boolean = getVersion(context) != null

    fun saveCalibratedVersion(context: Context) {
        val v = getVersion(context) ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_VERSION, v).apply()
    }

    fun getCalibratedVersion(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_VERSION, null)
    }

    /** 微信是否在校准后升级过 */
    fun isUpgraded(context: Context): Boolean {
        val calibrated = getCalibratedVersion(context) ?: return false
        val current = getVersion(context) ?: return false
        return calibrated != current
    }

    /** 获取升级提示文案，无需提示返回 null */
    fun getUpgradeWarning(context: Context): String? {
        if (!PositionConfig.isCalibrated(context)) return null
        val current = getVersion(context) ?: return "微信未安装，无法使用视频通话"
        val calibrated = getCalibratedVersion(context) ?: return null
        if (current != calibrated) {
            return "微信已从 $calibrated 升级到 $current，按键位置可能变化，建议重新校准"
        }
        return null
    }
}
