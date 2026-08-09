package com.example.mviapp.di

import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.history.SharedPreferencesHistoryRepository
import com.example.mviapp.core.marks.ReadingMarkRepository
import com.example.mviapp.core.marks.SharedPreferencesReadingMarkRepository
import com.example.mviapp.core.md.AssetsMdRepository
import com.example.mviapp.core.md.MdRepository
import com.example.mviapp.core.reader.ReadingPositionRepository
import com.example.mviapp.core.reader.SharedPreferencesReadingPositionRepository
import com.example.mviapp.core.storage.AndroidStorageRepository
import com.example.mviapp.core.storage.StorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块
 *
 * 将接口与实现绑定。Repository、DataSource 等依赖在此声明。
 * [SingletonComponent] 表示依赖在整个应用生命周期内单例。
 *
 * 新增 Repository 时：
 * 1. 在此添加 @Binds 绑定
 * 2. 如果实现类需要构造参数，改用 @Provides
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMdRepository(
        impl: AssetsMdRepository
    ): MdRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(
        impl: AndroidStorageRepository
    ): StorageRepository

    @Binds
    @Singleton
    abstract fun bindReadingHistoryRepository(
        impl: SharedPreferencesHistoryRepository
    ): ReadingHistoryRepository

    @Binds
    @Singleton
    abstract fun bindReadingPositionRepository(
        impl: SharedPreferencesReadingPositionRepository
    ): ReadingPositionRepository

    @Binds
    @Singleton
    abstract fun bindReadingMarkRepository(
        impl: SharedPreferencesReadingMarkRepository
    ): ReadingMarkRepository
}
