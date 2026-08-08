package com.example.mviapp.core.storage

/**
 * 文件夹浏览中的一项（子文件夹或 md 文件）
 *
 * @param name        显示名称
 * @param uri         文档 URI（文件夹为 document uri，文件可直接用于读取）
 * @param isDirectory 是否为文件夹
 */
data class FileEntry(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
)
