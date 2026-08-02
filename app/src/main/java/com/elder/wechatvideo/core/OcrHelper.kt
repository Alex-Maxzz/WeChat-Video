package com.elder.wechatvideo.core

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import com.elder.wechatvideo.util.WeChatConstants
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * OCR 辅助定位器（ML Kit 端侧中文识别）。
 *
 * 仅在无障碍节点查找失败时作为兜底：
 * 截图 → OCR 识别 → 在结果中搜索目标联系人文字 → 返回点击坐标。
 *
 * 需要 Android 11+（API 30）的 AccessibilityService.takeScreenshot()。
 * 低于 API 30 的设备直接返回 null（走原有坐标兜底）。
 */
object OcrHelper {

    private const val TAG = "OcrHelper"

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    /**
     * 截图并在识别结果中查找目标联系人名称，返回其屏幕中心坐标。
     *
     * @param service 无障碍服务实例（用于 takeScreenshot）
     * @param targetName 要搜索的联系人名称
     * @return Pair(x, y) 屏幕像素坐标，或 null（未找到/截图失败/API不支持）
     */
    suspend fun findContactPosition(
        service: AccessibilityService,
        targetName: String,
        debugCallback: ((String) -> Unit)? = null
    ): Pair<Float, Float>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "API < 30，不支持 takeScreenshot，跳过 OCR")
            debugCallback?.invoke("OCR: API<30 不支持截图")
            return null
        }

        debugCallback?.invoke("OCR: 正在截图…")
        val bitmap = takeScreenshot(service, debugCallback)
        if (bitmap == null) {
            return null
        }
        debugCallback?.invoke("OCR: 截图成功 ${bitmap.width}x${bitmap.height}")
        return try {
            recognizeAndFind(bitmap, targetName, debugCallback)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 使用 AccessibilityService.takeScreenshot() 截取当前屏幕。
     */
    private suspend fun takeScreenshot(
        service: AccessibilityService,
        debugCallback: ((String) -> Unit)? = null
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return suspendCoroutine { continuation ->
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            screenshot.hardwareBuffer.close()
                            if (bitmap != null) {
                                val swBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                bitmap.recycle()
                                continuation.resume(swBitmap)
                            } else {
                                debugCallback?.invoke("OCR: 截图成功但转换失败")
                                continuation.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot 失败，errorCode=$errorCode")
                            debugCallback?.invoke("OCR: 截图失败 错误码=$errorCode")
                            continuation.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "takeScreenshot 异常", e)
                continuation.resume(null)
            }
        }
    }

    /**
     * 对 bitmap 执行 OCR，在识别结果中查找包含 targetName 的文字块，返回其中心坐标。
     */
    private suspend fun recognizeAndFind(
        bitmap: Bitmap,
        targetName: String,
        debugCallback: ((String) -> Unit)? = null
    ): Pair<Float, Float>? {
        val image = InputImage.fromBitmap(bitmap, 0)

        val text: com.google.mlkit.vision.text.Text? = suspendCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result -> continuation.resume(result) }
                .addOnFailureListener { e ->
                    Log.w(TAG, "ML Kit 识别失败", e)
                    debugCallback?.invoke("OCR: ML Kit识别失败 ${e.message}")
                    continuation.resume(null)
                }
        }
        if (text == null) {
            debugCallback?.invoke("OCR: 识别结果为空")
            return null
        }

        val allLines = text.textBlocks.flatMap { it.lines }
        debugCallback?.invoke("OCR: 识别到${allLines.size}行: ${allLines.map { it.text }.take(5).joinToString("|")}")

        val bitmapHeight = bitmap.height.toFloat()

        // 收集分段标题的 Y 坐标，用于判断匹配行属于哪个分段。
        // 与 NodeFinder.findAllSectionHeaders 保持精确匹配口径一致：
        // 不使用子串匹配，避免"没有联系人匹配结果"等非标题文本被误识别为联系人分段标题。
        val contactHeaderYs = mutableListOf<Float>()
        val nonContactHeaderYs = mutableListOf<Float>()
        for (line in allLines) {
            val lt = line.text.replace(" ", "")
            val rect = line.boundingBox ?: continue
            val cy = rect.exactCenterY()
            if (WeChatConstants.CONTACT_SECTION_HEADERS.any { isOcrSectionHeader(lt, it) }) {
                contactHeaderYs.add(cy)
            } else if (WeChatConstants.NON_CONTACT_SECTION_HEADERS.any { isOcrSectionHeader(lt, it) }) {
                nonContactHeaderYs.add(cy)
            }
        }

        for (line in allLines) {
            val lineText = line.text.replace(" ", "")
            val rect = line.boundingBox ?: continue
            if (!isContactMatch(lineText, targetName, rect.exactCenterY(), bitmapHeight)) continue

            // 分段上下文校验：匹配行上方最近的标题必须是"联系人"/"朋友"，
            // 如果上方最近的是"群聊"/"公众号"等非联系人分段，则跳过（防止误点公众号）。
            // 若未识别到任何分段标题（字体小/遮挡），不拦截，保持原有行为。
            val cy = rect.exactCenterY()
            val nearestContact = contactHeaderYs.filter { it <= cy }.maxOrNull()
            val nearestNonContact = nonContactHeaderYs.filter { it <= cy }.maxOrNull()
            if (nearestNonContact != null && (nearestContact == null || nearestNonContact > nearestContact)) {
                debugCallback?.invoke("OCR: 命中「$targetName」但位于非联系人分段，跳过")
                continue
            }

            val x = rect.exactCenterX()
            val y = rect.exactCenterY()
            Log.i(TAG, "OCR 命中「$targetName」→ ($x, $y)")
            return Pair(x, y)
        }

        Log.d(TAG, "OCR 未找到「$targetName」")
        return null
    }

    /**
     * 判断 OCR 识别文本（已去空格）是否为分段标题。
     *
     * 与 [NodeFinder.isSectionHeader] 保持一致口径：精确匹配或"标题+数字"格式，
     * 不使用子串匹配，避免"没有联系人匹配结果"被误识别为"联系人"分段标题。
     */
    private fun isOcrSectionHeader(text: String, header: String): Boolean {
        if (text == header) return true
        if (text.startsWith(header)) {
            val remainder = text.substring(header.length)
            return remainder.matches(Regex("\\d+"))
        }
        return false
    }
}

/**
 * 判断一行 OCR 识别文本是否应被视为目标联系人命中（纯函数，可独立单元测试）。
 *
 * 放在 object 外部作为顶层函数，避免单元测试加载 [OcrHelper] object 时触发 ML Kit 类初始化。
 *
 * 匹配规则与第一层 [NodeFinder.findContactInSearchResults] 保持一致：
 * 1. 仅精确匹配或前缀匹配（startsWith，长度 ≤ 目标+1），避免误命中"未找到相关结果"等长文本；
 * 2. 排除屏幕顶部搜索框区域（其文本恒等于搜索词，非联系人结果）。
 *
 * 修复说明：原逻辑 `lineText.contains(targetName) || targetName.contains(lineText)` 存在两个缺陷：
 * - `lineText.contains(targetName)` 会误命中搜索框（搜索框文本即用户输入的搜索词）；
 * - `targetName.contains(lineText)` 过于宽松，单字符/空字符串行会误命中任意目标名。
 * 两者均导致搜索不存在联系人时 ocrFound=true，绕过 OCR 严格模式的停止逻辑。
 *
 * @param lineText 已去空格的 OCR 行文本
 * @param targetName 目标联系人名称
 * @param lineCenterY 该行中心 Y 坐标（像素）
 * @param bitmapHeight 截图总高度（像素）
 * @return true 视为命中
 */
internal fun isContactMatch(
    lineText: String,
    targetName: String,
    lineCenterY: Float,
    bitmapHeight: Float
): Boolean {
    if (lineText.isEmpty() || targetName.isEmpty()) return false
    // 与 NodeFinder.findContactInSearchResults 保持一致的匹配口径：
    // 仅精确匹配或前缀匹配（长度 ≤ 目标+1），不使用反向包含（targetName.contains(lineText)）
    val isExact = lineText == targetName
    val isPrefix = !isExact &&
        lineText.startsWith(targetName, ignoreCase = true) &&
        lineText.length <= targetName.length + 1
    if (!isExact && !isPrefix) return false
    // 排除搜索框区域（屏幕顶部 15%）：搜索框文本恒等于搜索词，非联系人结果，
    // 不排除会导致搜索不存在联系人时 OCR 误命中 → ocrFound=true → 绕过严格模式停止
    val searchBoxBottom = bitmapHeight * 0.15f
    return lineCenterY >= searchBoxBottom
}
