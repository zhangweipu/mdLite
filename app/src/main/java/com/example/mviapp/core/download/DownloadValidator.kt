package com.example.mviapp.core.download

import com.example.mviapp.core.common.FileSignatures
import com.example.mviapp.core.common.documentName

/**
 * 下载结果校验（纯 Kotlin，不依赖 Android，便于 JVM 单测覆盖）。
 *
 * 旧实现只管把响应体抄进文件，抄完就标记「已完成」，于是：
 * - 服务器返回 0 字节 / 提前断开 → 0 字节文件被当成下载成功
 * - 403/登录页/JSON 错误页 → HTML 被存成 .epub，阅读器报「不是有效的 ZIP」
 *
 * 这里把这些问题在「落盘之前」拦下来，并给出能指导用户操作的原因。
 * 返回 null 表示校验通过。
 */
object DownloadValidator {

    private val TEXT_EXTENSIONS = setOf(
        "html", "htm", "xhtml", "txt", "md", "markdown",
        "json", "xml", "css", "js", "csv", "log",
    )
    private val TEXT_MIME_TYPES = setOf(
        "text/html", "application/xhtml+xml", "application/json", "text/plain",
    )

    fun validate(
        fileName: String,
        writtenBytes: Long,
        expectedBytes: Long,
        contentType: String?,
        head: ByteArray,
    ): String? {
        if (writtenBytes <= 0) {
            return "服务器返回了 0 字节内容，下载未完成（链接可能需要登录、有防盗链或已失效）"
        }
        if (expectedBytes > 0 && writtenBytes != expectedBytes) {
            return "下载不完整：应为 $expectedBytes 字节，实际 $writtenBytes 字节（连接可能中途断开）"
        }

        val extension = fileName.documentName().substringAfterLast('.', "").lowercase()
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase()

        // EPUB 以文件头为准：不少站点把 EPUB 的 Content-Type 错标成 text/html，
        // 但只要开头是合法 ZIP 就不能误杀；反过来，头不对就一定是假文件。
        if (extension == "epub") {
            if (!FileSignatures.isZip(head)) {
                val kind = FileSignatures.describe(head)
                val sample = FileSignatures.preview(head)
                return "文件内容是$kind，不是 EPUB 压缩包（EPUB 必须以 PK 开头）；" +
                    "开头内容为「$sample」。请确认链接指向文件本身而不是网页，然后重新下载"
            }
            return null
        }

        if (extension !in TEXT_EXTENSIONS && mime != null && mime in TEXT_MIME_TYPES) {
            return "服务器返回的是 $mime 文本，不是 $fileName（常见原因：需要登录、防盗链或链接已失效）"
        }
        return null
    }
}
