package com.elder.wechatvideo.ui.screens.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elder.wechatvideo.R
import com.elder.wechatvideo.ui.theme.AvatarGradients
import com.elder.wechatvideo.ui.theme.PurplePrimary
import com.elder.wechatvideo.ui.theme.PurpleLight

/**
 * 关于页面。
 *
 * 展示应用图标、名称、版本号与简介，简洁美观。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val versionName = remember { getVersionName(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) }
            )
        }
    ) { innerPadding ->
        AboutContent(
            versionName = versionName,
            contentPadding = innerPadding
        )
    }
}

/**
 * 关于页内容主体。
 */
@Composable
private fun AboutContent(
    versionName: String,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 应用图标：渐变圆背景 + 前景矢量
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(PurplePrimary, PurpleLight)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(96.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 应用名称
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 版本号
        Text(
            text = stringResource(R.string.about_version, versionName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 描述卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = stringResource(R.string.about_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 版本更新日志
        Text(
            text = "版本更新记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        ChangelogCard(
            version = "V1.7.2",
            date = "2026-08-06",
            changes = "· 修复校准页面重启后误报无障碍未开启：CalibrationScreen 改用 isConnected || isAccessibilityServiceEnabled 双重判断\n· 修复视频通话坐标点击偏移到转账：tryClickVideoCall 改为节点优先查找，节点找不到才用坐标兜底\n· 修复 auto_dial=false 时不验证视频菜单是否弹出就误判成功：tryClickVideoConfirm 增加落地验证，菜单未弹出则报错提示重新校准"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.7.1",
            date = "2026-08-06",
            changes = "· 修复 gradle.properties 机器特定路径迁移：org.gradle.java.home 和 aapt2FromMavenOverride 移至用户级 ~/.gradle/gradle.properties，命令行构建不再找不到 JDK\n· 修复 fail() 后 3 秒内重新拨号浮层被提前隐藏：beginCallFlow() 开头清除旧 overlay.hide() 回调\n· 修正 SettingsPrefs.kt 类注释：移除已删除的\"浮窗位置锁定\"描述"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.7.0",
            date = "2026-08-06",
            changes = "· 修复 fail() 竞态（P0）：失败后 sm.resetToIdle() 延迟 3 秒执行，期间 OCR/盲戳检查 sm.isActive 仍为 true 会误点击。改为立即切 IDLE\n· 修复 resetSearchForRetry 漏重置 searchClickRetries：搜索结果超时重试时计数不清零，累计触发\"搜索按钮无法打开\"误报\n· 清理死代码：删除未调用的 AutoDialCard、失效的浮窗位置固定开关、8 个零引用僵尸常量"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.9",
            date = "2026-08-03",
            changes = "· 修复重启后快捷方式误报\"无障碍未开启\"（P0）：Settings.Secure 重启后可能暂时为空，但服务已实际绑定。增加 isConnected 运行时标志作为替代判据，任一为 true 即通过"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.8",
            date = "2026-08-03",
            changes = "· 修复聊天页判断假阴性：底部输入框检测阈值从 0.6 降至 0.4（屏幕下方60%），兜底键盘弹起场景（键盘占屏50%时输入框被顶到44%不再漏判）\n· 修复 OCR 分段标题匹配不一致：OcrHelper 仍用子串匹配会误识别\"没有联系人匹配结果\"为联系人标题，改为与 NodeFinder 一致的精确匹配"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.7",
            date = "2026-08-03",
            changes = "· 聊天页检测改多层方案：content-desc 关键词 + 底部 EditText 结构检测，不再依赖\"视频通话\"等微信可变文案\n· 分段标题检测改精确匹配：\"标题+空格+数字\"格式，避免子串误命中非标题文本"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.6",
            date = "2026-08-03",
            changes = "· 修复 OCR 与超时检查器竞态（P0）：OCR 期间阻止 timeoutChecker 触发搜索重试，OCR 完成后校验状态未变才执行点击，杜绝点错联系人\n· 修复分段标题子串匹配误命中：\"联系人\"子串会误命中\"没有联系人匹配结果\"等文本，改为精确匹配+\"标题 空格 数字\"格式\n· 修复聊天页判断过于宽泛：移除\"发送\"关键词，避免搜索页\"发送给朋友\"误判为已进入聊天页"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.5",
            date = "2026-08-03",
            changes = "· 修复 pendingCall 死锁：服务崩溃重启后 onServiceConnected 重置 pendingCall，不再永久拒绝新呼叫\n· 新增无障碍服务健康监控：保活服务每 5 分钟检测连接状态，断开时主动通知用户重新开启\n· 修复 + 按钮和视频通话步骤 root 为 null 时不重试，避免白白等待超时\n· OCR 文本匹配切到 Default 线程，搜索结果多时不再卡主线程\n· restartSearch() 补全按钮/视频/确认标志位重置，防止未来流程变更遗漏"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.4",
            date = "2026-08-02",
            changes = "· 修复搜索结果误点公众号：分段标题检测改为子串匹配，\"公众号 2\"等不再漏识别\n· 修复 OCR 永远不执行：第1层节点查找假成功导致 OCR 被跳过\n· OCR 新增分段上下文校验：非联系人分段下的匹配行自动跳过\n· 修复 OCR 期间无超时保护：恢复 startTimeoutChecker"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.3",
            date = "2026-08-02",
            changes = "· 修复 OCR 严格模式失效：搜索框文本被误判为联系人结果\n· OCR 匹配逻辑与无障碍节点查找对齐（精确/前缀），排除搜索框区域\n· 新增 22 项单元测试覆盖核心匹配场景\n· 重启后拨号不再误报\"未开启\"：无障碍检查改为 500ms×6 次重试，等待系统恢复绑定"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.2",
            date = "2026-08-01",
            changes = "· 修复 OCR 截图失败：补全无障碍服务 canTakeScreenshot 权限声明\n· 移除所有调试弹窗（DEBUG Toast），正式环境更干净\n· 取消按钮改为固定位置（仅点击，不可拖动），防止误触移位"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.1",
            date = "2026-08-01",
            changes = "· 修复保活服务冷启动缺口：打开App即启动前台服务，无障碍不再被系统杀\n· 修复 Direct Boot 崩溃：移除 LOCKED_BOOT_COMPLETED 避免加密存储异常\n· 修复节点泄漏：clickNode 统一回收、EditText 用后即收\n· 修复服务中断时进度浮窗残留\n· 校准服务结束时先 stopForeground 再 stopSelf，通知不残留\n· 取消按钮触控区加大到 48dp（适老化标准）\n· OCR 关闭时不再显示\"智能识别\"误导文字"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.6.0",
            date = "2026-08-01",
            changes = "· 新增设置页面（底部第三个Tab）：集中管理所有开关\n· OCR 智能识别开关（默认开启）+ 严格模式（识别不到就停止）\n· 自动拨打开关从校准页迁移到设置页\n· 拨号浮窗位置固定开关（默认关闭=可拖动）\n· 深色/浅色/跟随系统 主题切换\n· 关于页面移入设置子页面\n· 三层拨号防线顺序优化：节点→OCR→坐标"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.5.0",
            date = "2026-08-01",
            changes = "· 新增 OCR 智能识别：搜索结果点击失败时自动截图识别联系人位置（ML Kit 端侧）\n· 三层兜底：无障碍节点 → 校准坐标 → OCR 截图识别，拨号成功率大幅提升\n· 完全离线运行，不联网、不弹权限、无调用限制\n· 拨号浮窗双窗口架构：显示层触摸穿透，取消按钮独立可点可拖"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.4.6",
            date = "2026-08-01",
            changes = "· 拨号浮窗改双窗口架构：显示层完全触摸穿透，不影响微信操作\n· 取消按钮独立窗口：单击取消、按住拖动整体、位置记忆\n· 彻底解决浮窗阻挡微信点击的问题"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.4.5",
            date = "2026-08-01",
            changes = "· 稳定版：修复浮窗在系统字体放大时变形的问题（全部改用DIP单位）\n· 取消按钮固定尺寸64×36dp，任何情况下不变形\n· 校准浮窗同步DIP修复，按钮不再被字体缩放撑大\n· 拨号胶囊条定型：55dp高、75%屏宽、文字超长自动省略"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.4.4",
            date = "2026-08-01",
            changes = "· 拨号胶囊条高度调至 55dp，取消按钮红色渐变底白字（高对比度）\n· 长文本自动省略（…），取消按钮永远可见不被挤没\n· 支持拖动 + 位置记忆：拖到习惯位置后下次自动出现"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.4.3",
            date = "2026-08-01",
            changes = "· 拨号进度浮窗重设计：细长胶囊条（40dp高），屏幕居中，可拖动\n· 三态显示：进行中紫色图标+步骤badge / 成功青绿勾 / 失败珊瑚叉\n· 取消按钮全程可见（珊瑚色ghost胶囊）\n· 校准悬浮窗按钮改TextView，修复Material主题胶囊变方形\n· 校准卡片改屏幕居中+WRAP_CONTENT，不遮挡四角操作区"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.4.2",
            date = "2026-08-01",
            changes = "· 微信升级不再阻断拨号，改为 Toast 提示后继续呼叫\n· 修复未校准确认按钮时静默成功的 bug，改为明确报错\n· 修复无障碍节点泄漏，长期使用更稳定\n· 校准服务改前台服务，切到微信后不再被系统杀\n· 校准悬浮窗 v2 重设计：底部圆角卡片、半透毛玻璃、紫色胶囊按钮\n· 快捷方式图标去掉右下角徽标，满幅渐变无白边\n· NodeFinder 防 use-after-recycle，视频按钮兜底加文本校验"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.4.1",
            date = "2026-08-01",
            changes = "· 修复删除联系人后桌面快捷方式图标残留问题（改用 disableShortcuts 真正移除）\n· 修复微信未安装时无障碍服务空转 60 秒问题（改为先确认微信可启动再武装服务）\n· startCall 返回 Boolean，服务未连接或呼叫中可向用户反馈而非静默丢弃\n· 替换已废弃的 getRunningServices API，改用静态标志位检测保活服务状态\n· 开机自启默认开启确认：确保老人开机即保活，注释与代码保持一致"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.4.0",
            date = "2026-07-31",
            changes = "· 全新 V2 界面设计：暗色优先主题，品牌紫 #9B8CFF 主色调\n· 联系人列表重设计：圆角卡片、8色渐变头像、胶囊形视频按钮\n· 保活页新增环形进度动画、渐变校准入口卡片\n· 应用图标升级为渐变背景（紫→青绿）\n· 底部导航栏适配 V2 设计规范"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.3.4",
            date = "2026-07-31",
            changes = "· 适配 v2 设计系统：主色 #9B8CFF，三级色 #FFB4A2，深色主题默认优先\n· 关闭 Material You 动态取色，品牌固定色确保全设备一致\n· 头像渐变色扩展为 8 色，与桌面快捷方式图标同源"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.3.3",
            date = "2026-07-31",
            changes = "· 退出 App 后从最近任务列表隐藏卡片，后台继续保活防误杀\n· 前台通知不可滑除，防止老人误清导致服务被杀\n· 从最近任务划掉时服务自动重启，保活更稳定\n· 保活设置页新增厂商自启动/电池优化引导"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.3.2",
            date = "2026-07-31",
            changes = "· 状态机并发加固，修复超时检查与状态转换竞态\n· 修复多处无障碍节点泄漏，长时间使用更稳定\n· 拨号失败自动重试一次，仍失败给出友好提示\n· 分阶段超时更精准，总超时 75s→60s，失败反馈更快\n· 手机显示设置（分辨率/字体）变化时提醒重新校准"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.3.1",
            date = "2026-07-30",
            changes = "· 60Hz 屏幕适配：手势时长 80→120ms，步骤延迟放宽\n· 修复取消后超时回调仍触发的竞态\n· 修复 VOIP 事件重复触发完成逻辑\n· 修复搜索结果页 root 为空时静默卡死\n· 总超时 60s→75s，单步 15s→18s（适配低端机）"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.3.0",
            date = "2026-07-30",
            changes = "· 架构重构：无障碍服务拆分为状态机+节点查找+悬浮窗三组件\n· 超时常量统一，消除魔法数字\n· 修复节点遍历内存泄漏\n· 状态机可独立单元测试"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangelogCard(
            version = "V1.2.0",
            date = "2026-07-30",
            changes = "· 微信升级检测：校准后微信更新会自动提示重新校准\n· 并发保护：呼叫中拒绝重复请求\n· R8 混淆 + targetSdk 35\n· 关于页内嵌更新日志"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 微信版本信息
        val localContext = LocalContext.current
        val wechatVersion = remember {
            com.elder.wechatvideo.util.WeChatVersionDetector.getVersion(localContext)
        }
        if (wechatVersion != null) {
            Text(
                text = "当前微信版本：$wechatVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ChangelogCard(version: String, date: String, changes: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$version  ($date)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = changes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 读取当前应用版本名。
 */
private fun getVersionName(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName, 0
        )
        packageInfo.versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }
}
