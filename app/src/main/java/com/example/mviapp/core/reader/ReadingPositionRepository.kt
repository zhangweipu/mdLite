package com.example.mviapp.core.reader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 文档的阅读进度（滚动位置） */
@Serializable
data class SavedScrollPosition(
    val index: Int,
    val offset: Int,
)

/** 每条文档对应的进度 */
@Serializable
private data class PositionRecord(
    val source: String,
    val index: Int,
    val offset: Int,
)

/**
 * 阅读位置数据访问契约
 *
 * 按文档来源（assets 路径或 content:// URI）记住上次阅读位置，
 * 再次打开同一文档时自动恢复。
 */
interface ReadingPositionRepository {

    /** 保存某文档的阅读位置 */
    suspend fun save(source: String, index: Int, offset: Int)

    /** 读取某文档的阅读位置，无记录返回 null */
    suspend fun get(source: String): SavedScrollPosition?
}

/**
 * 基于 SharedPreferences + JSON 的实现
 */
@Singleton
class SharedPreferencesReadingPositionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReadingPositionRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun save(source: String, index: Int, offset: Int) = withContext(Dispatchers.IO) {
        val updated = buildList {
            add(PositionRecord(source = source, index = index, offset = offset))
            addAll(readAll().filter { it.source != source })
        }.take(MAX_RECORDS)
        prefs.edit()
            .putString(KEY_POSITIONS, json.encodeToString(ListSerializer(PositionRecord.serializer()), updated))
            .apply()
    }

    override suspend fun get(source: String): SavedScrollPosition? = withContext(Dispatchers.IO) {
        readAll()
            .firstOrNull { it.source == source }
            ?.let { SavedScrollPosition(index = it.index, offset = it.offset) }
    }

    private fun readAll(): List<PositionRecord> {
        val raw = prefs.getString(KEY_POSITIONS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PositionRecord.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS_NAME = "reading_positions"
        const val KEY_POSITIONS = "positions"
        const val MAX_RECORDS = 100
    }
}
