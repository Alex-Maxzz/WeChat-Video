package com.elder.wechatvideo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.elder.wechatvideo.keepalive.KeepAliveService
import com.elder.wechatvideo.keepalive.AutostartPrefs
import com.elder.wechatvideo.ui.navigation.AppNavigation
import com.elder.wechatvideo.ui.navigation.Routes
import com.elder.wechatvideo.ui.theme.ElderWeChatVideoTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用主界面。
 *
 * 使用 Hilt 注入（[@AndroidEntryPoint]），启用时令的 edgeToEdge 显示，
 * 并承载 Compose 导航与主题。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 修复冷启动缺口：App 前台时确保保活服务运行（Android 12+ 合规：前台启动 FGS）
        if (AutostartPrefs.isAutostartEnabled(this) && !KeepAliveService.isRunning) {
            KeepAliveService.startService(this)
        }

        // 从 CallBridgeActivity 跳转时，自动导航到校准页
        val navigateToCalibration = intent?.getBooleanExtra("navigate_to_calibration", false) ?: false
        val initialRoute = if (navigateToCalibration) Routes.CALIBRATION else null

        setContent {
            ElderWeChatVideoTheme {
                AppNavigation(initialRoute = initialRoute)
            }
        }
    }
}
