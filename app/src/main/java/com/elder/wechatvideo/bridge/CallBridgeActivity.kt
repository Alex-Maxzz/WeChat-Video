package com.elder.wechatvideo.bridge

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.elder.wechatvideo.R
import com.elder.wechatvideo.MainActivity
import com.elder.wechatvideo.data.ContactEntity
import com.elder.wechatvideo.data.ContactRepository
import com.elder.wechatvideo.service.WeChatAccessibilityService
import com.elder.wechatvideo.shortcut.ShortcutHelper
import com.elder.wechatvideo.util.PermissionUtils
import com.elder.wechatvideo.util.PositionConfig
import com.elder.wechatvideo.util.WeChatConstants
import com.elder.wechatvideo.util.WeChatVersionDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 呼叫中转 Activity
 *
 * 接收桌面快捷方式或 App 内按钮的点击 Intent，负责：
 * 1. 校验调用方合法性（action 正确 + target 联系人真实存在于本地数据库）——修复 B10
 * 2. 检查无障碍服务是否已开启
 * 3. 从数据库查询联系人备注名
 * 4. 直接启动微信主界面（不再使用已失效的 Deep Link）
 * 5. 通知无障碍服务搜索联系人并自动点击视频通话
 *
 * 本 Activity 为透明，执行完毕后立即 finish()
 */
@AndroidEntryPoint
class CallBridgeActivity : ComponentActivity() {

    @Inject
    lateinit var contactRepository: ContactRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---- B10 (a)：校验 Intent action 确为预期 action ----
        // 该 Activity exported=true（桌面快捷方式需要），任何 App 都可伪造 Intent 拉起，
        // 因此必须校验 action，否则不武装无障碍服务。保留 exported 但通过参数校验兜底。
        if (intent?.action != ShortcutHelper.ACTION_CALL_WECHAT) {
            Toast.makeText(this, R.string.invalid_action, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 1. 无障碍检查已移入协程内（带重试），解决重启后 Settings.Secure 延迟恢复的问题

        // 2. 检查按键校准是否已完成
        if (!PositionConfig.isCalibrated(this)) {
            Toast.makeText(this, R.string.calibration_not_done, Toast.LENGTH_LONG).show()
            // 打开 App 引导校准
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("navigate_to_calibration", true)
            }
            startActivity(intent)
            finish()
            return
        }

        // 2.5 检查微信是否在校准后升级。
        // 微信小版本升级极少改动核心入口坐标，不应阻断拨号（老人打电话优先）。
        // 仅弹 Toast 提示，数秒后自动消失；若真失败，状态机超时兜底会提示重试。
        if (WeChatVersionDetector.isUpgraded(this)) {
            Toast.makeText(this, "微信已更新，如拨号失败请重新校准", Toast.LENGTH_LONG).show()
        }

        // 2.6 检查设备显示参数是否在校准后变化（分辨率/DPI/字体缩放）。
        // 与微信升级不同：坐标已归一化存储，分辨率变化可自动换算，失效风险较低，
        // 故仅提示不阻断拨号（老人拨电话优先），并更新已记录参数避免每次重复提示。
        if (PositionConfig.isDeviceParamsChanged(this)) {
            Toast.makeText(this, "手机显示设置有变化，建议重新校准按键位置", Toast.LENGTH_LONG).show()
            PositionConfig.saveDeviceParams(this)
        }

        // 3. 先启动微信主界面，确认成功后再武装无障碍服务
        lifecycleScope.launch {
            // ---- 无障碍服务检查（带重试，解决重启后延迟恢复） ----
            var a11yEnabled = false
            repeat(6) { attempt ->
                if (PermissionUtils.isAccessibilityServiceEnabled(
                        this@CallBridgeActivity, WeChatAccessibilityService::class.java
                    )) {
                    a11yEnabled = true
                    return@repeat
                }
                if (attempt < 5) delay(500)
            }
            if (!a11yEnabled) {
                Toast.makeText(this@CallBridgeActivity, R.string.accessibility_not_enabled, Toast.LENGTH_LONG).show()
                PermissionUtils.openAccessibilitySettings(this@CallBridgeActivity)
                finish()
                return@launch
            }

            // ---- B10 (b)：校验传入的 target 联系人在 Room 数据库中真实存在 ----
            val contact = resolveContact()
            if (contact == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CallBridgeActivity, R.string.invalid_contact, Toast.LENGTH_LONG).show()
                    finish()
                }
                return@launch
            }

            // 用于搜索的名字：优先备注名，其次联系人姓名（以数据库为准，避免伪造 extra）
            val searchName = contact.wechatRemark.ifBlank { contact.name }

            // 先启动微信主界面，确认微信可用后再武装无障碍服务
            val wechatLaunched = launchWeChatMain()
            if (!wechatLaunched) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CallBridgeActivity,
                        R.string.call_failed,
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                return@launch
            }

            // ---- 等待无障碍服务绑定（重启后系统需要几秒重新 bind） ----
            var callAccepted = false
            repeat(6) { attempt ->
                callAccepted = WeChatAccessibilityService.startCall(searchName)
                if (callAccepted) return@repeat
                if (attempt < 5) delay(500)
            }
            if (!callAccepted) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CallBridgeActivity,
                        "无障碍服务启动中，请稍后再试",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                return@launch
            }

            // 显示呼叫提示
            if (contact.name.isNotBlank()) {
                Toast.makeText(
                    this@CallBridgeActivity,
                    getString(R.string.calling, contact.name),
                    Toast.LENGTH_SHORT
                ).show()
            }

            finish()
        }
    }

    /**
     * 根据 Intent 中尽可能多的标识（id / wxid / 备注名 / 姓名），
     * 在本地数据库中解析出真实存在的联系人。
     *
     * 任一标识命中即视为合法；全部缺失或全部未命中则返回 null（伪造/无效请求）。
     *
     * @return 真实存在的联系人，或 null
     */
    private suspend fun resolveContact(): ContactEntity? {
        // 1. 优先按联系人主键 id 查询（快捷方式始终携带）
        val contactId = intent.getLongExtra(ShortcutHelper.EXTRA_CONTACT_ID, -1L)
        if (contactId > 0) {
            contactRepository.getContactById(contactId)?.let { return it }
        }

        // 2. 按微信号（wxid）查询
        val wxid = intent.getStringExtra(ShortcutHelper.EXTRA_WECHAT_ID)
        if (!wxid.isNullOrBlank()) {
            contactRepository.getContactByWxId(wxid)?.let { return it }
        }

        // 3. 按微信备注名查询
        val remark = intent.getStringExtra(ShortcutHelper.EXTRA_WECHAT_REMARK)
        if (!remark.isNullOrBlank()) {
            contactRepository.getContactByRemark(remark)?.let { return it }
        }

        // 4. 按联系人姓名查询
        val name = intent.getStringExtra(ShortcutHelper.EXTRA_CONTACT_NAME)
        if (!name.isNullOrBlank()) {
            contactRepository.getContactByName(name)?.let { return it }
        }

        return null
    }

    /**
     * 直接启动微信主界面（LauncherUI）
     *
     * 不再使用 weixin:// Deep Link（微信 8.0.76 已失效），
     * 改为直接拉起微信主界面，由无障碍服务搜索联系人。
     */
    private fun launchWeChatMain(): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    WeChatConstants.WECHAT_PACKAGE,
                    WeChatConstants.WECHAT_LAUNCHER
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            true
        } catch (e: Exception) {
            // 微信未安装
            false
        }
    }
}
