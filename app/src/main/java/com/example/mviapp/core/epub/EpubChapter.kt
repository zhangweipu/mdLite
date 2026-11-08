package com.example.mviapp.core.epub

/**
 * EPUB 章节信息（轻量数据类，仅保存必要信息）
 *
 * 内容按需懒加载，不一次性解析全部章节正文，降低内存占用。
 */
data class EpubChapter(
    /** 章节在 ZIP 中的路径，用于读取内容 */
    val path: String,
    /** 章节标题（从目录中解析） */
    val title: String,
    /** 章节在阅读顺序中的索引 */
    val index: Int,
)
