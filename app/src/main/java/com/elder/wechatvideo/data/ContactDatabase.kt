package com.elder.wechatvideo.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room 数据库抽象类
 * 应用的主数据库，包含联系人表
 */
@Database(
    entities = [ContactEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ContactDatabase : RoomDatabase() {

    /**
     * 获取联系人 DAO
     */
    abstract fun contactDao(): ContactDao

    companion object {
        /** 数据库名称 */
        const val DATABASE_NAME = "wechat_video.db"

        // B14 迁移策略：版本升级必须提供显式 Migration（见 DatabaseModule）。
        // 不要启用 fallbackToDestructiveMigration()，以免用户联系人被静默清空。
        // 若未来修改表结构，请在此处递增 version 并在 DatabaseModule.addMigrations(...)
        // 中提供对应的 Migration 实现（保持字段与实体定义一致）。
    }
}
