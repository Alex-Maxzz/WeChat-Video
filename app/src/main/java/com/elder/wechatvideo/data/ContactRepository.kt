package com.elder.wechatvideo.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 联系人仓库类
 * 封装 DAO 操作，为上层（ViewModel 等）提供统一的数据访问入口
 * 通过 Hilt 注入 ContactDao
 */
@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao
) {

    /**
     * 获取所有联系人，返回 Flow 以支持响应式观察数据变化
     */
    fun getAllContacts(): Flow<List<ContactEntity>> = contactDao.getAllContacts()

    /**
     * 根据 ID 查询单个联系人
     * @return 联系人实体，不存在时返回 null
     */
    suspend fun getContactById(id: Long): ContactEntity? = contactDao.getContactById(id)

    /**
     * 根据微信备注名查询单个联系人
     * @return 联系人实体，不存在时返回 null
     */
    suspend fun getContactByRemark(remark: String): ContactEntity? =
        contactDao.getContactByRemark(remark)

    /**
     * 根据微信号（wxid）查询单个联系人。
     * @return 联系人实体，不存在时返回 null
     */
    suspend fun getContactByWxId(wxid: String): ContactEntity? =
        contactDao.getContactByWxId(wxid)

    /**
     * 根据联系人姓名查询单个联系人。
     * @return 联系人实体，不存在时返回 null
     */
    suspend fun getContactByName(name: String): ContactEntity? =
        contactDao.getContactByName(name)

    /**
     * 插入一个联系人
     * @return 新插入记录的主键 ID
     */
    suspend fun insert(contact: ContactEntity): Long = contactDao.insert(contact)

    /**
     * 更新一个联系人
     */
    suspend fun update(contact: ContactEntity) = contactDao.update(contact)

    /**
     * 删除一个联系人
     */
    suspend fun delete(contact: ContactEntity) = contactDao.delete(contact)
}
