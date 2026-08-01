package com.elder.wechatvideo.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启接收器。
 *
 * 监听系统开机完成广播，在设备启动后自动拉起保活前台服务，
 * 确保重启手机后一键视频功能依然可用。
 *
 * 监听的广播：
 * - [Intent.ACTION_BOOT_COMPLETED]：设备开机并解锁后发送，最常用的开机广播。
 * - [Intent.ACTION_LOCKED_BOOT_COMPLETED]：设备开机后在直接启动（Direct Boot）
 *   模式下发送，此时设备已启动但尚未解锁。
 *
 * 注意：
 * - Android 12+（API 31+）限制后台启动前台服务，但 BOOT_COMPLETED 属于豁免广播，
 *   因此从接收器中启动前台服务是允许的。
 * - 应用进程未启动时，接收到广播会先触发 [com.elder.wechatvideo.ElderWeChatApp.onCreate]
 *   （创建通知渠道），再执行 [onReceive]，因此服务启动时通知渠道已就绪。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                // B18：尊重用户开机自启开关，仅当用户显式开启时才拉起保活前台服务。
                // 默认（未开启）直接 return，避免未经用户同意就在开机后常驻后台。
                if (!AutostartPrefs.isAutostartEnabled(context)) {
                    return
                }
                KeepAliveService.startService(context)
            }
        }
    }
}
