package com.elder.wechatvideo.ui.theme

import androidx.compose.ui.graphics.Color

/* ============================================================
   v2 设计系统色值（来源：UI设计预览/设计系统预览v2.html）
   深色为默认优先主题，浅色为同源变体
   ============================================================ */

// Primary: #9B8CFF（薰衣草紫）
val PurplePrimary = Color(0xFF9B8CFF)
val PurplePrimaryDark = Color(0xFF7C6BFF)
val PurpleLight = Color(0xFF9B8CFF)
val PurpleContainer = Color(0xFFE8E4FF)
val PurpleContainerDark = Color(0xFF3A2F6B)
val OnPrimaryContainerDark = Color(0xFFE8E4FF)
val OnPrimaryContainerLight = Color(0xFF2A1F55)

// Secondary: #10B59A（青绿）
val TealSuccess = Color(0xFF10B59A)
val TealSuccessDark = Color(0xFF0E9C84)
val TealContainer = Color(0xFFA0F0D8)
val TealContainerDark = Color(0xFF00382E)
val OnSecondaryContainerDark = Color(0xFFA0F0D8)
val OnSecondaryContainerLight = Color(0xFF00382E)

// Tertiary: #FFB4A2（暖珊瑚）/ 浅色 #D85A45
val CoralTertiary = Color(0xFFFFB4A2)
val CoralTertiaryLight = Color(0xFFD85A45)

// 深色主题表面
val DarkBackground = Color(0xFF0F0E1A)
val DarkSurface = Color(0xFF16151F)
val DarkSurfaceContainer = Color(0xFF1C1B27)
val DarkSurfaceVariant = Color(0xFF252238)
val DarkOnSurface = Color(0xFFE8E6F5)
val DarkOnSurfaceVariant = Color(0xFFA0A0BC)
val DarkOutline = Color(0xFF5F5E5A)
val DarkOutlineVariant = Color(0xFF3A3940)

// 浅色主题表面
val LightBackground = Color(0xFFFBFAFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF3F1FA)
val LightSurfaceVariant = Color(0xFFE7E1F2)
val LightOnSurface = Color(0xFF1B1B22)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOutline = Color(0xFFCAC4D0)
val LightOutlineVariant = Color(0xFFE7E1F2)

// 兼容旧引用
val LightOnPrimary = Color(0xFF0F0E1A)
val DarkOnPrimary = Color(0xFF0F0E1A)

/* ============================================================
   头像 8 色渐变（v2 设计语言，与桌面快捷方式同源）
   ============================================================ */
val AvatarGradients = listOf(
    listOf(Color(0xFF7C6BFF), Color(0xFFA78BFA)),  // 紫
    listOf(Color(0xFF0EA5E9), Color(0xFF38BDF8)),  // 天蓝
    listOf(Color(0xFF10B59A), Color(0xFF34D399)),  // 青绿
    listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)),  // 琥珀
    listOf(Color(0xFFEC4899), Color(0xFFF472B6)),  // 粉
    listOf(Color(0xFF8B5CF6), Color(0xFFC4B5FD)),  // 堇紫
    listOf(Color(0xFFEF4444), Color(0xFFF87171)),  // 红
    listOf(Color(0xFF14B8A6), Color(0xFF5EEAD4)),  // 碧
)
