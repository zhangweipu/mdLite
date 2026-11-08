package com.example.mviapp.core.epub

import com.example.mviapp.core.common.percentDecode

/**
 * EPUB 内部路径工具（纯 Kotlin，不依赖 Android，便于 JVM 单元测试）。
 *
 * EPUB 里的路径问题非常多见，是「目录能出来、正文打不开」的主要原因：
 * - OPF/NCX 的 href 常带 `%20`、`%E4%B8%AD%E6%96%87` 等百分号编码
 * - href 相对的是 OPF 所在目录，而不是 ZIP 根目录
 * - 部分书用 `../`、Windows 反斜杠、锚点 `#section2`
 * - ZIP 条目名与 href 的大小写可能不一致
 */
object EpubPaths {

    /**
     * 把 OPF/NCX 里的相对 href 与 OPF 所在目录合并，解析成 ZIP 内的路径。
     *
     * @param baseDir OPF 所在目录（形如 `OEBPS` 或 `OPS/Text`，可为空）
     * @param rawHref 原始 href，可带锚点、query、百分号编码
     */
    fun resolve(baseDir: String, rawHref: String): String {
        val rel = clean(rawHref, decodePercent = true)
        val base = clean(baseDir, decodePercent = true)
            .split('/')
            .filter { it.isNotEmpty() && it != "." }
            .toMutableList()

        for (part in rel.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (base.isNotEmpty()) base.removeAt(base.lastIndex)
                else -> base.add(part)
            }
        }
        return base.joinToString("/")
    }

    /**
     * 规范化 ZIP 条目名：统一分隔符、去掉前导 `/` 与 `./`、消解 `..`。
     * 同时用于 Zip Slip 防护（路径永远不会越出缓存目录）。
     * 这里不做百分号解码，避免误伤条目名里真实的 `%` 字符。
     */
    fun entry(rawName: String): String {
        val cleaned = rawName.replace('\\', '/').trim().trimStart('/')
        val out = ArrayList<String>()
        for (part in cleaned.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                else -> out.add(part)
            }
        }
        return out.joinToString("/")
    }

    /** 判断两个 ZIP 路径是否等价（忽略大小写与百分号编码差异） */
    fun matches(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        return a.percentDecode().equals(b.percentDecode(), ignoreCase = true)
    }

    /** 取路径的目录部分（不含尾斜杠），根目录返回空串 */
    fun parentDir(path: String): String = path.substringBeforeLast('/', "")

    private fun clean(raw: String, decodePercent: Boolean): String {
        val value = if (decodePercent) raw.percentDecode() else raw
        return value.trim()
            .substringBefore('#')
            .substringBefore('?')
            .replace('\\', '/')
            .trimStart('/')
            .trim('"', '\'')
    }
}
