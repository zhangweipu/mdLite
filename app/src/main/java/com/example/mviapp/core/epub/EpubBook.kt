package com.example.mviapp.core.epub

/**
 * EPUB 书籍元数据（轻量，只含展示和导航必需信息）
 */
data class EpubBook(
    /** 书名 */
    val title: String,
    /** 作者（可能为空） */
    val author: String?,
    /** 章节列表（按阅读顺序） */
    val chapters: List<EpubChapter>,
    /** ZIP 根目录（OPF 文件所在目录的相对路径） */
    val rootDir: String,
)
