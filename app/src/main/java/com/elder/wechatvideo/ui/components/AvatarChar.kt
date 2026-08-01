package com.elder.wechatvideo.ui.components

/**
 * 头像显示文字工具。
 *
 * 老人常遇到同姓问题（如张大明 / 张小花 / 张小红），
 * 显示【名字的最后一个字】能最大化区分度（明 / 花 / 红），
 * 比显示首字（全是「张」）更利于快速辨认。
 *
 * @param name   联系人姓名
 * @param last   true 取最后一字（默认，推荐给老人），false 取第一字
 */
fun avatarChar(name: String, last: Boolean = true): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "·"
    return if (last) trimmed.last().toString() else trimmed.first().toString()
}
