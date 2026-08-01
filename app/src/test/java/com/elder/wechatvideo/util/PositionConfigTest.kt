package com.elder.wechatvideo.util

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：覆盖 PositionConfig 的归一化坐标存/取、旧值迁移逻辑（B21）
 * 与设备参数变化检测逻辑（V1.3.2）。
 *
 * 使用内存版 [FakeSharedPreferences]（自实现 SharedPreferences 接口，
 * 无需 Robolectric，也不引入任何新依赖），直接调用 PositionConfig 暴露的
 * internal 注入重载（屏幕尺寸/设备参数以参数传入，无需构造 Context）。
 * 仅验证坐标换算、迁移与参数比对的纯逻辑正确性，不涉及真实设备显示。
 */
class PositionConfigTest {

    /** 内存实现的 SharedPreferences，覆盖本测试所需的 Float/Int/Boolean/contains 语义 */
    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return map[key] as? MutableSet<String> ?: defValues
        }
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }

    private class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            apply { pending[key!!] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { pending[key!!] = values }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            apply { pending[key!!] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            apply { pending[key!!] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            apply { pending[key!!] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            apply { pending[key!!] = value }
        override fun remove(key: String?): SharedPreferences.Editor =
            apply { pending[key!!] = null }
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }
        override fun commit(): Boolean { applyChanges(); return true }
        override fun apply() = applyChanges()

        private fun applyChanges() {
            if (clearRequested) {
                prefs.map.clear()
                clearRequested = false
            }
            for ((k, v) in pending) {
                if (v == null) prefs.map.remove(k) else prefs.map[k] = v
            }
            pending.clear()
        }
    }

    /* ===================== 归一化坐标存/取 ===================== */

    @Test
    fun `save then read round-trips through normalized coords`() {
        val prefs = FakeSharedPreferences()
        // 在 1080x1920 屏幕上保存绝对像素 (540, 960) → 归一化应为 (0.5, 0.5)
        val editor = prefs.edit()
        PositionConfig.writeNormalized(editor, "search_x", "search_y", 540f, 960f, 1080f, 1920f)
        editor.apply()

        val (nx, ny) = PositionConfig.readNormalized(prefs, "search_x", "search_y", 1080f, 1920f)
        assertEquals(540f, nx * 1080f)
        assertEquals(960f, ny * 1920f)
    }

    @Test
    fun `saved value is normalized within unit range`() {
        val prefs = FakeSharedPreferences()
        val editor = prefs.edit()
        PositionConfig.writeNormalized(editor, "search_x", "search_y", 540f, 960f, 1080f, 1920f)
        editor.apply()

        // 内部存储应为归一化值（≤1.0），而非绝对像素
        val storedX = prefs.getFloat("search_x", -1f)
        val storedY = prefs.getFloat("search_y", -1f)
        assertTrue("stored x should be normalized (<=1.0), was $storedX", storedX <= 1.0f)
        assertTrue("stored y should be normalized (<=1.0), was $storedY", storedY <= 1.0f)
        assertEquals(0.5f, storedX)
        assertEquals(0.5f, storedY)
    }

    @Test
    fun `coordinates normalize correctly across different screen sizes`() {
        val prefs = FakeSharedPreferences()
        // 相同归一化意图：屏幕中心。在 1080x1920 上中心 = (540, 960)
        val editor = prefs.edit()
        PositionConfig.writeNormalized(editor, "search_x", "search_y", 540f, 960f, 1080f, 1920f)
        editor.apply()

        // 换一台 1440x2560 的手机读取，应还原为对应像素 (720, 1280)
        val (nx, ny) = PositionConfig.readNormalized(prefs, "search_x", "search_y", 1440f, 2560f)
        assertEquals(720f, nx * 1440f)
        assertEquals(1280f, ny * 2560f)
    }

    @Test
    fun `old absolute pixel values are migrated to normalized on read`() {
        val prefs = FakeSharedPreferences()
        // 预置"旧版"绝对像素数据（值 > 1.0），模拟升级前的校准数据
        prefs.edit().putFloat("search_x", 540f).putFloat("search_y", 960f).apply()

        val (nx, ny) = PositionConfig.readNormalized(prefs, "search_x", "search_y", 1080f, 1920f)

        // 迁移后读取应还原为正确像素
        assertEquals(540f, nx * 1080f)
        assertEquals(960f, ny * 1920f)

        // 迁移后存储值应已回写为归一化（≤1.0），避免下次再次迁移
        val storedX = prefs.getFloat("search_x", -1f)
        val storedY = prefs.getFloat("search_y", -1f)
        assertTrue("migrated x should be <=1.0, was $storedX", storedX <= 1.0f)
        assertTrue("migrated y should be <=1.0, was $storedY", storedY <= 1.0f)
    }

    @Test
    fun `out-of-bounds pixel is coerced into unit range when saving`() {
        val prefs = FakeSharedPreferences()
        // 传入超出屏幕的像素 (2000, -100) → 归一化后应被 coerceIn 到 [0,1]
        val editor = prefs.edit()
        PositionConfig.writeNormalized(editor, "search_x", "search_y", 2000f, -100f, 1080f, 1920f)
        editor.apply()

        assertEquals(1.0f, prefs.getFloat("search_x", -1f))
        assertEquals(0.0f, prefs.getFloat("search_y", -1f))

        // 读取时还原为屏幕边界像素
        val (nx, ny) = PositionConfig.readNormalized(prefs, "search_x", "search_y", 1080f, 1920f)
        assertEquals(1080f, nx * 1080f)
        assertEquals(0f, ny * 1920f)
    }

    /* ===================== 设备参数变化检测（V1.3.2） ===================== */

    @Test
    fun `device params change returns false when no record exists`() {
        val prefs = FakeSharedPreferences()
        // 无历史记录（旧版校准数据）时不误报
        assertFalse(PositionConfig.isDeviceParamsChanged(prefs, 1080, 1920, 480, 1.0f))
    }

    @Test
    fun `device params change returns false when unchanged`() {
        val prefs = FakeSharedPreferences()
        PositionConfig.saveDeviceParams(prefs, 1080, 1920, 480, 1.0f)
        assertFalse(PositionConfig.isDeviceParamsChanged(prefs, 1080, 1920, 480, 1.0f))
    }

    @Test
    fun `device params change detects resolution change`() {
        val prefs = FakeSharedPreferences()
        PositionConfig.saveDeviceParams(prefs, 1080, 1920, 480, 1.0f)
        assertTrue(PositionConfig.isDeviceParamsChanged(prefs, 1440, 2560, 480, 1.0f))
    }

    @Test
    fun `device params change detects density dpi change`() {
        val prefs = FakeSharedPreferences()
        PositionConfig.saveDeviceParams(prefs, 1080, 1920, 480, 1.0f)
        assertTrue(PositionConfig.isDeviceParamsChanged(prefs, 1080, 1920, 440, 1.0f))
    }

    @Test
    fun `device params change ignores small font scale drift`() {
        val prefs = FakeSharedPreferences()
        PositionConfig.saveDeviceParams(prefs, 1080, 1920, 480, 1.0f)
        // |差| ≤ 0.1 不算变化
        assertFalse(PositionConfig.isDeviceParamsChanged(prefs, 1080, 1920, 480, 1.05f))
    }

    @Test
    fun `device params change detects large font scale change`() {
        val prefs = FakeSharedPreferences()
        PositionConfig.saveDeviceParams(prefs, 1080, 1920, 480, 1.0f)
        // |差| > 0.1 视为变化（老人常用超大字体，1.0 → 1.3）
        assertTrue(PositionConfig.isDeviceParamsChanged(prefs, 1080, 1920, 480, 1.3f))
    }
}
