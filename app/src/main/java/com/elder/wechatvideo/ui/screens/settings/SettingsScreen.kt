package com.elder.wechatvideo.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elder.wechatvideo.util.PositionConfig
import com.elder.wechatvideo.util.SettingsPrefs

/**
 * 设置页面（底部第三个 Tab）。
 *
 * 包含：拨号设置（自动拨号/OCR）、浮窗设置、外观设置、关于入口。
 */
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current

    var autoDial by remember { mutableStateOf(PositionConfig.isAutoDialEnabled(context)) }
    var ocrEnabled by remember { mutableStateOf(SettingsPrefs.isOcrEnabled(context)) }
    var ocrStrict by remember { mutableStateOf(SettingsPrefs.isOcrStrictMode(context)) }
    var overlayLocked by remember { mutableStateOf(SettingsPrefs.isOverlayLocked(context)) }
    var themeMode by remember { mutableStateOf(SettingsPrefs.getThemeMode(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(20.dp))

        // ===== 拨号设置 =====
        SectionTitle("拨号设置")
        Spacer(modifier = Modifier.height(8.dp))

        SettingsCard {
            SwitchItem(
                title = "自动拨打视频通话",
                subtitle = if (autoDial) "全自动搜索并拨打" else "只进入聊天，手动拨打",
                checked = autoDial,
                onCheckedChange = {
                    autoDial = it
                    PositionConfig.setAutoDialEnabled(context, it)
                }
            )
            Divider()
            SwitchItem(
                title = "OCR 智能识别",
                subtitle = if (ocrEnabled) "节点失败时截图识别联系人" else "已关闭，使用坐标定位",
                checked = ocrEnabled,
                onCheckedChange = {
                    ocrEnabled = it
                    SettingsPrefs.setOcrEnabled(context, it)
                }
            )
            if (ocrEnabled) {
                Divider()
                SwitchItem(
                    title = "OCR 严格模式",
                    subtitle = if (ocrStrict) "识别不到就停止，不盲戳" else "识别不到仍尝试坐标",
                    checked = ocrStrict,
                    onCheckedChange = {
                        ocrStrict = it
                        SettingsPrefs.setOcrStrictMode(context, it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 浮窗设置 =====
        SectionTitle("浮窗设置")
        Spacer(modifier = Modifier.height(8.dp))

        SettingsCard {
            SwitchItem(
                title = "拨号进度浮窗位置固定",
                subtitle = if (overlayLocked) "固定不动" else "可拖动，位置记忆",
                checked = overlayLocked,
                onCheckedChange = {
                    overlayLocked = it
                    SettingsPrefs.setOverlayLocked(context, it)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 外观 =====
        SectionTitle("外观")
        Spacer(modifier = Modifier.height(8.dp))

        SettingsCard {
            ThemeSelector(
                currentMode = themeMode,
                onModeChange = { mode ->
                    themeMode = mode
                    SettingsPrefs.setThemeMode(context, mode)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 其他 =====
        SectionTitle("其他")
        Spacer(modifier = Modifier.height(8.dp))

        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAbout() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "关于",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "版本信息、更新日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
private fun ThemeSelector(
    currentMode: String,
    onModeChange: (String) -> Unit
) {
    val options = listOf("system" to "跟随系统", "dark" to "深色模式", "light" to "浅色模式")
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "主题模式",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { (mode, label) ->
                val selected = currentMode == mode
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                        .clickable { onModeChange(mode) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .padding(0.dp)
    )
}
