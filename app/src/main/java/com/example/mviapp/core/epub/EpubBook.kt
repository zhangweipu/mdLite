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

/**
 * 一章的渲染数据。
 *
 * [baseUrl] 指向该章在缓存目录中的位置，WebView 据此加载书内的图片与 CSS；
 * 资源尚未解压完成时为空，此时仍能正常显示正文文字。
 * [fixedLayout] 为固定版式（漫画/画册），WebView 需要整体缩放适配屏宽。
 */
data class EpubChapterPage(
    val html: String,
    val baseUrl: String?,
    val fixedLayout: Boolean = false,
    /** 所属章节索引：同一章因资源补齐 / 字号切换重灌时据此保留滚动位置 */
    val chapterIndex: Int = -1,
)
