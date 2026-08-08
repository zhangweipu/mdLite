package com.example.mviapp.core.md

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Markdown 数据访问契约
 *
 * UI 层不关心文档来源（assets / 网络 / 数据库）。
 */
interface MdRepository {

    /** 获取可阅读的 Markdown 文档列表 */
    suspend fun getFiles(): List<MdFile>

    /** 读取指定路径（assets 相对路径）的 Markdown 原文 */
    suspend fun readFile(filePath: String): String

    /** 通过 content:// URI 读取 Markdown 原文（本地存储文件） */
    suspend fun readUri(uri: Uri): String
}

/**
 * 基于 assets 目录的实现
 *
 * 文档约定放在 `app/src/main/assets/md/` 下，启动时自动扫描并提取标题与简介。
 */
@Singleton
class AssetsMdRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : MdRepository {

    override suspend fun getFiles(): List<MdFile> = withContext(Dispatchers.IO) {
        context.assets.list(MD_DIR)
            ?.filter { it.endsWith(".md") }
            ?.sorted()
            ?.map { fileName ->
                val content = readAsset("$MD_DIR/$fileName")
                MdFile(
                    fileName = fileName,
                    title = content.firstHeading() ?: fileName.removeSuffix(".md"),
                    description = content.firstParagraph().orEmpty(),
                )
            }
            .orEmpty()
    }

    override suspend fun readFile(filePath: String): String = withContext(Dispatchers.IO) {
        readAsset(filePath)
    }

    override suspend fun readUri(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("无法打开文件")
        } catch (e: SecurityException) {
            throw SecurityException("文件权限已失效，请重新通过文件选择器打开该文件", e)
        }
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    /** 提取第一个一级标题（# xxx） */
    private fun String.firstHeading(): String? =
        lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()

    /** 提取第一段非空正文，用于列表简介 */
    private fun String.firstParagraph(): String? =
        lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotEmpty() &&
                    !line.startsWith("#") &&
                    !line.startsWith("```") &&
                    !line.startsWith(">")
            }

    private companion object {
        const val MD_DIR = "md"
    }
}
