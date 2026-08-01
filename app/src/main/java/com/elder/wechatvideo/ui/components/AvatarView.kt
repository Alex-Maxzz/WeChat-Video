package com.elder.wechatvideo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.wechatvideo.ui.theme.AvatarGradients

/**
 * 可复用的头像组件。
 *
 * 渐变圆形背景 + 姓名文字居中显示。颜色由 [colorIndex] 决定，
 * 自动在 [AvatarGradients] 6 组渐变色中循环取色。
 *
 * 默认显示【名字最后一个字】（[showLastChar]=true），以应对老人常用的
 * "同姓多联系人"场景（张大明/张小花 → 明/花，一眼区分）。
 *
 * @param name 联系人姓名
 * @param colorIndex 头像颜色索引，超出范围时自动取模
 * @param size 头像直径，默认 56.dp
 * @param showLastChar true=显示最后一字（默认），false=显示第一字
 * @param modifier 额外修饰符
 */
@Composable
fun AvatarView(
    name: String,
    colorIndex: Int,
    size: Dp = 56.dp,
    showLastChar: Boolean = true,
    modifier: Modifier = Modifier
) {
    val safeIndex = if (AvatarGradients.isEmpty()) 0
    else ((colorIndex % AvatarGradients.size) + AvatarGradients.size) % AvatarGradients.size
    val gradientColors: List<Color> = AvatarGradients[safeIndex]

    val initial = remember(name, showLastChar) {
        avatarChar(name, showLastChar)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(colors = gradientColors)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
