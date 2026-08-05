package com.elder.wechatvideo.util

/**
 * 微信相关常量定义
 *
 * 集中存放微信包名、启动器、按钮文案与 resource-id 映射、
 * 搜索相关常量以及各类超时常量，供无障碍服务与呼叫中转逻辑使用。
 *
 * 注意：微信每次版本更新都可能改变控件 resource-id 与文案，使用时应以
 * 文案匹配作为主策略、resource-id 作为辅助策略。
 */
object WeChatConstants {

    /* ===================== 基础包名与组件 ===================== */

    /** 微信包名 */
    const val WECHAT_PACKAGE = "com.tencent.mm"

    /** 微信启动入口 Activity（桌面图标指向的主界面） */
    const val WECHAT_LAUNCHER = "com.tencent.mm.ui.LauncherUI"

    /* ===================== 按钮文案 ===================== */

    /**
     * 视频通话按钮可能的显示文案列表。
     */
    val VIDEO_CALL_TEXTS = listOf(
        "视频通话",
        "视频电话",
        "Voice & Video Call",
        "Video Call",
        "语音视频通话"
    )

    /**
     * 聊天页右下角加号按钮可能的显示文案。
     */
    val PLUS_BUTTON_TEXTS = listOf(
        "+",
        "添加",
        "更多功能"
    )

    /**
     * 微信主界面顶部搜索按钮可能的文案。
     */
    val SEARCH_BUTTON_TEXTS = listOf(
        "搜索",
        "Search"
    )

    /**
     * 搜索页输入框的 resource-id 列表。
     */
    val SEARCH_EDITTEXT_RESOURCE_IDS = listOf(
        "com.tencent.mm:id/cd7",
        "com.tencent.mm:id/j3",
        "com.tencent.mm:id/l4",
        "com.tencent.mm:id/ks",
        "com.tencent.mm:id/gi"
    )

    /* ===================== 搜索结果分组与类型过滤（修复误点公众号） ===================== */

    /**
     * 搜索结果中代表「联系人 / 朋友」分组的分段标题。
     * 仅在该分组内的结果行才视为个人联系人候选。
     */
    val CONTACT_SECTION_HEADERS = listOf(
        "联系人",
        "朋友"
    )

    /**
     * 搜索结果中代表「非联系人」分组的分段标题。
     * 这些分组下的结果行（群聊 / 公众号 / 小程序 / 文章 / 视频号 / 企业微信）一律忽略。
     */
    val NON_CONTACT_SECTION_HEADERS = listOf(
        "群聊",
        "公众号",
        "小程序",
        "文章",
        "视频号",
        "企业微信",
        "朋友圈"
    )

    /**
     * 候选结果行负向过滤关键词：命中任一即视为非个人联系人，直接排除。
     * 典型场景："儿子" 匹配到公众号 "儿子的小卖部"，其行内带有 "公众号" 子标题。
     */
    val OFFICIAL_ACCOUNT_ROW_KEYWORDS = listOf(
        "公众号",
        "小程序",
        "文章",
        "群聊",
        "企业微信",
        "视频号"
    )

    /**
     * 公众号主页特征文本（用于落点校验，命中即判定误入公众号，必须中止呼叫）。
     */
    val OFFICIAL_ACCOUNT_PAGE_KEYWORDS = listOf(
        "公众号简介",
        "进入公众号",
        "服务号",
        "订阅号"
    )

    /**
     * 非个人聊天页面特征文本（落点校验第二道防线）。
     * 命中任一即判定当前不在个人聊天页，应中止拨号。
     * 注意：不能包含"视频通话"等聊天页也有的文案。
     */
    val NON_CHAT_PAGE_KEYWORDS = listOf(
        "公众号简介",
        "进入公众号",
        "服务号",
        "订阅号",
        "小程序",
        "视频号",
        "在看",
        "赞一下",
        "分享到朋友圈",
        "收藏",
        "投诉",
        "复制链接",
        "打开小程序",
        "前往公众号",
        "阅读原文",
        "写留言"
    )

    /* ===================== resource-id ===================== */

    /**
     * 聊天页右下角加号按钮可能的 resource-id 列表。
     *
     * 注意（修复 B3）：微信的 resource-id 在不同界面会被复用。以 `ks` 为例，
     * 它在部分版本中既是「搜索输入框」(见 [SEARCH_EDITTEXT_RESOURCE_IDS])，
     * 又是「视频通话」按钮(见 [VIDEO_CALL_RESOURCE_ID_MAP] 8.0.41+)，
     * **绝非**加号按钮。因此这里刻意不包含 `ks`，避免跨语义误匹配。
     * 此外，[WeChatAccessibilityService] 在按 id 查找加号按钮时还会额外校验
     * className 为 ImageView/ImageButton，进一步防止误触。
     */
    val PLUS_BUTTON_RESOURCE_IDS = listOf(
        "com.tencent.mm:id/ju",
        "com.tencent.mm:id/h9m",
        "com.tencent.mm:id/b4m",
        "com.tencent.mm:id/bjz"
    )

    /**
     * 视频通话按钮 resource-id 的版本映射表。
     *
     * 注意：resource-id 随版本变化，仅作辅助。文案匹配为主策略。
     */
    val VIDEO_CALL_RESOURCE_ID_MAP: Map<String, List<String>> = mapOf(
        "8.0.0 - 8.0.40" to listOf(
            "com.tencent.mm:id/b4m",
            "com.tencent.mm:id/kge",
            "com.tencent.mm:id/feq",
            "com.tencent.mm:id/h9m",
            "com.tencent.mm:id/lqf",
            "com.tencent.mm:id/gsm"
        ),
        "8.0.41+" to listOf(
            "com.tencent.mm:id/ks",
            "com.tencent.mm:id/n2r",
            "com.tencent.mm:id/ffn"
        )
    )

    /**
     * 全版本通用的视频通话按钮 resource-id 集合。
     */
    val VIDEO_CALL_RESOURCE_IDS_ALL: List<String> =
        VIDEO_CALL_RESOURCE_ID_MAP.values.flatten().distinct()

}
