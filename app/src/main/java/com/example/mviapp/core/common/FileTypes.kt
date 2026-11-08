package com.example.mviapp.core.common

/**
 * 从文件路径 / content URI / 文件名中提取可用于判断扩展名的部分。
 *
 * 之所以不能直接 `endsWith(".epub")`：
 * SAF（系统文件选择器）返回的 URI 形如
 * `content://com.android.externalstorage.documents/document/primary%3ABooks%2Fnovel.epub`，
 * 需要解码后取最后一段；部分 provider 甚至只返回数字 ID，此时由调用方用文件名兜底。
 */
internal fun String.documentName(): String =
    percentDecode()
        .substringBefore('?')
        .substringBefore('#')
        .replace('\\', '/')
        .substringAfterLast('/')
        .substringAfterLast(':')
        .trim()

/**
 * 纯 Kotlin 的百分号解码（不依赖 android.net.Uri，因此可在 JVM 单测中直接运行）。
 * 按 UTF-8 字节解码，正确处理中文文件名（%E4%B8%AD…）。
 */
internal fun String.percentDecode(): String {
    if (indexOf('%') < 0) return this
    val out = java.io.ByteArrayOutputStream(length)
    val plain = StringBuilder()

    fun flush() {
        if (plain.isNotEmpty()) {
            val bytes = plain.toString().toByteArray(Charsets.UTF_8)
            out.write(bytes, 0, bytes.size)
            plain.setLength(0)
        }
    }

    var i = 0
    while (i < length) {
        val ch = this[i]
        if (ch == '%' && i + 2 < length) {
            val hi = Character.digit(this[i + 1], 16)
            val lo = Character.digit(this[i + 2], 16)
            if (hi >= 0 && lo >= 0) {
                flush()
                out.write((hi shl 4) or lo)
                i += 3
                continue
            }
        }
        plain.append(ch)
        i++
    }
    flush()
    return String(out.toByteArray(), Charsets.UTF_8)
}

/** 是否为 Markdown 文件 */
fun String.isMarkdownFile(): Boolean = documentName().let {
    it.endsWith(".md", ignoreCase = true) || it.endsWith(".markdown", ignoreCase = true)
}

/** 是否为 HTML 文件 */
fun String.isHtmlFile(): Boolean = documentName().let {
    it.endsWith(".html", ignoreCase = true) || it.endsWith(".htm", ignoreCase = true)
}

/** 是否为纯文本文件 */
fun String.isTxtFile(): Boolean = documentName().endsWith(".txt", ignoreCase = true)

/** 是否为 PDF 文件 */
fun String.isPdfFile(): Boolean = documentName().endsWith(".pdf", ignoreCase = true)

/** 是否为 EPUB 文件 */
fun String.isEpubFile(): Boolean = documentName().endsWith(".epub", ignoreCase = true)

/** 是否为阅读器支持的文档文件（md / html / txt / pdf / epub） */
fun String.isSupportedDocFile(): Boolean =
    isMarkdownFile() || isHtmlFile() || isTxtFile() || isPdfFile() || isEpubFile()
