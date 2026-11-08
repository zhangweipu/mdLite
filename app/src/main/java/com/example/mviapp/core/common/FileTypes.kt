package com.example.mviapp.core.common

/** 是否为 Markdown 文件 */
fun String.isMarkdownFile(): Boolean =
    endsWith(".md", ignoreCase = true) || endsWith(".markdown", ignoreCase = true)

/** 是否为 HTML 文件 */
fun String.isHtmlFile(): Boolean =
    endsWith(".html", ignoreCase = true) || endsWith(".htm", ignoreCase = true)

/** 是否为纯文本文件 */
fun String.isTxtFile(): Boolean =
    endsWith(".txt", ignoreCase = true)

/** 是否为 PDF 文件 */
fun String.isPdfFile(): Boolean =
    endsWith(".pdf", ignoreCase = true)

/** 是否为 EPUB 文件 */
fun String.isEpubFile(): Boolean =
    endsWith(".epub", ignoreCase = true)

/** 是否为阅读器支持的文档文件（md / html / txt / pdf / epub） */
fun String.isSupportedDocFile(): Boolean =
    isMarkdownFile() || isHtmlFile() || isTxtFile() || isPdfFile() || isEpubFile()
