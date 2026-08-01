package com.elder.wechatvideo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：覆盖 KeepAliveStatus（B19 字段语义）的核心逻辑。
 *
 * 不依赖 Android 框架，也不引入 Robolectric 等额外测试框架，
 * 仅验证 data class 字段默认值与 completedCount 计算是否符合预期。
 */
class KeepAliveStatusTest {

    @Test
    fun `default instance has all flags false`() {
        val status = KeepAliveStatus()
        assertFalse(status.accessibilityEnabled)
        // B19：batteryExempt 默认 false（已豁免=false 表示尚未豁免电池优化）
        assertFalse(status.batteryExempt)
        assertFalse(status.notificationGranted)
        // data class 默认 false（未初始化状态）；实际值由 AutostartPrefs 读取，默认为 true
        assertFalse(status.autostartEnabled)
        // B19：后台运行默认 false（不再硬编码 true）
        assertFalse(status.backgroundEnabled)
        assertFalse(status.shortcutPermissionGranted)
    }

    @Test
    fun `totalCount is always 6`() {
        assertEquals(6, KeepAliveStatus().totalCount)
        assertEquals(6, KeepAliveStatus(batteryExempt = true).totalCount)
    }

    @Test
    fun `completedCount counts only true flags`() {
        // 0 项就绪
        assertEquals(0, KeepAliveStatus().completedCount)

        // 1 项就绪：batteryExempt=true（语义：已豁免电池优化）
        val oneReady = KeepAliveStatus(batteryExempt = true)
        assertEquals(1, oneReady.completedCount)
        assertTrue(oneReady.batteryExempt)

        // 全部就绪
        val allReady = KeepAliveStatus(
            accessibilityEnabled = true,
            batteryExempt = true,
            notificationGranted = true,
            autostartEnabled = true,
            backgroundEnabled = true,
            shortcutPermissionGranted = true
        )
        assertEquals(6, allReady.completedCount)
    }

    @Test
    fun `batteryExempt true means battery optimization is exempted`() {
        // B19 语义修正：batteryExempt=true → 已加入电池优化白名单（isIgnoringBatteryOptimizations=true）
        val exempt = KeepAliveStatus(batteryExempt = true)
        assertTrue(exempt.batteryExempt)

        val notExempt = KeepAliveStatus(batteryExempt = false)
        assertFalse(notExempt.batteryExempt)
    }

    @Test
    fun `autostartEnabled reflects user toggle only`() {
        // autostartEnabled 如实反映用户开关；AutostartPrefs 默认 true，data class 默认 false 仅为未初始化占位
        val on = KeepAliveStatus(autostartEnabled = true)
        assertTrue(on.autostartEnabled)

        val off = KeepAliveStatus(autostartEnabled = false)
        assertFalse(off.autostartEnabled)
    }
}
