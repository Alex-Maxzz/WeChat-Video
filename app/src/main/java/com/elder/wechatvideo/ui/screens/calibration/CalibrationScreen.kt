package com.elder.wechatvideo.ui.screens.calibration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elder.wechatvideo.R
import com.elder.wechatvideo.service.CalibrationOverlayService
import com.elder.wechatvideo.service.WeChatAccessibilityService
import com.elder.wechatvideo.util.PermissionUtils
import com.elder.wechatvideo.util.PositionConfig
import com.elder.wechatvideo.util.WeChatConstants

/**
 * 按键位置校准页面
 *
 * 引导用户完成微信按键位置的手动校准：
 * 1. 检查前置条件（悬浮窗权限、无障碍服务、微信安装）
 * 2. 点击「开始校准」打开微信并启动悬浮窗校准服务
 * 3. 校准完成后坐标持久化，后续拨号自动使用
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isCalibrated by remember { mutableStateOf(PositionConfig.isCalibrated(context)) }
    var hasOverlayPermission by remember { mutableStateOf(PermissionUtils.canDrawOverlays(context)) }
    var hasAccessibility by remember {
        mutableStateOf(
            WeChatAccessibilityService.isConnected ||
            PermissionUtils.isAccessibilityServiceEnabled(context, WeChatAccessibilityService::class.java)
        )
    }
    var isWeChatInstalled by remember { mutableStateOf(isWeChatInstalled(context)) }

    // 页面回到前台时刷新状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isCalibrated = PositionConfig.isCalibrated(context)
                hasOverlayPermission = PermissionUtils.canDrawOverlays(context)
                hasAccessibility = WeChatAccessibilityService.isConnected ||
                    PermissionUtils.isAccessibilityServiceEnabled(
                        context, WeChatAccessibilityService::class.java
                    )
                isWeChatInstalled = isWeChatInstalled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allPrerequisitesMet = hasOverlayPermission && hasAccessibility && isWeChatInstalled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calibration_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 校准状态卡片
            StatusCard(isCalibrated = isCalibrated)

            // 前置条件
            Text(
                text = "前置条件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            PrerequisiteItem(
                icon = Icons.Filled.CheckCircle,
                title = stringResource(R.string.calibration_overlay_permission),
                desc = stringResource(R.string.calibration_overlay_desc),
                isEnabled = hasOverlayPermission,
                actionText = stringResource(R.string.go_settings),
                onAction = {
                    PermissionUtils.openOverlaySettings(context)
                }
            )

            PrerequisiteItem(
                icon = Icons.Filled.CheckCircle,
                title = stringResource(R.string.calibration_accessibility),
                desc = stringResource(R.string.calibration_accessibility_desc),
                isEnabled = hasAccessibility,
                actionText = stringResource(R.string.go_settings),
                onAction = {
                    PermissionUtils.openAccessibilitySettings(context)
                }
            )

            PrerequisiteItem(
                icon = Icons.Filled.CheckCircle,
                title = "微信",
                desc = if (isWeChatInstalled) stringResource(R.string.calibration_wechat_installed)
                       else stringResource(R.string.calibration_wechat_not_installed),
                isEnabled = isWeChatInstalled,
                actionText = null,
                onAction = {}
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 开始/重新校准按钮
            Button(
                onClick = { startCalibration(context, isCalibrated) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                enabled = allPrerequisitesMet
            ) {
                Text(
                    text = stringResource(
                        if (isCalibrated) R.string.calibration_recalibrate
                        else R.string.calibration_start
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // 校准说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.calibration_instructions_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.calibration_instructions_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/* ===================== 组件 ===================== */

@Composable
private fun StatusCard(isCalibrated: Boolean) {
    val color = if (isCalibrated) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.tertiary
    val icon = if (isCalibrated) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val text = stringResource(
        if (isCalibrated) R.string.calibration_status_done
        else R.string.calibration_status_not_done
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun PrerequisiteItem(
    icon: ImageVector,
    title: String,
    desc: String,
    isEnabled: Boolean,
    actionText: String?,
    onAction: () -> Unit
) {
    val color = if (isEnabled) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (actionText != null && !isEnabled) {
                OutlinedButton(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
    }
}

/* ===================== 工具函数 ===================== */

private fun isWeChatInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo(WeChatConstants.WECHAT_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

/**
 * 启动校准：清除旧数据 → 启动悬浮窗服务 → 打开微信
 */
private fun startCalibration(context: Context, isRecalibrate: Boolean) {
    if (isRecalibrate) {
        PositionConfig.clearCalibration(context)
    }

    // 先启动悬浮窗校准服务（前台服务，防止切到微信后被系统回收）
    val serviceIntent = Intent(context, CalibrationOverlayService::class.java)
    androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)

    // 再打开微信
    val wechatIntent = Intent().apply {
        component = ComponentName(
            WeChatConstants.WECHAT_PACKAGE,
            WeChatConstants.WECHAT_LAUNCHER
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(wechatIntent) }
}
