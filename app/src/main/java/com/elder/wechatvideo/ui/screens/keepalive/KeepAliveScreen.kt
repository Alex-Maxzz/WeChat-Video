package com.elder.wechatvideo.ui.screens.keepalive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elder.wechatvideo.R
import com.elder.wechatvideo.util.PositionConfig

/**
 * 保活设置页。
 *
 * 展示 6 项保活权限状态与进度，每项可跳转对应系统设置页。
 * 每当页面回到前台（[Lifecycle.Event.ON_RESUME]）自动刷新状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepAliveScreen(
    onNavigateToCalibration: () -> Unit = {},
    viewModel: KeepAliveViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val autostartEnabled by viewModel.autostartEnabled.collectAsStateWithLifecycle()

    // 页面回到前台时刷新状态（用户可能刚从系统设置开启某项）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keepalive_title)) }
            )
        }
    ) { innerPadding ->
        KeepAliveContent(
            completedCount = status.completedCount,
            totalCount = status.totalCount,
            items = buildSettingItems(status, viewModel, autostartEnabled),
            onNavigateToCalibration = onNavigateToCalibration,
            contentPadding = innerPadding
        )
    }
}

/**
 * 单个保活设置项的展示数据。
 *
 * @param switch 可选的开关控件状态（checked + 变更回调）。为空表示该项仅提供「去设置」按钮，
 *        无内部开关。目前仅「开机自启」项带开关（B18）。
 */
private data class SettingItem(
    val enabled: Boolean,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val switch: Pair<Boolean, (Boolean) -> Unit>? = null
)

/**
 * 根据当前状态构造 6 项设置列表。
 */
private fun buildSettingItems(
    status: com.elder.wechatvideo.util.KeepAliveStatus,
    viewModel: KeepAliveViewModel,
    autostartEnabled: Boolean
): List<SettingItem> = listOf(
    SettingItem(
        enabled = status.accessibilityEnabled,
        titleRes = R.string.ka_accessibility,
        descRes = R.string.ka_accessibility_desc,
        icon = Icons.Filled.AccessibilityNew,
        onClick = viewModel::openAccessibilitySettings
    ),
    SettingItem(
        enabled = status.autostartEnabled,
        titleRes = R.string.ka_autostart,
        descRes = R.string.ka_autostart_desc,
        icon = Icons.Filled.PowerSettingsNew,
        onClick = viewModel::openAutostartSettings,
        // B18：开机自启用户开关，直接切换 SharedPreferences 并联动保活服务启停
        switch = autostartEnabled to viewModel::setAutostartEnabled
    ),
    SettingItem(
        enabled = status.batteryExempt,
        titleRes = R.string.ka_battery,
        descRes = R.string.ka_battery_desc,
        icon = Icons.Filled.BatteryStd,
        onClick = viewModel::openBatterySettings
    ),
    SettingItem(
        enabled = status.notificationGranted,
        titleRes = R.string.ka_notification,
        descRes = R.string.ka_notification_desc,
        icon = Icons.Filled.Notifications,
        onClick = viewModel::openNotificationSettings
    ),
    SettingItem(
        enabled = status.backgroundEnabled,
        titleRes = R.string.ka_background,
        descRes = R.string.ka_background_desc,
        icon = Icons.Filled.PlayCircle,
        onClick = viewModel::openBackgroundSettings
    ),
    SettingItem(
        enabled = status.shortcutPermissionGranted,
        titleRes = R.string.ka_shortcut,
        descRes = R.string.ka_shortcut_desc,
        icon = Icons.Filled.AppShortcut,
        onClick = viewModel::openShortcutSettings
    )
)

/**
 * 保活页内容主体。
 */
@Composable
private fun KeepAliveContent(
    completedCount: Int,
    totalCount: Int,
    items: List<SettingItem>,
    onNavigateToCalibration: () -> Unit,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 副标题
        Text(
            text = stringResource(R.string.keepalive_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 进度卡片
        ProgressCard(
            completedCount = completedCount,
            totalCount = totalCount
        )

        // 按键校准卡片
        CalibrationEntryCard(onClick = onNavigateToCalibration)

        // 5 项设置卡片
        items.forEach { item ->
            SettingCard(item)
        }

        // 厂商自启动引导卡片（针对国产 ROM 引导开启自启动与电池优化）
        VendorAutostartCard()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 进度摘要卡片（v2：环形进度 + 百分比 + 说明文字）。
 */
@Composable
private fun ProgressCard(
    completedCount: Int,
    totalCount: Int
) {
    val progress = if (totalCount <= 0) 0f else completedCount.toFloat() / totalCount.toFloat()
    val allDone = completedCount == totalCount
    val ringColor = if (allDone) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // 环形进度
            val trackColor = MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier.size(58.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(58.dp)) {
                    val strokeWidth = 6.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                    // 背景环
                    drawCircle(
                        color = trackColor,
                        radius = radius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
                    )
                    // 进度环
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            center.x - radius, center.y - radius
                        ),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
                Text(
                    text = "$completedCount/$totalCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allDone) "全部就绪" else "保活权限配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (allDone) "所有权限已开启，服务稳定运行中"
                           else "还有 ${totalCount - completedCount} 项需要开启",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 百分比
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ringColor
            )
        }
    }
}

/**
 * 单个设置项卡片：图标 + 标题 + 描述 + 状态徽标 + 去设置按钮。
 */
@Composable
private fun SettingCard(item: SettingItem) {
    val statusColor = if (item.enabled) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(item.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(item.descRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.switch != null) {
                    Switch(
                        checked = item.switch.first,
                        onCheckedChange = item.switch.second
                    )
                } else {
                    StatusBadge(enabled = item.enabled)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = item.onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.go_settings),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * 按键校准入口卡片（v2：primary 渐变背景，整卡可点击）。
 */
@Composable
private fun CalibrationEntryCard(onClick: () -> Unit) {
    val context = LocalContext.current
    val isCalibrated = PositionConfig.isCalibrated(context)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ka_calibration),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (isCalibrated) "已校准 · 点击重新校准"
                           else stringResource(R.string.ka_calibration_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 状态徽标：已开启（绿色）/ 未开启（橙色）。
 */
@Composable
private fun StatusBadge(enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.tertiary
    val text = stringResource(if (enabled) R.string.enabled else R.string.not_enabled)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 厂商自启动引导卡片。
 *
 * 根据 [Build.MANUFACTURER] 判断设备厂商，给出针对性的「自启动管理」「电池优化」入口
 * 文案，点击按钮尝试跳转对应系统设置；跳转失败则 Toast 提示手动前往。文案面向老人/
 * 家属，简洁友好。遵循现有卡片的 Compose 代码风格与主题。
 */
@Composable
private fun VendorAutostartCard() {
    val context = LocalContext.current
    // 厂商信息在设备生命周期内不变，使用 remember 避免重复计算
    val vendorInfo = remember { VendorAutoStart.detect() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ka_vendor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = vendorInfo.guidance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    // 依次尝试厂商自启动入口 → 电池优化 → 应用详情，全部失败则 Toast 提示
                    val opened = VendorAutoStart.tryOpen(context)
                    if (!opened) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.ka_vendor_toast),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.ka_vendor_button),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * 厂商自启动引导工具。
 *
 * 通过 [Build.MANUFACTURER]（大小写不敏感）判断厂商，返回面向老人的引导文案，
 * 并提供尝试跳转系统设置的方法。厂商专属 Intent 失败时自动回退到通用入口。
 */
private object VendorAutoStart {

    /** 厂商引导信息：展示名 + 针对性提示文案 */
    data class VendorInfo(
        val displayName: String,
        val guidance: String
    )

    /**
     * 检测当前设备厂商并返回针对性引导文案。
     */
    fun detect(): VendorInfo {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                VendorInfo("小米手机", "请进入「手机管家 - 应用管理 - 自启动」，开启「一键视频」自启动，并在电池优化中设为无限制。")
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                VendorInfo("华为/荣耀手机", "请进入「手机管家 - 应用启动管理」，将「一键视频」改为手动管理，允许自启动与后台活动。")
            manufacturer.contains("oppo") ->
                VendorInfo("OPPO手机", "请进入「手机管家 - 自启动管理」，开启「一键视频」自启动，并在电池中允许后台运行。")
            manufacturer.contains("vivo") ->
                VendorInfo("vivo手机", "请进入「i管家 - 应用管理 - 权限管理 - 自启动」，允许「一键视频」自启动与后台弹出。")
            manufacturer.contains("samsung") ->
                VendorInfo("三星手机", "请在「设置 - 电池 - 后台使用限制」中将「一键视频」设为不受限制，并在应用启动中允许自启动。")
            else ->
                VendorInfo("安卓手机", "请在系统「设置 - 应用管理」中，将「一键视频」的自启动与后台运行权限开启。")
        }
    }

    /**
     * 尝试打开厂商自启动/电池优化设置页。
     *
     * 依次尝试：厂商专属入口 → 电池优化豁免页 → 应用详情页，首个成功即返回 true；
     * 全部失败（如缺少对应 Activity）返回 false，由调用方 Toast 兑底。
     */
    fun tryOpen(context: Context): Boolean {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val candidates = buildList {
            // 厂商专属自启动入口（最佳尝试，失败则跳过）
            addAll(vendorSpecificIntents(manufacturer))
            // 通用电池优化入口（需 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限，已声明）
            add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            // 应用详情页（最可靠的兑底入口）
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        return candidates.any { intent ->
            runCatching { context.startActivity(intent) }.isSuccess
        }
    }

    /**
     * 各厂商专属自启动管理 Intent（经验值，ROM 版本不同可能失效）。
     * 失败时由 [tryOpen] 自动回退到通用入口。
     */
    private fun vendorSpecificIntents(manufacturer: String): List<Intent> = when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> listOf(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        manufacturer.contains("oppo") -> listOf(
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent().apply {
                component = ComponentName(
                    "com.color.safecenter",
                    "com.color.safecenter.permission.startup.StartupActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        manufacturer.contains("vivo") -> listOf(
            Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.suptauguryadd.SuptAuguryAddActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        else -> emptyList()
    }
}
