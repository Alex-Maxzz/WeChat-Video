# 微信视频 · ElderWeChatVideo

> 面向老年人的**微信视频通话辅助** Android 应用。通过系统无障碍服务（AccessibilityService）识别并驱动微信界面，配合悬浮校准层，让老人"一键"发起微信视频通话；联系人本地存储，通话流程由状态机驱动。

- **包名**：`com.elder.wechatvideo`
- **版本**：`1.7.0_Alis`（versionCode 35）
- **最低/目标 SDK**：`minSdk 26` / `compileSdk & targetSdk 35`
- **技术栈**：Kotlin 2.0.20 · Jetpack Compose · Hilt · Room · Navigation Compose · ML Kit 中文 OCR

---

## 功能特性

- 📞 **一键视频通话**：自动在微信中找到指定联系人并发起视频通话
- 👵 **大字大点**：专为老人优化的 Compose 界面（大字体、大按钮、高对比）
- 📇 **本地联系人管理**：增删改查，数据存于本地 Room 数据库（不上云）
- 🎯 **按键位置校准**：首次使用引导校准悬浮层与微信界面坐标偏移
- 🔋 **保活与自启**：前台保活服务 + 开机自启广播，确保随时可用
- 🔍 **端侧 OCR 兜底**：ML Kit 中文文字识别，作为节点定位失败时的兜底方案

---

## 系统架构

采用**分层架构**，UI 与业务逻辑解耦，核心通话能力下沉到 `core` 与 `service` 层，数据通过 Room + Hilt 注入。

```mermaid
flowchart TB
  subgraph UI["UI 层 (Jetpack Compose)"]
    Screens["screens/* 各页面 + ViewModel"]
    Nav["navigation/AppNavigation"]
    Theme["theme/* 主题"]
    Widgets["widgets/* CrosshairView / TapMarkView"]
  end
  subgraph Domain["领域/核心层 (core)"]
    SM["CallStateMachine 通话状态机"]
    NF["NodeFinder 节点定位"]
    OCR["OcrHelper 端侧 OCR"]
    OV["OverlayController 浮层控制"]
  end
  subgraph Svc["系统服务层"]
    AS["WeChatAccessibilityService 无障碍驱动"]
    COS["CalibrationOverlayService 校准浮层"]
    KA["KeepAliveService 保活"]
    BR["BootReceiver 开机自启"]
    BRIDGE["CallBridgeActivity 桥接入口"]
  end
  subgraph Data["数据层 (Room + Hilt)"]
    REPO["ContactRepository"]
    DAO["ContactDao"]
    DB["ContactDatabase"]
    MOD["DatabaseModule (Hilt)"]
  end
  subgraph Util["工具层"]
    U["WeChatConstants / VersionDetector / PermissionUtils / PositionConfig / SettingsPrefs / KeepAliveStatus"]
  end

  UI --> Domain
  UI --> Svc
  Svc --> Domain
  Domain --> Data
  Domain --> Util
  Data --> MOD
```

**数据流简述**：用户在 UI 选择联系人 → `CallBridgeActivity` 拉起通话 → `WeChatAccessibilityService` 接管微信界面 → `NodeFinder`/`OcrHelper` 定位"视频通话"按钮 → `CallStateMachine` 推进通话状态 → `OverlayController` 显示校准/状态浮层。

---

## 模块 / 包结构

| 包路径 | 职责 |
|--------|------|
| `com.elder.wechatvideo` | 应用入口 `ElderWeChatApp`（继承 `HiltAndroidApplication`） |
| `.bridge` | `CallBridgeActivity`：拉起通话的桥接/入口 Activity |
| `.core` | 通话核心：`CallStateMachine`（状态机）、`NodeFinder`（节点查找）、`OcrHelper`（OCR）、`OverlayController`（浮层控制） |
| `.data` | 联系人持久化：`ContactEntity` / `ContactDao` / `ContactDatabase` / `ContactRepository` |
| `.data.di` | `DatabaseModule`：Hilt 提供 Room 依赖 |
| `.keepalive` | 保活：`AutostartPrefs` / `BootReceiver` / `KeepAliveService` |
| `.service` | `WeChatAccessibilityService`（核心无障碍驱动）、`CalibrationOverlayService`（校准浮层） |
| `.shortcut` | `ShortcutHelper` / `ShortcutResultReceiver`：桌面快捷方式 |
| `.ui` | Compose UI：`navigation`、`screens`（各页面 + ViewModel）、`theme`、`components`、`widgets` |
| `.util` | 常量与工具：`WeChatConstants`、`WeChatVersionDetector`、`PermissionUtils`、`PositionConfig`、`SettingsPrefs`、`KeepAliveStatus` |
| `.widget` | 自定义 View：`CrosshairView`、`TapMarkView` |

**目录树（核心源码）**：

```
app/src/main/
├── AndroidManifest.xml
├── java/com/elder/wechatvideo/
│   ├── ElderWeChatApp.kt
│   ├── MainActivity.kt
│   ├── bridge/CallBridgeActivity.kt
│   ├── core/{CallStateMachine,NodeFinder,OcrHelper,OverlayController}.kt
│   ├── data/{ContactEntity,ContactDao,ContactDatabase,ContactRepository}.kt
│   ├── data/di/DatabaseModule.kt
│   ├── keepalive/{AutostartPrefs,BootReceiver,KeepAliveService}.kt
│   ├── service/{WeChatAccessibilityService,CalibrationOverlayService}.kt
│   ├── shortcut/{ShortcutHelper,ShortcutResultReceiver}.kt
│   ├── ui/components/{AvatarChar,AvatarView}.kt
│   ├── ui/navigation/AppNavigation.kt
│   ├── ui/screens/{about,calibration,contacts,keepalive,settings}/...
│   ├── ui/theme/{Color,Theme,Type}.kt
│   ├── util/{KeepAliveStatus,PermissionUtils,PositionConfig,SettingsPrefs,WeChatConstants,WeChatVersionDetector}.kt
│   └── widget/{CrosshairView,TapMarkView}.kt
├── res/  (drawable / mipmap / values / xml)
└── test/...  (单元测试)
```

---

## 技术栈

| 类别 | 选型 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.20 |
| UI | Jetpack Compose (Material3) | BOM 2024.09.02 / M3 1.3.0 |
| 导航 | Navigation Compose | 2.8.0 |
| 依赖注入 | Hilt | 2.52 |
| 本地存储 | Room | 2.6.1 |
| 图片加载 | Coil | 2.7.0 |
| 端侧 OCR | ML Kit text-recognition-chinese | 16.0.1 |
| 构建 | Android Gradle Plugin / KSP | 8.5.2 / 2.0.20-1.0.25 |
| 测试 | JUnit4（纯 JVM 单测） | 4.13.2 |

---

## 构建与运行

**前置条件**

- JDK 17
- Android SDK（项目 `compileSdk = 35`，构建工具见 `gradle.properties` 中 `android.aapt2FromMavenOverride` 指向本地 build-tools 34.0.0）
- Android 8.0+（API 26）真机用于调试无障碍能力

**步骤**

1. 克隆仓库
2. 在根目录创建 `local.properties` 并写入本机 SDK 路径：
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
   （该文件已被 `.gitignore` 忽略，不会入库）
3. 打开 Android Studio，等待 Gradle 同步
4. 连接真机（API 26+），运行 `app` 模块

> ⚠️ **关于 Gradle Wrapper**：`gradle-wrapper.jar` 为二进制文件，未纳入版本库。克隆后请在本机执行 `gradle wrapper`（或复制本机已有 wrapper jar 到 `gradle/wrapper/`）以使用 `./gradlew`；也可直接用系统已安装的 Gradle 构建。

---

## 权限与隐私说明

本应用为辅助老人使用微信而设计，**需要以下敏感能力，请知悉**：

- **无障碍服务（AccessibilityService）**：用于识别并操作微信界面以发起通话。需在系统设置中手动开启，应用不会在后台静默开启。开启后该服务可"观察/操作"屏幕上其他应用（微信）的界面。
- **保活与前台服务**：`KeepAliveService` 以前台服务形式保活，`BootReceiver` 在开机后自启，目的是让老人随时能一键通话。
- **本地优先**：联系人数据仅存于本机 Room 数据库，**核心通话流程不依赖网络上传**；OCR 由 ML Kit 在端侧完成。

请仅在本人设备/授权设备上使用，并遵守微信相关使用规范。

---

## 测试

单元测试位于 `app/src/test/java/...`，使用 JUnit4（纯 JVM，无需 Robolectric）：

- `core/OcrHelperTest.kt`
- `util/KeepAliveStatusTest.kt`
- `util/PositionConfigTest.kt`

运行：`./gradlew :app:testDebugUnitTest`

---

## 版本更新记录

> 完整版可交互文档见 [`docs/版本更新文档.html`](docs/版本更新文档.html)

### V1.7.0_Alis（2026-08-06）`当前版本`

- 🔧 **fail() 竞态导致取消/失败后仍可能误点击（P0）**：`sm.resetToIdle()` 延迟 3 秒，期间 OCR/盲戳检查 `sm.isActive` 仍为 true 会误点击。改为立即切 IDLE
- 🔧 **resetSearchForRetry 漏重置 searchClickRetries**：搜索结果超时重试时计数不清零，累计触发"搜索按钮无法打开"误报
- 🧹 清理死代码：删除未调用的 AutoDialCard、失效的浮窗位置固定开关、8 个零引用僵尸常量

### V1.6.9_Alis（2026-08-03）

- 🔧 **重启后快捷方式误报"无障碍未开启"（P0）**：设备重启后 `Settings.Secure` 可能暂时为空，但无障碍服务已通过 `onServiceConnected` 绑定。第1道检测增加 `isConnected` 运行时标志作为替代判据，任一为 true 即通过

### V1.6.8_Alis（2026-08-03）

- 🔧 **聊天页判断假阴性（P1）**：`hasBottomInputBar` 阈值 0.6→0.4，兜底键盘弹起场景（输入框被顶到 44% 不再漏判）
- 🔧 **OCR 分段标题匹配不一致（P2）**：`OcrHelper` 仍用子串匹配会误识别"没有联系人匹配结果"，改为与 `NodeFinder` 一致的精确匹配

### V1.6.7_Alis（2026-08-03）

- 🔧 **落点校验永远失败（P0）**：微信新版聊天页按钮全是图标，旧文字关键词失效。改用 `content-desc` 特征 + 底部 `EditText` 结构检测
- 🔧 **失败后无法重试（P1）**：`fail()` 中 `pendingCall` 立即清除，不再延迟 3 秒
- 🔧 **+ 按钮 resource-id 过时（P2）**：`PLUS_BUTTON_RESOURCE_IDS` 新增 `bjz`

### V1.6.6_Alis（2026-08-03）

- 🔧 **OCR 协程与 timeoutChecker 竞态（P0）**：引入 `ocrInProgress` 标志阻止 OCR 期间搜索重试，OCR 完成后校验状态未变才执行点击
- 🔧 **分段标题子串匹配误命中（P1）**：改为精确匹配 + "标题 空格 数字"格式
- 🔧 **聊天页判断过于宽泛（P2）**：移除"发送"关键词，避免搜索页误判

### V1.6.5_Alis（2026-08-03）

- 🔧 **pendingCall 死锁（P0）**：`onServiceConnected` 中重置 `pendingCall`，服务崩溃重启后不再永久拒绝新呼叫
- 🔧 **+ 按钮/视频通话 root 为 null 不重试（P1）**：统一加 800ms 重试调度
- 🔧 **restartSearch() 重置不完整（P2）**：补全所有标志位和计数器重置
- ⚡ 无障碍服务健康监控：每 5 分钟检查连接状态，断开时主动通知
- ⚡ OCR 文本匹配切到 `Dispatchers.Default`，不再卡主线程

### V1.6.4_Alis（2026-08-02）

- 🔧 **搜索结果误点公众号**：分段标题检测改为子串匹配，"公众号 2"等不再漏识别
- 🔧 **OCR 永远不执行**：第1层节点查找假成功导致 OCR 被跳过，修复后正确降级
- 🔧 **OCR 误点公众号**：新增分段上下文校验，非联系人分段下的匹配行跳过
- 🔧 **OCR 期间无超时保护**：恢复 `startTimeoutChecker`

### V1.6.3_Alis（2026-08-02）

- 🔧 **OCR 严格模式失效**：搜索框文本被误判为联系人结果，修复匹配逻辑并新增 22 项单元测试
- 🔧 **重启后拨号误报"未开启"**：无障碍检查改为 500ms×6 次重试
- ⚡ OCR 匹配口径与无障碍节点查找对齐（精确/前缀），排除搜索框区域

### V1.6.2_Alis（2026-08-01）

- 🔧 **OCR 截图失败**：补全 `canTakeScreenshot` 权限声明
- 🔧 移除全部调试弹窗，取消按钮改为固定位置

### V1.6.1_Alis（2026-08-01）

- 🔧 保活服务冷启动缺口：打开 App 即启动前台服务
- 🔧 Direct Boot 崩溃：移除 `LOCKED_BOOT_COMPLETED`
- 🔧 节点泄漏：`clickNode` 统一回收
- 🔧 校准服务结束时先 `stopForeground` 再 `stopSelf`

### V1.6.0_Alis（2026-08-01）

- ✨ 新增设置页面：OCR 开关、严格模式、自动拨号、浮窗固定、主题切换
- ✨ 三层拨号防线：节点 → OCR → 坐标

### V1.5.0_Alis（2026-08-01）

- ✨ 新增 OCR 智能识别（ML Kit 端侧），三层兜底架构
- ✨ 拨号浮窗双窗口架构：显示层触摸穿透

---

## 设计文档

UI 与架构设计预览归档于 [`docs/design/`](docs/design/)：

- `架构示意.html` · `界面预览_架构对齐.html` · `设计系统预览v2.html` · `图标优化v2.html`
- `calibration/`：校准悬浮窗设计规范与预览

---

## 许可证

未指定许可证。如需开源，请补充 `LICENSE` 文件。
