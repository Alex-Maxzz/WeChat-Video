package com.elder.wechatvideo.ui.screens.contacts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elder.wechatvideo.data.ContactEntity
import com.elder.wechatvideo.data.ContactRepository
import com.elder.wechatvideo.shortcut.ShortcutHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 联系人列表页 UI 状态。
 *
 * @property loading 是否正在加载
 * @property contacts 联系人列表
 * @property showDeleteDialog 待删除确认对话框对应的联系人，null 表示不显示
 */
data class ContactListUiState(
    val loading: Boolean = true,
    val contacts: List<ContactEntity> = emptyList(),
    val showDeleteDialog: ContactEntity? = null
)

/**
 * 联系人列表页 ViewModel。
 *
 * 通过 [ContactRepository] 订阅联系人列表，并提供删除联系人、
 * 切换桌面快捷方式的能力。
 *
 * @param repository 联系人仓库
 * @param context 应用上下文，用于调用 [ShortcutHelper]
 */
@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val repository: ContactRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactListUiState())
    val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
        // B16：用户真正把图标放到桌面后，由 ShortcutResultReceiver 转发至此，
        // 此时才把 shortcutPinned 写回数据库，避免"取消却已标记"。
        ShortcutHelper.onPinConfirmed = { id ->
            viewModelScope.launch { markPinned(id, true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 释放静态回调，避免持有已销毁的 ViewModel
        ShortcutHelper.onPinConfirmed = null
    }

    /**
     * 订阅数据库中的联系人列表，自动响应数据变化。
     */
    fun loadContacts() {
        repository.getAllContacts()
            .onEach { contacts ->
                _uiState.update { it.copy(loading = false, contacts = contacts) }
            }
            .catch { error ->
                _uiState.update { it.copy(loading = false) }
                error.printStackTrace()
            }
            .launchIn(viewModelScope)
    }

    /**
     * 显示删除确认对话框。
     */
    fun requestDelete(contact: ContactEntity) {
        _uiState.update { it.copy(showDeleteDialog = contact) }
    }

    /**
     * 取消删除确认对话框。
     */
    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = null) }
    }

    /**
     * 删除指定联系人，并同步移除其桌面快捷方式（若已固定）。
     */
    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            if (contact.shortcutPinned) {
                runCatching { ShortcutHelper.unpinShortcut(context, contact) }
            }
            repository.delete(contact)
            _uiState.update { it.copy(showDeleteDialog = null) }
        }
    }

    /**
     * 切换联系人的桌面快捷方式状态：
     * - 已固定 -> 取消固定（立即写回数据库）
     * - 未固定 -> 请求添加到桌面（是否标记以用户确认放置为准，见 [markPinned]）
     *
     * 操作结果会写回数据库，UI 通过 Flow 自动刷新。
     */
    fun toggleShortcut(contact: ContactEntity) {
        viewModelScope.launch {
            val willPin = !contact.shortcutPinned
            if (willPin) {
                // 发起请求；不乐观标记，等系统回调 onPinConfirmed 确认用户真正放置后才写 DB。
                // 避免"用户取消系统弹窗却显示已固定"的不一致。
                // 少数 ROM 不发送回调时，用户可手动再次点击切换。
                runCatching { ShortcutHelper.pinShortcut(context, contact) }
            } else {
                runCatching { ShortcutHelper.unpinShortcut(context, contact) }
                markPinned(contact.id, false)
            }
        }
    }

    /**
     * 按 ID 更新快捷方式固定状态（用于系统回调确认 / 主动移除）。
     */
    private suspend fun markPinned(contactId: Long, pinned: Boolean) {
        val contact = repository.getContactById(contactId) ?: return
        if (contact.shortcutPinned != pinned) {
            repository.update(contact.copy(shortcutPinned = pinned))
        }
    }
}
