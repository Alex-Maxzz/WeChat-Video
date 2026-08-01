package com.elder.wechatvideo.data.di

import android.content.Context
import androidx.room.Room
import com.elder.wechatvideo.data.ContactDao
import com.elder.wechatvideo.data.ContactDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库 Hilt 模块
 * 提供 ContactDatabase 和 ContactDao 的单例依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * 提供 ContactDatabase 单例
     * @param context 应用上下文
     * @return ContactDatabase 实例
     */
    @Provides
    @Singleton
    fun provideContactDatabase(
        @ApplicationContext context: Context
    ): ContactDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = ContactDatabase::class.java,
            name = ContactDatabase.DATABASE_NAME
        )
            // B14：严禁 fallbackToDestructiveMigration()，否则版本升级时会静默清空联系人数据。
            // 改为显式 addMigrations()，让所有 schema 变更都必须编写对应 Migration 才能升级；
            // 当前 schema 版本为 1 且未变更，同版本不会触发迁移，因此此处先不传任何 Migration 也是安全的。
            // 日后若需要 bump 版本，请在此处追加 Migration(oldVersion, newVersion) 并手写迁移 SQL，
            // 切勿重新启用 destructive 回退。
            .addMigrations()
            .build()
    }

    /**
     * 提供 ContactDao
     * @param contactDatabase 数据库实例
     * @return ContactDao 实例
     */
    @Provides
    fun provideContactDao(contactDatabase: ContactDatabase): ContactDao {
        return contactDatabase.contactDao()
    }
}
