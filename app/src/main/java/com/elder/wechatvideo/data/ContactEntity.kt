package com.elder.wechatvideo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 联系人实体类
 * 对应数据库中的 contacts 表，存储微信视频通话联系人信息
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    /** 主键ID，自动生成 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 联系人姓名 */
    val name: String,

    /** 微信备注名，用于在微信中定位联系人 */
    @ColumnInfo(name = "wechat_remark")
    val wechatRemark: String,

    /** 微信ID（可选），如 wxid_xxx */
    @ColumnInfo(name = "wechat_id")
    val wechatId: String? = null,

    /** 电话号码（可选） */
    val phone: String? = null,

    /** 头像渐变色索引，默认为 0 */
    @ColumnInfo(name = "avatar_color_index")
    val avatarColorIndex: Int = 0,

    /** 是否已固定到桌面快捷方式，默认为 false */
    @ColumnInfo(name = "shortcut_pinned")
    val shortcutPinned: Boolean = false,

    /** 创建时间戳（毫秒） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
