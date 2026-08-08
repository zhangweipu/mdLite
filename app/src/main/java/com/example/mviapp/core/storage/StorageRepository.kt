package com.example.mviapp.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地存储浏览数据访问契约
 *
 * 基于 Storage Access Framework（SAF），用户通过系统选择器授权某个文件夹后，
 * 无需申请存储权限即可递归浏览子文件夹并读取 md 文件。
 */
interface StorageRepository {

    /** 列出文件夹内容，仅保留子文件夹与 .md 文件 */
    suspend fun listDirectory(uri: Uri): StorageDirectory

    /** 读取文件内容（content:// URI） */
    suspend fun readFile(uri: Uri): String

}

/** 一次文件夹浏览的结果 */
data class StorageDirectory(
    val name: String,
    val entries: List<FileEntry>,
)

/**
 * 基于 DocumentsContract + ContentResolver 的实现
 *
 * 支持两种输入：
 * - 系统选择器返回的 tree URI（content://.../tree/...）
 * - 浏览过程中子文件夹的 document URI（content://.../document/...），自动转为 tree
 */
@Singleton
class AndroidStorageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : StorageRepository {

    override suspend fun listDirectory(uri: Uri): StorageDirectory = withContext(Dispatchers.IO) {
        val entries = listChildren(uri)

        StorageDirectory(
            name = displayName(uri),
            entries = entries,
        )
    }

    override suspend fun readFile(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("无法打开文件")
        } catch (e: SecurityException) {
            throw SecurityException("文件权限已失效，请重新通过文件选择器打开该文件", e)
        }
    }

    // ==================== 内部工具 ====================

    private fun listChildren(uri: Uri): List<FileEntry> {
        val authority = uri.authority ?: error("无法访问该文件夹")
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrNull()
            ?: error("无法访问该文件夹")
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, docId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val entries = mutableListOf<FileEntry>()

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val childDocId = cursor.getString(idColumn) ?: continue
                val mime = cursor.getString(mimeColumn)
                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDirectory || name.endsWith(".md", ignoreCase = true)) {
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    entries += FileEntry(
                        name = name,
                        uri = childUri.toString(),
                        isDirectory = isDirectory,
                    )
                }
            }
        } ?: error("无法读取文件夹内容")

        return entries.sortedWith(
            compareBy<FileEntry> { !it.isDirectory }
                .thenBy { it.name.lowercase() },
        )
    }

    private fun displayName(uri: Uri): String {
        val decoded = Uri.decode(uri.lastPathSegment.orEmpty())
        val name = decoded
            .substringAfterLast(':')
            .substringAfterLast('/')
        return name.ifBlank { "本机存储" }
    }
}
