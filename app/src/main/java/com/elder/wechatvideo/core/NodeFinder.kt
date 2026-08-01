package com.elder.wechatvideo.core

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.elder.wechatvideo.util.WeChatConstants

/**
 * 节点查找与过滤引擎。
 *
 * 从 WeChatAccessibilityService 中抽离的所有节点搜索/匹配/过滤逻辑。
 * 职责单一：给定 root + 目标，返回匹配节点。不做点击、不做状态转换。
 * 所有返回的节点由调用方负责 recycle。
 */
class NodeFinder {

    /* ===================== 搜索输入框 ===================== */

    fun findSearchEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (resId in WeChatConstants.SEARCH_EDITTEXT_RESOURCE_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(resId)
            var hit: AccessibilityNodeInfo? = null
            for (node in nodes) {
                if (node.className?.toString() == "android.widget.EditText" && node.isEditable) {
                    hit = node
                    break
                }
            }
            for (node in nodes) {
                if (node != hit) node.recycle()
            }
            if (hit != null) return hit
        }
        return findEditableNode(root)
    }

    /* ===================== 搜索按钮 ===================== */

    fun findSearchButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (text in WeChatConstants.SEARCH_BUTTON_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            var result: AccessibilityNodeInfo? = null
            for (node in nodes) {
                if (result == null && node.text?.toString().equals(text, ignoreCase = true)) {
                    result = findClickableParent(node)
                }
            }
            // 未命中/未返回的节点统一回收（result 可能就是列表中的节点本身，需排除）
            nodes.forEach { if (it !== result) it.recycle() }
            if (result != null) return result
        }
        val icons = mutableListOf<AccessibilityNodeInfo>()
        collectNodesByDescription(root, "搜索", icons)
        var result: AccessibilityNodeInfo? = null
        for (node in icons) {
            if (result == null) result = findClickableParent(node)
        }
        icons.forEach { if (it !== result) it.recycle() }
        return result
    }

    /* ===================== 搜索结果联系人匹配（四道防线） ===================== */

    fun findContactInSearchResults(
        root: AccessibilityNodeInfo,
        targetName: String
    ): AccessibilityNodeInfo? {
        val rawNodes = root.findAccessibilityNodeInfosByText(targetName)
        var result: AccessibilityNodeInfo? = null
        try {
            val sectionBounds = findContactsSectionBounds(root) ?: return null
            val candidates = rawNodes.filter { node ->
                val text = node.text?.toString() ?: return@filter false
                val isExact = text == targetName
                val isPrefix = !isExact &&
                    text.startsWith(targetName, ignoreCase = true) &&
                    text.length <= targetName.length + 1
                if (!isExact && !isPrefix) return@filter false
                if (!isWithinBounds(node, sectionBounds)) return@filter false
                if (containsNegativeKeyword(node)) return@filter false
                true
            }
            val exact = candidates.firstOrNull { it.text?.toString() == targetName }
            val best = exact ?: candidates.firstOrNull()
            result = best?.let { findClickableParent(it) ?: it }
            return result
        } finally {
            // 返回的节点可能就是 rawNodes 中的元素（findClickableParent 可能返回节点自身），需排除后再回收
            rawNodes.forEach { if (it !== result) it.recycle() }
        }
    }

    /* ===================== + 按钮 ===================== */

    fun findPlusButton(root: AccessibilityNodeInfo, screenHeight: Int): AccessibilityNodeInfo? {
        for (resId in WeChatConstants.PLUS_BUTTON_RESOURCE_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(resId)
            var result: AccessibilityNodeInfo? = null
            for (node in nodes) {
                if (result == null && isInBottomHalf(node, screenHeight) && isImageNode(node)) result = node
            }
            nodes.forEach { if (it !== result) it.recycle() }
            if (result != null) return result
        }
        for (text in WeChatConstants.PLUS_BUTTON_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            var result: AccessibilityNodeInfo? = null
            for (node in nodes) {
                if (result == null && isInBottomHalf(node, screenHeight)) {
                    result = findClickableParent(node) ?: node
                }
            }
            nodes.forEach { if (it !== result) it.recycle() }
            if (result != null) return result
        }
        val images = findClickableImages(root)
        val chosen = images
            .filter { isInBottomHalf(it, screenHeight, 0.6f) }
            .maxByOrNull { getNodeCenterX(it) }
        images.forEach { if (it !== chosen) it.recycle() }
        return chosen
    }

    /* ===================== 视频通话按钮 ===================== */

    fun findVideoCallButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (text in WeChatConstants.VIDEO_CALL_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            var result: AccessibilityNodeInfo? = null
            for (node in nodes) {
                if (result == null && node.text?.toString().equals(text, ignoreCase = true)) {
                    result = findClickableParent(node) ?: node
                }
            }
            nodes.forEach { if (it !== result) it.recycle() }
            if (result != null) return result
        }
        for (resId in WeChatConstants.VIDEO_CALL_RESOURCE_IDS_ALL) {
            val nodes = root.findAccessibilityNodeInfosByViewId(resId)
            var result: AccessibilityNodeInfo? = null
            for (node in nodes) {
                // 兜底分支仍需文本校验，防止跨语义 id 误匹配
                val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                if (result == null && WeChatConstants.VIDEO_CALL_TEXTS.any { text.contains(it) }) {
                    result = findClickableParent(node) ?: node
                }
            }
            nodes.forEach { if (it !== result) it.recycle() }
            if (result != null) return result
        }
        return null
    }

    /* ===================== 落点校验 ===================== */

    fun isOfficialAccountPage(root: AccessibilityNodeInfo): Boolean {
        if (treeContainsAny(root, WeChatConstants.OFFICIAL_ACCOUNT_PAGE_KEYWORDS)) return true
        return treeContainsAny(root, listOf("公众号")) && treeContainsAny(root, listOf("发消息"))
    }

    fun isOnSearchResultsPage(root: AccessibilityNodeInfo): Boolean {
        val headers = WeChatConstants.CONTACT_SECTION_HEADERS + WeChatConstants.NON_CONTACT_SECTION_HEADERS
        return treeContainsAny(root, headers)
    }

    fun hasChatSessionIndicator(root: AccessibilityNodeInfo): Boolean {
        return treeContainsAny(root, listOf("视频通话", "语音通话", "说点什么", "发送", "音视频通话", "设置备注和标签"))
    }

    /* ===================== 工具方法 ===================== */

    fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null && !current.isClickable) {
            val parent = current.parent
            // 上溯链上的中间节点及时回收（传入的 node 由调用方管理）
            if (current !== node) current.recycle()
            current = parent
        }
        return current
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString() == "android.widget.EditText" && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) {
                // 命中的是 child 子树中的节点时，child 本身也需回收（修复提前 return 时的泄漏）
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findContactsSectionBounds(root: AccessibilityNodeInfo): Pair<Float, Float>? {
        val headers = findAllSectionHeaders(root)
        val contactHeader = headers.firstOrNull { it.first in WeChatConstants.CONTACT_SECTION_HEADERS }
            ?: return null
        val nextHeader = headers
            .filter { it.second > contactHeader.second + 1f }
            .minByOrNull { it.second }
        val screenBottom = 10000f
        val bottomY = nextHeader?.second ?: screenBottom
        return contactHeader.second to bottomY
    }

    private fun findAllSectionHeaders(root: AccessibilityNodeInfo): List<Pair<String, Float>> {
        val allHeaders = WeChatConstants.CONTACT_SECTION_HEADERS + WeChatConstants.NON_CONTACT_SECTION_HEADERS
        val found = mutableListOf<Pair<String, Float>>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val t = node.text?.toString() ?: ""
            if (t in allHeaders) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                found.add(t to rect.top.toFloat())
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
            // 只提取文本/坐标，遍历过的非 root 节点统一回收
            if (node !== root) node.recycle()
        }
        return found
    }

    private fun isWithinBounds(node: AccessibilityNodeInfo, bounds: Pair<Float, Float>): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.exactCenterY() >= bounds.first && rect.exactCenterY() < bounds.second
    }

    private fun containsNegativeKeyword(node: AccessibilityNodeInfo): Boolean {
        val clickable = findClickableParent(node)
        val roots = if (clickable != null && clickable != node) listOf(node, clickable) else listOf(node)
        val hit = roots.any { treeContainsAny(it, WeChatConstants.OFFICIAL_ACCOUNT_ROW_KEYWORDS) }
        // 临时获取的可点击父节点用完即回收
        if (clickable != null && clickable !== node) clickable.recycle()
        return hit
    }

    private fun treeContainsAny(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(node) }
        while (stack.isNotEmpty()) {
            val n = stack.removeFirst()
            val t = n.text?.toString() ?: ""
            val d = n.contentDescription?.toString() ?: ""
            for (kw in keywords) {
                if (t.contains(kw) || d.contains(kw)) {
                    return true
                }
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.add(it) }
            }
            // 纯查询，不回收遍历节点（由调用方统一管理 root 生命周期，
            // 避免 OEM ROM 上 getChild 返回缓存引用导致 use-after-recycle）
        }
        return false
    }

    private fun collectNodesByDescription(node: AccessibilityNodeInfo, desc: String, result: MutableList<AccessibilityNodeInfo>) {
        if (node.contentDescription?.toString()?.contains(desc) == true) result.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodesByDescription(child, desc, result)
            // 未被收集的子节点及时回收（已收集的由调用方统一处理）
            if (result.none { it === child }) child.recycle()
        }
    }

    private fun isInBottomHalf(node: AccessibilityNodeInfo, screenHeight: Int, threshold: Float = 0.5f): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.top > screenHeight * threshold
    }

    private fun isImageNode(node: AccessibilityNodeInfo): Boolean {
        val cn = node.className?.toString() ?: ""
        return cn.contains("ImageView") || cn.contains("ImageButton")
    }

    private fun getNodeCenterX(node: AccessibilityNodeInfo): Float {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.exactCenterX()
    }

    private fun findClickableImages(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val cn = node.className?.toString() ?: ""
            val collected = node.isClickable && (cn.contains("ImageView") || cn.contains("ImageButton"))
            if (collected) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
            // 未被收集的遍历节点及时回收（root 由调用方管理）
            if (!collected && node !== root) node.recycle()
        }
        return result
    }
}
