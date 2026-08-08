package com.example.mviapp.core.history

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阅读记录数据访问契约
 */
interface ReadingHistoryRepository {

    /** 新增/更新一条记录（同来源去重并置顶） */
    suspend fun add(title: String, source: String)

    /** 重命名记录（保留原阅读时间，来源同步更新） */
    suspend fun rename(source: String, newSource: String, newTitle: String)

    /** 删除指定来源的记录 */
    suspend fun remove(source: String)

    /** 清空全部记录 */
    suspend fun clear()

    /** 获取最近 [limit] 条记录（按阅读时间倒序） */
    suspend fun getRecent(limit: Int): List<ReadingRecord>
}

/**
 * 基于 SharedPreferences + JSON 的实现
 *
 * 应用本地持久化，无需新增依赖。
 */
@Singleton
class SharedPreferencesHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReadingHistoryRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun add(title: String, source: String) = withContext(Dispatchers.IO) {
        val updated = buildList {
            add(ReadingRecord(title = title, source = source, readAt = System.currentTimeMillis()))
            addAll(readAll().filter { it.source != source })
        }.take(MAX_RECORDS)
        prefs.edit()
            .putString(KEY_RECORDS, json.encodeToString(ListSerializer(ReadingRecord.serializer()), updated))
            .apply()
    }

    override suspend fun rename(source: String, newSource: String, newTitle: String) =
        withContext(Dispatchers.IO) {
            val all = readAll()
            val existing = all.firstOrNull { it.source == source }
            val updated = buildList {
                add(
                    ReadingRecord(
                        title = newTitle,
                        source = newSource,
                        readAt = existing?.readAt ?: System.currentTimeMillis(),
                    ),
                )
                addAll(all.filter { it.source != source && it.source != newSource })
            }.take(MAX_RECORDS)
            saveAll(updated)
        }

    override suspend fun getRecent(limit: Int): List<ReadingRecord> = withContext(Dispatchers.IO) {
        // 按阅读时间倒序，后阅读的排在前面
        readAll()
            .sortedByDescending { it.readAt }
            .take(limit)
    }

    override suspend fun remove(source: String) = withContext(Dispatchers.IO) {
        val updated = readAll().filterNot { it.source == source }
        saveAll(updated)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        saveAll(emptyList())
    }

    private fun readAll(): List<ReadingRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ReadingRecord.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun saveAll(records: List<ReadingRecord>) {
        prefs.edit()
            .putString(KEY_RECORDS, json.encodeToString(ListSerializer(ReadingRecord.serializer()), records))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "reading_history"
        const val KEY_RECORDS = "records"
        const val MAX_RECORDS = 20
    }
}
