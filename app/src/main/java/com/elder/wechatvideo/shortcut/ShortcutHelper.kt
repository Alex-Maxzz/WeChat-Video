package com.elder.wechatvideo.shortcut

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.elder.wechatvideo.bridge.CallBridgeActivity
import com.elder.wechatvideo.data.ContactEntity
import com.elder.wechatvideo.ui.components.avatarChar

/**
 * 桌面快捷方式管理器
 *
 * 为每个联系人创建独立的桌面图标，点击即发起微信视频通话。
 * 使用 ShortcutManagerCompat + ShortcutInfoCompat 兼容 Android 7.0 及以上。
 *
 * 图标设计（v2）：
 * - 显示【名字最后一个字】，同姓多联系人（张大明/张小花）也能一眼区分
 * - 满幅渐变底（8 色，与 App 内头像同源）+ 白色居中大字 + 描影
 * - 无徽标，由系统启动器自行裁切形状，杜绝白边
 */
object ShortcutHelper {

    /** 快捷方式 Intent Action（与 AndroidManifest 中 CallBridgeActivity 的 intent-filter 一致） */
    const val ACTION_CALL_WECHAT = "com.elder.wechatvideo.CALL_WECHAT"

    /** Extra Key: 联系人 ID */
    const val EXTRA_CONTACT_ID = "contact_id"

    /** Extra Key: 联系人姓名 */
    const val EXTRA_CONTACT_NAME = "contact_name"

    /** Extra Key: 微信备注名 */
    const val EXTRA_WECHAT_REMARK = "wechat_remark"

    /** Extra Key: 微信 wxid */
    const val EXTRA_WECHAT_ID = "wechat_id"

    /**
     * 用户真正把图标放到桌面后触发的回调（由 [ShortcutResultReceiver] 转发）。
     * 由 ContactListViewModel 在初始化时赋值，用于把 shortcutPinned 写回数据库。
     */
    @Volatile
    var onPinConfirmed: ((Long) -> Unit)? = null

    /** 渐变色组（v2 设计系统 8 色，与 App 内头像 / Color.kt 同源） */
    private val GRADIENTS = listOf(
        intArrayOf(0xFF7C6BFF.toInt(), 0xFFA78BFA.toInt()),  // 紫
        intArrayOf(0xFF0EA5E9.toInt(), 0xFF38BDF8.toInt()),  // 天蓝
        intArrayOf(0xFF10B59A.toInt(), 0xFF34D399.toInt()),  // 青绿
        intArrayOf(0xFFF59E0B.toInt(), 0xFFFBBF24.toInt()),  // 琥珀
        intArrayOf(0xFFEC4899.toInt(), 0xFFF472B6.toInt()),  // 粉
        intArrayOf(0xFF8B5CF6.toInt(), 0xFFC4B5FD.toInt()),  // 堇紫
        intArrayOf(0xFFEF4444.toInt(), 0xFFF87171.toInt()),  // 红
        intArrayOf(0xFF14B8A6.toInt(), 0xFF5EEAD4.toInt()),  // 碧
    )

    /**
     * 检查设备是否支持 requestPinShortcut
     */
    fun isPinnedShortcutSupported(context: Context): Boolean {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    }

    /**
     * 为联系人创建桌面快捷方式。
     *
     * 通过 [PendingIntent] 回调，仅在用户确认放置后才通知 [onPinConfirmed]，
     * 避免"用户取消却已标记已固定"的不一致。
     *
     * @return true 请求已发送（系统可能弹出确认对话框）
     */
    fun pinShortcut(context: Context, contact: ContactEntity): Boolean {
        if (!isPinnedShortcutSupported(context)) {
            return false
        }

        val shortcutId = getShortcutId(contact.id)
        val intent = createCallIntent(context, contact)
        val icon = createAvatarIcon(contact.name, contact.avatarColorIndex)

        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(contact.name)
            .setLongLabel("视频：${contact.name}")
            .setIcon(icon)
            .setIntent(intent)
            .build()

        // 系统回调：用户确认放置后触发
        val callbackIntent = Intent(context, ShortcutResultReceiver::class.java).apply {
            action = ShortcutResultReceiver.ACTION_PINNED
            putExtra(EXTRA_CONTACT_ID, contact.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            contact.id.toInt(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, pending.intentSender)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 移除联系人的桌面快捷方式。
     *
     * 使用 [ShortcutManagerCompat.disableShortcuts] 使已固定到桌面的图标变灰/移除，
     * 并从动态快捷方式列表中删除。仅用 removeDynamicShortcuts 无法移除已固定的桌面图标，
     * disableShortcuts 会使启动器将对应图标标记为不可用并自动隐藏。
     */
    fun unpinShortcut(context: Context, contact: ContactEntity) {
        val shortcutId = getShortcutId(contact.id)
        try {
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf(shortcutId),
                "联系人已删除"
            )
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
        } catch (e: Exception) {
            // 忽略移除失败
        }
    }

    /**
     * 更新已有快捷方式的信息（如姓名/头像变更后）
     */
    fun updateShortcut(context: Context, contact: ContactEntity) {
        val shortcutId = getShortcutId(contact.id)
        val intent = createCallIntent(context, contact)
        val icon = createAvatarIcon(contact.name, contact.avatarColorIndex)

        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(contact.name)
            .setLongLabel("视频：${contact.name}")
            .setIcon(icon)
            .setIntent(intent)
            .build()

        try {
            ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        } catch (e: Exception) {
            // 更新失败忽略
        }
    }

    /**
     * 生成快捷方式 ID
     */
    private fun getShortcutId(contactId: Long): String {
        return "contact_$contactId"
    }

    /**
     * 创建呼叫 Intent（指向 CallBridgeActivity）
     */
    private fun createCallIntent(context: Context, contact: ContactEntity): Intent {
        return Intent(context, CallBridgeActivity::class.java).apply {
            action = ACTION_CALL_WECHAT
            putExtra(EXTRA_CONTACT_ID, contact.id)
            putExtra(EXTRA_CONTACT_NAME, contact.name)
            contact.wechatRemark.let { putExtra(EXTRA_WECHAT_REMARK, it) }
            contact.wechatId?.let { putExtra(EXTRA_WECHAT_ID, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    /**
     * 生成联系人头像图标（v2：满幅渐变底 + 居中尾字 + 描影，无徽标）。
     *
     * 使用 192px 高分辨率位图，渐变铺满整个画布（无透明区域），
     * 由系统启动器自行裁切圆形/圆角方块，杜绝白边问题。
     */
    private fun createAvatarIcon(name: String, colorIndex: Int): IconCompat {
        val size = 192
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        val colors = GRADIENTS[((colorIndex % GRADIENTS.size) + GRADIENTS.size) % GRADIENTS.size]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 满幅渐变背景（铺满整个画布，不留透明区域）
        val shader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            colors[0], colors[1], Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // 名字最后一个字（白色大字，居中，带描影增强可读性）
        val ch = avatarChar(name, last = true)
        paint.shader = null
        paint.color = Color.WHITE
        paint.textSize = size * 0.52f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        // 描影：浅渐变底（琥珀/天蓝）上仍清晰
        paint.setShadowLayer(4f, 0f, 2f, 0x55000000)
        val textBounds = Rect()
        paint.getTextBounds(ch, 0, ch.length, textBounds)
        val baseline = cy + textBounds.height() / 2f - textBounds.bottom
        canvas.drawText(ch, cx, baseline, paint)

        return IconCompat.createWithBitmap(bitmap)
    }
}
