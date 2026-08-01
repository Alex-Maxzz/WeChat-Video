package com.elder.wechatvideo.ui.screens.keepalive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elder.wechatvideo.keepalive.AutostartPrefs
import com.elder.wechatvideo.keepalive.KeepAliveService
import com.elder.wechatvideo.util.KeepAliveStatus
import com.elder.wechatvideo.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 保活设置页 ViewModel。
 *
 * 通过 [PermissionUtils] 读取各项保活权限状态，并提供跳转到对应系统设置页的方法。
 *
 * 说明：[PermissionUtils.getKeepAliveStatus] 默认按本应用无障碍服务的完整类名
 * 检测，无需在此显式引用 Service Class。
 *
 * @param context 应用上下文
 */
@HiltViewModel
class KeepAliveViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _status = MutableStateFlow(KeepAliveStatus())
    val status: StateFlow<KeepAliveStatus> = _status.asStateFlow()

    /** 开机自启开关的实时状态（用于保活页开关即时反馈，避免等待 refresh） */
    private val _autostartEnabled =
        MutableStateFlow(AutostartPrefs.isAutostartEnabled(context))
    val autostartEnabled: StateFlow<Boolean> = _autostartEnabled.asStateFlow()

    init {
        refresh()
    }

    /**
     * 重新读取各项保活权限状态。
     *
     * 在 IO 线程执行以避免阻塞主线程（部分系统查询可能较慢）。
     */
    fun refresh() {
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) {
                PermissionUtils.getKeepAliveStatus(context)
            }
            _status.value = latest
        }
    }

    /* ===================== 跳转系统设置 ===================== */

    /** 跳转到无障碍服务设置页 */
    fun openAccessibilitySettings() {
        PermissionUtils.openAccessibilitySettings(context)
    }

    /** 跳转到电池优化豁免申请页 */
    fun openBatterySettings() {
        PermissionUtils.requestIgnoreBatteryOptimizations(context)
    }

    /** 跳转到通知权限设置页 */
    fun openNotificationSettings() {
        PermissionUtils.openNotificationSettings(context)
    }

    /**
     * 跳转到开机自启设置页。
     *
     * 国产 ROM 没有统一的标准入口，回退到应用详情设置页。
     */
    fun openAutostartSettings() {
        PermissionUtils.openAppDetailsSettings(context)
    }

    /**
     * 切换开机自启开关（B18）。
     *
     * 写入用户偏好，并同步启动/停止保活前台服务：
     * - 开启：立即启动 [KeepAliveService]，确保后台保活；
     * - 关闭：停止 [KeepAliveService]，不再常驻后台。
     * 同时刷新总体状态，使进度与各项指示保持真实。
     *
     * @param enabled 用户选择的新状态
     */
    fun setAutostartEnabled(enabled: Boolean) {
        AutostartPrefs.setAutostartEnabled(context, enabled)
        _autostartEnabled.value = enabled
        if (enabled) {
            KeepAliveService.startService(context)
        } else {
            KeepAliveService.stopService(context)
        }
        refresh()
    }

    /**
     * 跳转到后台运行 / 应用详情设置页。
     */
    fun openBackgroundSettings() {
        PermissionUtils.openAppDetailsSettings(context)
    }

    /**
     * 跳转到桌面快捷方式权限设置页。
     */
    fun openShortcutSettings() {
        PermissionUtils.openShortcutSettings(context)
    }
}
