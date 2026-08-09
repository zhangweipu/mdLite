package com.example.mviapp.core.marks

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 一条段落标记（高亮） */
@Serializable
data class ParagraphHighlight(
    val blockIndex: Int,
    val preview: String,
    val createdAt: Long,
)

/** 一条书签（定位到某个内容块） */
@Serializable
data class Bookmark(
    val blockIndex: Int,
    val preview: String,
    val createdAt: Long,
)

/**
 * 阅读标记数据访问契约
 *
 * 按文档来源（assets 路径或 content:// URI）保存：
 * - 段落标记：长按段落高亮；
 * - 书签：在阅读位置添加定位书签。
 */
interface ReadingMarkRepository {

    /** 某文档的全部段落标记 */
    suspend fun getHighlights(source: String): List<ParagraphHighlight>

    /** 切换某段的标记状态，返回更新后的标记列表 */
    suspend fun toggleHighlight(source: String, blockIndex: Int, preview: String): List<ParagraphHighlight>

    /** 某文档的全部书签（按添加时间倒序） */
    suspend fun getBookmarks(source: String): List<Bookmark>

    /** 添加书签（同一位置去重置顶），返回更新后的书签列表 */
    suspend fun addBookmark(source: String, blockIndex: Int, preview: String): List<Bookmark>

    /** 删除指定位置的书签 */
    suspend fun removeBookmark(source: String, blockIndex: Int): List<Bookmark>
}

/** 基于 SharedPreferences + JSON 的实现 */
@Singleton
class SharedPreferencesReadingMarkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReadingMarkRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun getHighlights(source: String): List<ParagraphHighlight> = withContext(Dispatchers.IO) {
        marks(source).highlights.sortedBy { it.blockIndex }
    }

    override suspend fun toggleHighlight(
        source: String,
        blockIndex: Int,
        preview: String,
    ): List<ParagraphHighlight> = withContext(Dispatchers.IO) {
        val data = marks(source)
        val highlights = if (data.highlights.any { it.blockIndex == blockIndex }) {
            data.highlights.filterNot { it.blockIndex == blockIndex }
        } else {
            data.highlights + ParagraphHighlight(
                blockIndex = blockIndex,
                preview = preview,
                createdAt = System.currentTimeMillis(),
            )
        }
        save(source, data.copy(highlights = highlights))
        highlights.sortedBy { it.blockIndex }
    }

    override suspend fun getBookmarks(source: String): List<Bookmark> = withContext(Dispatchers.IO) {
        marks(source).bookmarks.sortedByDescending { it.createdAt }
    }

    override suspend fun addBookmark(
        source: String,
        blockIndex: Int,
        preview: String,
    ): List<Bookmark> = withContext(Dispatchers.IO) {
        val data = marks(source)
        val bookmarks = listOf(
            Bookmark(
                blockIndex = blockIndex,
                preview = preview,
                createdAt = System.currentTimeMillis(),
            ),
        ) + data.bookmarks.filterNot { it.blockIndex == blockIndex }
        save(source, data.copy(bookmarks = bookmarks))
        bookmarks
    }

    override suspend fun removeBookmark(source: String, blockIndex: Int): List<Bookmark> =
        withContext(Dispatchers.IO) {
            val data = marks(source)
            val bookmarks = data.bookmarks.filterNot { it.blockIndex == blockIndex }
            save(source, data.copy(bookmarks = bookmarks))
            bookmarks
        }

    private fun marks(source: String): MarksData {
        val raw = prefs.getString(source, null) ?: return MarksData()
        return runCatching { json.decodeFromString<MarksData>(raw) }
            .getOrDefault(MarksData())
    }

    private fun save(source: String, data: MarksData) {
        prefs.edit()
            .putString(source, json.encodeToString(MarksData.serializer(), data))
            .apply()
    }

    @Serializable
    private data class MarksData(
        val highlights: List<ParagraphHighlight> = emptyList(),
        val bookmarks: List<Bookmark> = emptyList(),
    )

    private companion object {
        const val PREFS_NAME = "reading_marks"
    }
}
