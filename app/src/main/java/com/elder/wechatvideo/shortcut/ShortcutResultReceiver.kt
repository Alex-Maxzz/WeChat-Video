package com.elder.wechatvideo.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elder.wechatvideo.shortcut.ShortcutHelper.EXTRA_CONTACT_ID

/**
 * 接收系统「用户已把快捷方式放到桌面」的回调广播，
 * 转发给 [ShortcutHelper.onPinConfirmed]，由 ViewModel 写回数据库。
 *
 * 这样可避免"用户取消放置却已标记已固定"的不一致问题。
 */
class ShortcutResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PINNED = "com.elder.wechatvideo.SHORTCUT_PINNED"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_PINNED) {
            val id = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
            if (id > 0) {
                ShortcutHelper.onPinConfirmed?.invoke(id)
            }
        }
    }
}
