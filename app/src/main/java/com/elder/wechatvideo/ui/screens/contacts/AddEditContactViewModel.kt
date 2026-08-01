package com.elder.wechatvideo.ui.screens.contacts

import android.content.Context
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 编辑页中从导航读取的联系人 ID 参数 key */
private const val SAVED_STATE_CONTACT_ID = "id"

/**
 * 添加 / 编辑联系人表单 UI 状态。
 *
 * @property loading 是否正在加载已有联系人（仅编辑模式）
 * @property isEdit 是否为编辑模式
 * @property name 姓名
 * @property wechatRemark 微信备注名
 * @property wechatId 微信号（可选）
 * @property phone 电话号码（可选）
 * @property avatarColorIndex 头像颜色索引
 * @property nameError 姓名是否为空错误
 * @property remarkError 微信备注名是否为空错误
 * @property finished 操作完成（保存或删除）后置 true，用于触发页面返回
 */
data class AddEditUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val name: String = "",
    val wechatRemark: String = "",
    val wechatId: String = "",
    val phone: String = "",
    val avatarColorIndex: Int = 0,
    val nameError: Boolean = false,
    val remarkError: Boolean = false,
    val finished: Boolean = false
)

/**
 * 添加 / 编辑联系人 ViewModel。
 *
 * 通过 [SavedStateHandle] 读取导航传入的联系人 ID（编辑模式），
 * 加载已有数据填充表单；保存时根据模式执行新增或更新。
 *
 * @param repository 联系人仓库
 * @param savedStateHandle 导航参数容器，含可选 "id"
 * @param context 应用上下文，用于 [ShortcutHelper] 同步快捷方式
 */
@HiltViewModel
class AddEditContactViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** 编辑模式下的联系人 ID；添加模式下为 -1L */
    private val contactId: Long = savedStateHandle.get<Long>(SAVED_STATE_CONTACT_ID) ?: -1L

    /** 是否为编辑模式 */
    private val isEditMode: Boolean = contactId > 0L

    private val _uiState = MutableStateFlow(AddEditUiState(loading = isEditMode, isEdit = isEditMode))
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    init {
        if (isEditMode) {
            loadContact(contactId)
        }
    }

    /**
     * 加载已有联系人信息到表单。
     */
    private fun loadContact(id: Long) {
        viewModelScope.launch {
            val contact = repository.getContactById(id)
            if (contact != null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        name = contact.name,
                        wechatRemark = contact.wechatRemark,
                        wechatId = contact.wechatId.orEmpty(),
                        phone = contact.phone.orEmpty(),
                        avatarColorIndex = contact.avatarColorIndex
                    )
                }
            } else {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    /* ===================== 表单字段更新 ===================== */

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameError = false) }
    }

    fun onRemarkChange(value: String) {
        _uiState.update { it.copy(wechatRemark = value, remarkError = false) }
    }

    fun onWechatIdChange(value: String) {
        _uiState.update { it.copy(wechatId = value) }
    }

    fun onPhoneChange(value: String) {
        _uiState.update { it.copy(phone = value) }
    }

    fun onAvatarColorChange(index: Int) {
        _uiState.update { it.copy(avatarColorIndex = index) }
    }

    /**
     * 保存联系人。新增模式执行插入，编辑模式执行更新。
     * 编辑模式下若已固定桌面快捷方式，则同步刷新快捷方式信息。
     *
     * @return true 表示校验通过并已发起保存
     */
    fun saveContact(): Boolean {
        val state = _uiState.value

        // 必填项校验
        val nameOk = state.name.isNotBlank()
        val remarkOk = state.wechatRemark.isNotBlank()
        if (!nameOk || !remarkOk) {
            _uiState.update {
                it.copy(nameError = !nameOk, remarkError = !remarkOk)
            }
            return false
        }

        viewModelScope.launch {
            val trimmedName = state.name.trim()
            val trimmedRemark = state.wechatRemark.trim()
            val wechatId = state.wechatId.trim().ifBlank { null }
            val phone = state.phone.trim().ifBlank { null }

            if (isEditMode) {
                val existing = repository.getContactById(contactId)
                val updated = (existing ?: ContactEntity(
                    id = contactId,
                    name = trimmedName,
                    wechatRemark = trimmedRemark
                )).copy(
                    name = trimmedName,
                    wechatRemark = trimmedRemark,
                    wechatId = wechatId,
                    phone = phone,
                    avatarColorIndex = state.avatarColorIndex
                )
                repository.update(updated)

                // 若已固定桌面图标，刷新快捷方式以同步姓名/头像色
                if (updated.shortcutPinned) {
                    runCatching { ShortcutHelper.pinShortcut(context, updated) }
                }
            } else {
                repository.insert(
                    ContactEntity(
                        name = trimmedName,
                        wechatRemark = trimmedRemark,
                        wechatId = wechatId,
                        phone = phone,
                        avatarColorIndex = state.avatarColorIndex
                    )
                )
            }
            _uiState.update { it.copy(finished = true) }
        }
        return true
    }

    /**
     * 删除当前编辑的联系人（仅编辑模式可用），并移除其桌面快捷方式。
     */
    fun deleteContact() {
        if (!isEditMode) return
        viewModelScope.launch {
            val contact = repository.getContactById(contactId) ?: return@launch
            if (contact.shortcutPinned) {
                runCatching { ShortcutHelper.unpinShortcut(context, contact) }
            }
            repository.delete(contact)
            _uiState.update { it.copy(finished = true) }
        }
    }
}
