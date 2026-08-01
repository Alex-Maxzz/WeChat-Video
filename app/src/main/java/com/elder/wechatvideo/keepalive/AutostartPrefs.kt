package com.elder.wechatvideo.keepalive

import android.content.Context
import android.content.SharedPreferences

/**
 * 开机自启用户开关的持久化存储。
 *
 * 默认值为 true：即设备重启后自动启动 [KeepAliveService]，确保老人无需手动设置
 * 即可享受开机即保活的体验。用户可在保活设置页主动关闭此开关。
 *
 * [BootReceiver] 在收到 BOOT_COMPLETED 时读取此标志，仅当为 true 才启动服务。
 * [com.elder.wechatvideo.ui.screens.keepalive.KeepAliveViewModel] 负责写入此标志，
 * 并在切换时同步启动/停止保活服务。
 */
object AutostartPrefs {

    private const val PREFS_NAME = "autostart_prefs"
    private const val KEY_AUTOSTART_ENABLED = "autostart_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 是否允许开机自启。
     * @param context 上下文
     * @return true 表示开机自启已开启（默认 true，确保老人开机即保活）
     */
    fun isAutostartEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTOSTART_ENABLED, true)
    }

    /**
     * 设置开机自启开关。
     * @param context 上下文
     * @param enabled true 开启，false 关闭
     */
    fun setAutostartEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTOSTART_ENABLED, enabled).apply()
    }
}
