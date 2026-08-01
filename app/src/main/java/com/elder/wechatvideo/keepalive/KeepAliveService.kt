package com.elder.wechatvideo.keepalive

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elder.wechatvideo.ElderWeChatApp
import com.elder.wechatvideo.MainActivity
import com.elder.wechatvideo.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * 保活前台服务。
 *
 * 通过常驻通知将自身提升为前台服务，降低被系统回收的概率，
 * 确保无障碍服务与一键视频通话功能随时可用。
 *
 * 功能说明：
 * - 启动后立即显示一条常驻通知（低优先级，不打扰用户）。
 * - 通知点击后跳转回 [MainActivity]，方便老人回到应用主界面。
 * - 服务被系统杀死后会自动重建（[Service.START_STICKY]）。
 * - 用户从最近任务划掉应用时，[onTaskRemoved] 会主动重启服务，保活不中断。
 * - 通过 [startService] / [stopService] 便捷控制服务的启停。
 *
 * 前台服务类型为 [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE]，
 * 在 AndroidManifest 中已声明 `foregroundServiceType="specialUse"` 及对应的
 * `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 属性。
 */
@AndroidEntryPoint
class KeepAliveService : Service() {

    companion object {
        /** 前台通知 ID（固定值，用于标识保活通知） */
        private const val NOTIFICATION_ID = 1001

        /** PendingIntent 请求码 */
        private const val REQUEST_CODE = 0

        /**
         * 服务运行状态标志：onCreate 时置 true，onDestroy 时置 false。
         * 替代已废弃的 [android.app.ActivityManager.getRunningServices]，
         * 供 [com.elder.wechatvideo.util.PermissionUtils.isKeepAliveServiceRunning] 直接读取。
         */
        @Volatile
        @JvmStatic
        var isRunning: Boolean = false
            private set

        /**
         * 启动保活前台服务。
         *
         * 内部使用 [ContextCompat.startForegroundService]，兼容 Android 8.0+。
         * 服务启动后会自动调用 [startForeground] 显示常驻通知。
         *
         * 注意：Android 12+（API 31+）对后台启动前台服务有限制，调用方需处于前台
         * 或满足豁免条件（如 [Intent.ACTION_BOOT_COMPLETED] 广播）。
         *
         * @param context 上下文
         */
        fun startService(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * 停止保活前台服务。
         *
         * 服务停止后常驻通知会自动移除。
         *
         * @param context 上下文
         */
        fun stopService(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        // 服务创建时立即启动前台通知，满足 startForegroundService 的 5 秒时限要求
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知已在 onCreate 中启动，此处无需重复操作
        // START_STICKY：服务被系统杀死后会自动重建（intent 为 null）
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    /**
     * 用户从最近任务列表划掉应用时回调。
     *
     * 此处主动重新启动 [KeepAliveService] 自身，使保活前台服务在被销毁后重建，
     * 保证后台守护不中断。onTaskRemoved 属于后台启动前台服务的豁免场景，且本服务
     * 即为前台服务，使用 [ContextCompat.startForegroundService] 兼容各版本并确保合规。
     * [runCatching] 兜底极端情况下（如服务已被停止）启动失败，避免抛出异常。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(this, KeepAliveService::class.java)
        runCatching {
            ContextCompat.startForegroundService(this, restartIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * 构建并启动前台通知。
     *
     * Android 14（API 34）及以上必须在 [startForeground] 中显式传入前台服务类型，
     * 否则会抛出 `MissingForegroundServiceTypeException`。
     */
    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * 构建保活常驻通知。
     *
     * 通知使用 [ElderWeChatApp.CHANNEL_ID] 渠道（IMPORTANCE_LOW，静默展示），
     * 点击后通过 PendingIntent 跳转回 [MainActivity]。
     *
     * @return 构建完成的 [Notification] 对象
     */
    private fun buildNotification(): Notification {
        // 点击通知后跳回主界面
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, ElderWeChatApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 常驻通知，不可滑动清除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 双保险：显式设置 FLAG_NO_CLEAR 与 FLAG_ONGOING_EVENT，
        // 确保通知栏不可被用户左右滑动清除（部分 ROM 对 setOngoing 处理不一致）
        notification.flags = notification.flags or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_ONGOING_EVENT

        return notification
    }
}
