package com.elder.wechatvideo.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：覆盖 [isContactMatch] 顶层函数的匹配判定逻辑。
 *
 * 重点验证 OCR 严格模式失效缺陷的修复：
 * - 搜索框区域（屏幕顶部 15%）的文本不得误命中（原 `lineText.contains(targetName)` 缺陷）
 * - 反向包含 `targetName.contains(lineText)` 不得再触发（原单字符/空字符串误命中）
 * - 匹配口径与第一层 NodeFinder.findContactInSearchResults 一致（精确/前缀）
 */
class OcrHelperTest {

    /** 屏幕高度常量（像素），用于推算搜索框边界 */
    private val screenH = 2400f
    /** 搜索框底部边界 = 屏幕高度 × 15% */
    private val searchBoxBottom = screenH * 0.15f   // 360px
    /** 搜索结果区域中心 Y（安全位于搜索框之下） */
    private val resultAreaY = searchBoxBottom + 200f   // 560px

    /* ===================== 搜索框误命中修复（核心缺陷） ===================== */

    @Test
    fun `搜索框区域的精确匹配文本不得命中`() {
        // 搜索框文本恒等于搜索词，位于屏幕顶部 → 必须排除，否则严格模式被绕过
        assertFalse(
            isContactMatch("张三", "张三", searchBoxBottom - 50f, screenH)
        )
    }

    @Test
    fun `搜索框边界处恰好不命中`() {
        // Y 恰好等于搜索框底部（360px）→ 边界值为"不含搜索框"，应命中
        // 注：isContactMatch 使用 >=，故 Y=360 应命中
        assertTrue(
            isContactMatch("张三", "张三", searchBoxBottom, screenH)
        )
    }

    @Test
    fun `搜索框正上方不命中`() {
        assertFalse(
            isContactMatch("张三", "张三", searchBoxBottom - 1f, screenH)
        )
    }

    /* ===================== 反向包含误命中修复（核心缺陷） ===================== */

    @Test
    fun `空字符串行不得命中任意目标名`() {
        // 原 targetName.contains("") 恒为 true → 任意搜索词都误命中
        assertFalse(isContactMatch("", "张三", resultAreaY, screenH))
    }

    @Test
    fun `单字符行不得通过反向包含命中`() {
        // 原 targetName.contains("张") → 搜索"张三"时误命中任何含"张"的行
        assertFalse(isContactMatch("张", "张三", resultAreaY, screenH))
    }

    @Test
    fun `短文本行不得通过反向包含命中`() {
        // 原 targetName.contains("张三") → 搜索"张三不存在"时误命中"张三"
        assertFalse(isContactMatch("张三", "张三不存在的人", resultAreaY, screenH))
    }

    /* ===================== 正常命中场景（存在的联系人） ===================== */

    @Test
    fun `结果区域精确匹配命中`() {
        assertTrue(isContactMatch("张三", "张三", resultAreaY, screenH))
    }

    @Test
    fun `结果区域前缀匹配命中`() {
        // 文本以目标名开头且长度 ≤ 目标+1（如"张三("）
        assertTrue(isContactMatch("张三(", "张三", resultAreaY, screenH))
    }

    @Test
    fun `结果区域前缀匹配恰好多一字符命中`() {
        assertTrue(isContactMatch("妈妈:", "妈妈", resultAreaY, screenH))
    }

    @Test
    fun `结果区域忽略大小写前缀匹配命中`() {
        assertTrue(isContactMatch("Tom(", "tom", resultAreaY, screenH))
    }

    /* ===================== 不应命中场景（不存在的联系人） ===================== */

    @Test
    fun `不包含目标名的长文本不命中`() {
        // 如"没有找到相关结果"不包含"张三" → 不命中
        assertFalse(isContactMatch("没有找到相关结果", "张三", resultAreaY, screenH))
    }

    @Test
    fun `搜索建议行不以目标名开头不命中`() {
        // 如"搜索: 张三"去空格后为"搜索:张三"，不以"张三"开头 → 不命中
        assertFalse(isContactMatch("搜索:张三", "张三", resultAreaY, screenH))
    }

    @Test
    fun `前缀过长不命中`() {
        // 以目标名开头但长度 > 目标+1 → 不命中（避免误命中长文本）
        assertFalse(isContactMatch("张三的备注信息很长", "张三", resultAreaY, screenH))
    }

    @Test
    fun `目标名出现在文本中间不命中`() {
        // "我的朋友张三先生" 包含"张三"但不是前缀 → 不命中
        assertFalse(isContactMatch("我的朋友张三先生", "张三", resultAreaY, screenH))
    }

    /* ===================== 边界条件 ===================== */

    @Test
    fun `空目标名不命中`() {
        assertFalse(isContactMatch("张三", "", resultAreaY, screenH))
    }

    @Test
    fun `空行文本不命中`() {
        assertFalse(isContactMatch("", "", resultAreaY, screenH))
    }

    @Test
    fun `零高度截图安全处理不崩溃`() {
        // 异常输入：bitmapHeight=0 → searchBoxBottom=0 → 所有 Y>=0 都在结果区，
        // 但仍需通过文本匹配；此处验证不会因除零或负值崩溃
        assertFalse(isContactMatch("不存在", "张三", 0f, 0f))
        // Y=0 在 searchBoxBottom=0 之上吗？0 >= 0 → true，文本精确匹配 → 应命中
        assertTrue(isContactMatch("张三", "张三", 0f, 0f))
    }

    @Test
    fun `单字符目标名精确匹配命中`() {
        // 边界：目标名为单字符"张"，结果区精确匹配
        assertTrue(isContactMatch("张", "张", resultAreaY, screenH))
    }

    @Test
    fun `单字符目标名在搜索框不命中`() {
        assertFalse(isContactMatch("张", "张", searchBoxBottom - 10f, screenH))
    }

    /* ===================== 严格模式场景模拟 ===================== */

    /**
     * 模拟严格模式核心场景：搜索不存在的联系人时，OCR 截图只识别到搜索框文本，
     * isContactMatch 必须全部返回 false，使 findContactPosition 返回 null → ocrFound=false → 严格模式 fail()。
     */
    @Test
    fun `严格模式_不存在的联系人_搜索框文本不命中`() {
        val targetName = "不存在的联系人"
        // 搜索框区域只有搜索词本身
        assertFalse(
            isContactMatch(targetName, targetName, 100f, screenH)
        )
        // 搜索框下方有"没有找到相关结果"
        assertFalse(
            isContactMatch("没有找到相关结果", targetName, resultAreaY, screenH)
        )
        // 任何位置都没有真正的联系人 → OCR 整体返回 null → 严格模式停止
    }

    /**
     * 模拟严格模式正常场景：搜索存在的联系人时，结果区域有精确匹配 → 命中 → 正常拨号。
     */
    @Test
    fun `严格模式_存在的联系人_结果区域命中`() {
        val targetName = "妈妈"
        assertTrue(
            isContactMatch(targetName, targetName, resultAreaY, screenH)
        )
    }

    /**
     * 模拟关闭严格模式场景：OCR 未找到 → 走坐标盲戳兜底。
     * 此测试验证 isContactMatch 在不命中时确实返回 false（让 ocrFound 保持 false）。
     */
    @Test
    fun `关闭严格模式_OCR未找到_返回false以走盲戳兜底`() {
        // 搜索框区域不命中 + 结果区"没有找到"不命中 → OCR 返回 null → ocrFound=false
        assertFalse(isContactMatch("张三", "张三", 100f, screenH))
        assertFalse(isContactMatch("没有找到", "张三", resultAreaY, screenH))
    }
}
