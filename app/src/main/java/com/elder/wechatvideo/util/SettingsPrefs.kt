package com.elder.wechatvideo.util

import android.content.Context

/**
 * 设置页偏好管理。
 *
 * 管理 OCR 开关、严格模式、主题模式等用户偏好。
 */
object SettingsPrefs {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_OCR_ENABLED = "ocr_enabled"
    private const val KEY_OCR_STRICT = "ocr_strict_mode"
    private const val KEY_THEME_MODE = "theme_mode" // "system" | "dark" | "light"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** OCR 智能识别总开关（默认开启） */
    fun isOcrEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_OCR_ENABLED, true)

    fun setOcrEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OCR_ENABLED, enabled).apply()
    }

    /** OCR 严格模式：识别不到就停止，不回退到坐标盲戳（默认关闭） */
    fun isOcrStrictMode(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_OCR_STRICT, false)

    fun setOcrStrictMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OCR_STRICT, enabled).apply()
    }

    /** 主题模式："system"（默认）| "dark" | "light" */
    fun getThemeMode(context: Context): String =
        getPrefs(context).getString(KEY_THEME_MODE, "system") ?: "system"

    fun setThemeMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
    }
}
