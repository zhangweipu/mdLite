package com.example.mviapp.core.md

/**
 * 轻量 HTML → Markdown 转换器
 *
 * 让阅读器可以直接阅读 .html 文件：把常见 HTML 标签转换为 Markdown 语法，
 * 再交给 [MarkdownParser] 统一渲染。支持标题、段落、列表、代码块、引用、表格、
 * 链接、粗体/斜体/行内代码等常用标签；script/style 内容会被忽略。
 */
object HtmlToMarkdown {

    fun convert(html: String): String {
        val cleaned = html
            .replace(COMMENT, "")
            .replace(SCRIPT_STYLE, "")

        val sb = StringBuilder()
        var inPre = false
        var tableHeader = true
        var headerCols = 0
        var pendingHref: String? = null

        for (token in TOKEN.findAll(cleaned)) {
            val raw = token.value
            if (raw.startsWith("<")) {
                val tag = raw
                    .trimStart('<')
                    .trimEnd('>')
                    .let { if (it.startsWith('/')) it.drop(1) else it }
                    .substringBefore(' ')
                    .substringBefore('\t')
                    .substringBefore('\n')
                    .lowercase()
                val closing = raw.startsWith("</")

                when (tag) {
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        if (closing) {
                            ensureNewline(sb)
                        } else {
                            ensureNewline(sb)
                            repeat(tag[1].digitToInt()) { sb.append('#') }
                            sb.append(' ')
                        }
                    }
                    "p", "div", "section", "article", "header", "footer", "main", "ul", "ol" -> {
                        if (!closing) ensureNewline(sb)
                    }
                    "li" -> {
                        if (!closing) {
                            ensureNewline(sb)
                            sb.append("- ")
                        }
                    }
                    "blockquote" -> {
                        if (!closing) {
                            ensureNewline(sb)
                            sb.append("> ")
                        }
                    }
                    "pre" -> {
                        if (!closing) {
                            ensureNewline(sb)
                            sb.append("```\n")
                            inPre = true
                        } else {
                            ensureNewline(sb)
                            sb.append("```")
                            inPre = false
                        }
                    }
                    "code" -> if (!inPre) sb.append('`')
                    "br" -> if (!closing) sb.append('\n')
                    "strong", "b" -> sb.append("**")
                    "em", "i" -> sb.append("*")
                    "a" -> {
                        if (!closing) {
                            pendingHref = attribute(raw, "href")
                            sb.append('[')
                        } else {
                            val href = pendingHref.orEmpty()
                            pendingHref = null
                            sb.append("]($href)")
                        }
                    }
                    "img" -> {
                        val src = attribute(raw, "src")
                        val alt = attribute(raw, "alt")
                        if (src.isNotBlank()) sb.append("[${alt.ifBlank { src }}]($src)")
                    }
                    "hr" -> {
                        if (!closing) {
                            ensureNewline(sb)
                            sb.append("---")
                            ensureNewline(sb)
                        }
                    }
                    "table" -> {
                        if (!closing) {
                            ensureNewline(sb)
                            tableHeader = true
                            headerCols = 0
                        }
                    }
                    "thead", "tbody", "tfoot" -> {}
                    "tr" -> {
                        if (!closing) {
                            ensureNewline(sb)
                            sb.append("| ")
                        } else if (tableHeader) {
                            ensureNewline(sb)
                            sb.append('|')
                            repeat(headerCols) { sb.append(" --- |") }
                            tableHeader = false
                        }
                    }
                    "th", "td" -> {
                        if (closing) {
                            sb.append(" |")
                        } else if (tag == "th") {
                            headerCols++
                        }
                    }
                    else -> {}
                }
            } else {
                val text = decodeEntities(raw)
                sb.append(if (inPre) text else collapseWhitespace(text))
            }
        }
        return sb.toString().trim()
    }

    // ==================== 工具 ====================

    private fun ensureNewline(sb: StringBuilder) {
        if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
    }

    private fun collapseWhitespace(text: String): String =
        text.replace(Regex("\\s+"), " ")

    private fun attribute(tag: String, name: String): String {
        val regex = Regex("""$name\s*=\s*["']([^"']*)["']|$name\s*=\s*([^\s"'>]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(tag) ?: return ""
        return (match.groupValues[1].ifEmpty { match.groupValues[2] }).let(::decodeEntities)
    }

    private fun decodeEntities(text: String): String {
        var result = text
        for ((entity, replacement) in ENTITY_MAP) {
            result = result.replace(entity, replacement)
        }
        result = result.replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }
        result = result.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
        return result
    }

    private val ENTITY_MAP = mapOf(
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
        "&nbsp;" to " ",
        "&mdash;" to "—",
        "&ndash;" to "–",
        "&hellip;" to "…",
        "&copy;" to "©",
        "&reg;" to "®",
        "&trade;" to "™",
        "&times;" to "×",
        "&divide;" to "÷",
        "&middot;" to "·",
        "&bull;" to "•",
        "&laquo;" to "«",
        "&raquo;" to "»",
        "&lsquo;" to "'",
        "&rsquo;" to "'",
        "&ldquo;" to "\"",
        "&rdquo;" to "\"",
        "&rarr;" to "→",
        "&larr;" to "←",
    )

    private val COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val SCRIPT_STYLE = Regex("""(?is)<(script|style)[^>]*>.*?</\1>""")
    private val TOKEN = Regex("<[^>]+>|[^<]+")
}
