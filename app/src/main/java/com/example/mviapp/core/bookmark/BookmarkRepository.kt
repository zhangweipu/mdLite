package com.example.mviapp.core.bookmark

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 书签仓库接口
 */
interface BookmarkRepository {

    /** 所有书签的 Flow（可观察） */
    val bookmarks: StateFlow<List<Bookmark>>

    /** 获取所有书签（按添加时间倒序） */
    suspend fun getAll(): List<Bookmark>

    /** 添加书签 */
    suspend fun add(title: String, url: String)

    /** 删除书签 */
    suspend fun remove(url: String)

    /** 是否已收藏 */
    suspend fun isBookmarked(url: String): Boolean

    /** 切换收藏状态（已收藏则取消，未收藏则添加） */
    suspend fun toggle(title: String, url: String): Boolean

    /** 获取上次访问的网址 */
    suspend fun getLastVisited(): String?

    /** 保存上次访问的网址 */
    suspend fun setLastVisited(url: String)
}

/**
 * 基于 SharedPreferences 的书签实现
 *
 * 轻量级存储，不依赖 Room 等数据库，低配设备也能流畅运行。
 */
@Singleton
class SharedPreferencesBookmarkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : BookmarkRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    override val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    init {
        _bookmarks.value = readAll().sortedByDescending { it.createdAt }
    }

    override suspend fun getAll(): List<Bookmark> = withContext(Dispatchers.IO) {
        readAll().sortedByDescending { it.createdAt }
    }

    override suspend fun add(title: String, url: String) {
        val id = url.toMd5()
        val existing = _bookmarks.value.firstOrNull { it.id == id }
        if (existing != null) return // 已存在则不重复添加

        val bookmark = Bookmark(
            id = id,
            title = title.ifBlank { url },
            url = url,
        )
        val updated = listOf(bookmark) + _bookmarks.value
        _bookmarks.value = updated
        withContext(Dispatchers.IO) { saveAll(updated) }
    }

    override suspend fun remove(url: String) {
        val id = url.toMd5()
        val updated = _bookmarks.value.filterNot { it.id == id }
        _bookmarks.value = updated
        withContext(Dispatchers.IO) { saveAll(updated) }
    }

    override suspend fun isBookmarked(url: String): Boolean {
        val id = url.toMd5()
        return _bookmarks.value.any { it.id == id }
    }

    override suspend fun toggle(title: String, url: String): Boolean {
        return if (isBookmarked(url)) {
            remove(url)
            false
        } else {
            add(title, url)
            true
        }
    }

    override suspend fun getLastVisited(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_LAST_VISITED, null)
    }

    override suspend fun setLastVisited(url: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_LAST_VISITED, url).apply()
        }
    }

    // ==================== 私有方法 ====================

    private fun readAll(): List<Bookmark> {
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Bookmark.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun saveAll(items: List<Bookmark>) {
        prefs.edit()
            .putString(KEY_BOOKMARKS, json.encodeToString(ListSerializer(Bookmark.serializer()), items))
            .apply()
    }

    private fun String.toMd5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS_NAME = "browser_data"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_LAST_VISITED = "last_visited"
    }
}
