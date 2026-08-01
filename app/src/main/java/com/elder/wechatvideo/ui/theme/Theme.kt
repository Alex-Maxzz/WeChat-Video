package com.elder.wechatvideo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.elder.wechatvideo.util.SettingsPrefs

/* ============================================================
   v2 设计系统主题（来源：UI设计预览/设计系统预览v2.html）
   - 关闭动态取色（Material You），使用品牌固定色
   - 深色为默认优先主题
   ============================================================ */

private val DarkColorScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = TealSuccess,
    onSecondary = DarkOnPrimary,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = CoralTertiary,
    onTertiary = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = TealSuccessDark,
    onSecondary = LightSurface,
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = CoralTertiaryLight,
    onTertiary = LightSurface,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

@Composable
fun ElderWeChatVideoTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (SettingsPrefs.getThemeMode(context)) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
