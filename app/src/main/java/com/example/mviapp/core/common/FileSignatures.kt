package com.example.mviapp.core.common

/**
 * 文件头（magic number）识别。
 *
 * 下载完成后不能只看「有没有写出文件」：服务器返回 403 登录页、JSON 错误、
 * 空响应时，文件同样会被创建。用文件头判断拿到的到底是不是真正的 ZIP/EPUB，
 * 才能把「下载失败」和「EPUB 解析失败」区分开，并给出可操作的提示。
 */
object FileSignatures {

    /** PK\x03\x04（普通 ZIP）、PK\x05\x06（空 ZIP）、PK\x07\x08（分卷 ZIP） */
    fun isZip(head: ByteArray): Boolean {
        if (head.size < 4) return false
        if (head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) return false
        val third = head[2].toInt() and 0xFF
        val fourth = head[3].toInt() and 0xFF
        return (third == 0x03 && fourth == 0x04) ||
            (third == 0x05 && fourth == 0x06) ||
            (third == 0x07 && fourth == 0x08)
    }

    /** 把文件头翻译成人类可读的格式说明，用于错误提示 */
    fun describe(head: ByteArray): String {
        if (head.isEmpty()) return "空文件"
        if (isZip(head)) return "ZIP 压缩包"

        // 用 ISO-8859-1 逐字节解码，避免非文本字节造成异常或乱码
        val text = String(head, 0, head.size, Charsets.ISO_8859_1)
        val trimmed = text.trimStart('\uFEFF', '\u0000', ' ', '\r', '\n', '\t').lowercase()
        return when {
            trimmed.startsWith("<!doctype") || trimmed.startsWith("<html") -> "HTML 网页"
            trimmed.startsWith("<?xml") && trimmed.contains("<html") -> "XHTML 网页"
            trimmed.startsWith("{") || trimmed.startsWith("[") -> "JSON 文本"
            trimmed.startsWith("%pdf") -> "PDF 文件"
            trimmed.startsWith("\u001f\u008b") -> "GZIP 压缩数据"
            trimmed.startsWith("<?xml") -> "XML 文本"
            trimmed.startsWith("<") -> "标记语言文本"
            trimmed.all { it.isLetterOrDigit() || it in " \r\n\t.,;:!?-_=+/'\"()[]{}<>" } -> "纯文本"
            else -> "无法识别的二进制数据"
        }
    }

    /** 截取文件头中可打印的部分，便于在错误提示里展示证据 */
    fun preview(head: ByteArray, limit: Int = 24): String = buildString {
        for (byte in head) {
            val ch = (byte.toInt() and 0xFF).toChar()
            append(if (ch in ' '..'~') ch else '.')
            if (length >= limit) break
        }
    }
}
