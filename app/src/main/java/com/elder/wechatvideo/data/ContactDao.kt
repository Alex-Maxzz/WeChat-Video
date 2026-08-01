package com.elder.wechatvideo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 联系人数据访问对象（DAO）
 * 提供对 contacts 表的增删改查操作
 */
@Dao
interface ContactDao {

    /**
     * 获取所有联系人，按创建时间倒序排列
     * 返回 Flow 以支持响应式观察数据变化
     */
    @Query("SELECT * FROM contacts ORDER BY created_at DESC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    /**
     * 根据 ID 查询单个联系人
     * @param id 联系人主键
     * @return 联系人实体，不存在时返回 null
     */
    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: Long): ContactEntity?

    /**
     * 根据微信备注名查询单个联系人
     * @param remark 微信备注名
     * @return 联系人实体，不存在时返回 null
     */
    @Query("SELECT * FROM contacts WHERE wechat_remark = :remark LIMIT 1")
    suspend fun getContactByRemark(remark: String): ContactEntity?

    /**
     * 根据微信号（wxid）查询单个联系人。
     * 用于 CallBridgeActivity 校验外部拉起请求时传入的 wxid 是否真实存在。
     * @param wxid 微信号
     * @return 联系人实体，不存在时返回 null
     */
    @Query("SELECT * FROM contacts WHERE wechat_id = :wxid LIMIT 1")
    suspend fun getContactByWxId(wxid: String): ContactEntity?

    /**
     * 根据联系人姓名查询单个联系人。
     * 姓名可能重名，取最新创建的一条（与列表排序一致）。
     * @param name 联系人姓名
     * @return 联系人实体，不存在时返回 null
     */
    @Query("SELECT * FROM contacts WHERE name = :name ORDER BY created_at DESC LIMIT 1")
    suspend fun getContactByName(name: String): ContactEntity?

    /**
     * 插入一个联系人
     * @return 新插入记录的主键 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity): Long

    /**
     * 更新一个联系人
     */
    @Update
    suspend fun update(contact: ContactEntity)

    /**
     * 删除一个联系人
     */
    @Delete
    suspend fun delete(contact: ContactEntity)
}
