package com.example.mviapp.core.md

/**
 * 轻量 Markdown 解析器
 *
 * 支持常见语法：
 * - 标题（# ~ ######）
 * - 段落
 * - 行内格式：**粗体**、*斜体*、`行内代码`、[链接](url)
 * - 围栏代码块（```lang ... ```）
 * - 引用（>）
 * - 无序列表（- / * / +）与有序列表（1. 2. ...）
 * - 表格（GFM 风格，分隔行 :--- / ---: / :---: 支持列对齐）
 * - 分割线（--- / *** / ___）
 *
 * 暂不支持：嵌套列表、图片、转义字符等高级语法。
 */
object MarkdownParser {

    /** 行内元素 */
    sealed interface MdInline {
        data class Plain(val text: String) : MdInline
        data class Bold(val text: String) : MdInline
        data class Italic(val text: String) : MdInline
        data class Code(val text: String) : MdInline
        data class Link(val text: String, val url: String) : MdInline
    }

    /** 块级元素 */
    sealed interface MdBlock {
        data class Heading(val level: Int, val content: List<MdInline>) : MdBlock
        data class Paragraph(val content: List<MdInline>) : MdBlock
        data class CodeBlock(val code: String, val language: String?) : MdBlock
        data class Blockquote(val content: List<MdInline>) : MdBlock
        data class UnorderedList(val items: List<List<MdInline>>) : MdBlock
        data class OrderedList(val items: List<List<MdInline>>) : MdBlock
        data class Table(
            val header: List<List<MdInline>>,
            val alignments: List<TableAlignment>,
            val rows: List<List<List<MdInline>>>,
        ) : MdBlock
        data object HorizontalRule : MdBlock
    }

    /** 表格列对齐方式 */
    enum class TableAlignment { Left, Center, Right }

    fun parse(markdown: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = markdown.split("\n")
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            when {
                line.isBlank() -> index++

                isFenceStart(line) -> {
                    val (code, nextIndex) = readFencedCode(lines, index)
                    val language = line.trim().removePrefix("```").trim().ifEmpty { null }
                    blocks += MdBlock.CodeBlock(code = code, language = language)
                    index = nextIndex
                }

                isHeading(line) -> {
                    val level = line.takeWhile { it == '#' }.length
                    blocks += MdBlock.Heading(
                        level = level,
                        content = parseInlines(line.drop(level).trim()),
                    )
                    index++
                }

                isTableStart(lines, index) -> {
                    val header = splitTableRow(lines[index])
                    val alignments = splitTableRow(lines[index + 1]).map(::parseAlignment)
                    val rows = mutableListOf<List<String>>()
                    index += 2
                    while (index < lines.size && isTableRow(lines[index])) {
                        rows += splitTableRow(lines[index])
                        index++
                    }
                    blocks += MdBlock.Table(
                        header = header.map { parseInlines(it) },
                        alignments = alignments,
                        rows = rows.map { row ->
                            val padded = (row + List(header.size - row.size) { "" }).take(header.size)
                            padded.map { parseInlines(it) }
                        },
                    )
                }

                isHorizontalRule(line) -> {
                    blocks += MdBlock.HorizontalRule
                    index++
                }

                isBlockquote(line) -> {
                    val quoted = mutableListOf<String>()
                    while (index < lines.size && isBlockquote(lines[index])) {
                        quoted += lines[index].trim().removePrefix(">").trim()
                        index++
                    }
                    blocks += MdBlock.Blockquote(parseInlines(quoted.joinToString(" ")))
                }

                isUnorderedItem(line) -> {
                    val items = mutableListOf<String>()
                    while (index < lines.size && isUnorderedItem(lines[index])) {
                        val content = lines[index].trim()
                            .substringAfter("-")
                            .substringAfter("*")
                            .substringAfter("+")
                            .trim()
                        items += content
                        index++
                    }
                    blocks += MdBlock.UnorderedList(items.map { parseInlines(it) })
                }

                isOrderedItem(line) -> {
                    val items = mutableListOf<String>()
                    while (index < lines.size && isOrderedItem(lines[index])) {
                        items += lines[index].trim().replace(ORDERED_MARKER, "").trim()
                        index++
                    }
                    blocks += MdBlock.OrderedList(items.map { parseInlines(it) })
                }

                else -> {
                    // 连续非空、非特殊行合并为一个段落
                    val paragraphLines = mutableListOf(line)
                    index++
                    while (index < lines.size && isParagraphLine(lines[index])) {
                        paragraphLines += lines[index]
                        index++
                    }
                    blocks += MdBlock.Paragraph(parseInlines(paragraphLines.joinToString(" ")))
                }
            }
        }
        return blocks
    }

    /** 解析行内格式，返回行内元素序列 */
    fun parseInlines(text: String): List<MdInline> {
        val result = mutableListOf<MdInline>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            val match = INLINE_TOKEN.find(remaining)
            if (match == null) {
                result += MdInline.Plain(remaining)
                break
            }
            if (match.range.first > 0) {
                result += MdInline.Plain(remaining.substring(0, match.range.first))
            }
            result += toInline(match.value)
            remaining = remaining.substring(match.range.last + 1)
        }
        return result
    }

    /**
     * 计算章节块索引。
     *
     * 规则：优先取拥有至少两个标题的最高层级（# > ## > ### ...）作为章节，
     * 避免“单个 # 文档标题 + 多个 ## 小节”时只剩一个点的问题；
     * 若每个层级都只有一个标题，则回退到第一个标题的层级。
     */
    fun chapterBlockIndexes(blocks: List<MdBlock>): List<Int> {
        val headings = blocks.mapIndexedNotNull { index, block ->
            (block as? MdBlock.Heading)?.let { index to it.level }
        }
        if (headings.isEmpty()) return emptyList()
        val chapterLevel = (1..6).firstOrNull { level ->
            headings.count { it.second == level } >= 2
        } ?: headings.first().second
        return headings.filter { it.second == chapterLevel }.map { it.first }
    }

    /** 生成块级元素的一行摘要文本（用于标记/书签的预览） */
    fun blockPreview(block: MdBlock): String {
        val text = when (block) {
            is MdBlock.Heading -> inlinesText(block.content)
            is MdBlock.Paragraph -> inlinesText(block.content)
            is MdBlock.CodeBlock -> block.code.lineSequence().firstOrNull().orEmpty()
            is MdBlock.Blockquote -> inlinesText(block.content)
            is MdBlock.UnorderedList -> block.items.firstOrNull().let { inlinesText(it.orEmpty()) }
            is MdBlock.OrderedList -> block.items.firstOrNull().let { inlinesText(it.orEmpty()) }
            is MdBlock.Table -> block.header.joinToString(" | ") { inlinesText(it) }
            MdBlock.HorizontalRule -> ""
        }
        return text.lineSequence().firstOrNull().orEmpty().trim().take(40)
    }

    private fun inlinesText(inlines: List<MdInline>): String =
        inlines.joinToString("") { inline ->
            when (inline) {
                is MdInline.Plain -> inline.text
                is MdInline.Bold -> inline.text
                is MdInline.Italic -> inline.text
                is MdInline.Code -> inline.text
                is MdInline.Link -> inline.text
            }
        }

    // ==================== 行内解析 ====================

    private fun toInline(token: String): MdInline = when {
        token.startsWith("`") -> MdInline.Code(token.drop(1).dropLast(1))
        token.startsWith("**") -> MdInline.Bold(token.drop(2).dropLast(2))
        token.startsWith("*") -> MdInline.Italic(token.drop(1).dropLast(1))
        else -> {
            val match = LINK_PATTERN.find(token)
            if (match != null) {
                MdInline.Link(
                    text = match.groupValues[1],
                    url = match.groupValues[2],
                )
            } else {
                MdInline.Plain(token)
            }
        }
    }

    // ==================== 块级判断 ====================

    private fun isHeading(line: String): Boolean =
        HEADING.matches(line)

    private fun isHorizontalRule(line: String): Boolean =
        HORIZONTAL_RULE.matches(line.trim())

    private fun isBlockquote(line: String): Boolean =
        line.trimStart().startsWith(">")

    private fun isTableRow(line: String): Boolean =
        line.trim().contains('|')

    private fun isTableSeparator(line: String): Boolean =
        TABLE_SEPARATOR.matches(line.trim()) && line.contains('-')

    private fun isTableStart(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        if (!isTableRow(lines[index])) return false
        if (!isTableSeparator(lines[index + 1])) return false
        return splitTableRow(lines[index]).size == splitTableRow(lines[index + 1]).size
    }

    private fun splitTableRow(line: String): List<String> {
        val content = line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .trim()
        return content.split('|').map { it.trim() }
    }

    private fun parseAlignment(cell: String): TableAlignment {
        val trimmed = cell.trim()
        val left = trimmed.startsWith(":")
        val right = trimmed.endsWith(":")
        return when {
            left && right -> TableAlignment.Center
            right -> TableAlignment.Right
            else -> TableAlignment.Left
        }
    }

    private fun isUnorderedItem(line: String): Boolean =
        line.trim().startsWith("- ") ||
            line.trim().startsWith("* ") ||
            line.trim().startsWith("+ ")

    private fun isOrderedItem(line: String): Boolean =
        ORDERED_MARKER.containsMatchIn(line.trim())

    private fun isFenceStart(line: String): Boolean =
        line.trim().startsWith("```")

    private fun isParagraphLine(line: String): Boolean =
        line.isNotBlank() &&
            !isHeading(line) &&
            !isHorizontalRule(line) &&
            !isBlockquote(line) &&
            !isUnorderedItem(line) &&
            !isOrderedItem(line) &&
            !isFenceStart(line)

    private fun readFencedCode(lines: List<String>, start: Int): Pair<String, Int> {
        val codeLines = mutableListOf<String>()
        var index = start + 1
        while (index < lines.size && !isFenceStart(lines[index])) {
            codeLines += lines[index]
            index++
        }
        // 跳过结束围栏（若存在）
        return codeLines.joinToString("\n") to (index + 1).coerceAtMost(lines.size)
    }

    // ==================== 正则 ====================

    private val INLINE_TOKEN = Regex("""(`[^`\n]+`|\*\*[^*\n]+\*\*|\*[^*\n]+\*|\[[^\]\n]+]\([^)\n]+\))""")
    private val LINK_PATTERN = Regex("""^\[([^\]]+)]\(([^)]+)\)$""")
    private val ORDERED_MARKER = Regex("""^\d+\.\s+""")
    private val HORIZONTAL_RULE = Regex("""(-{3,}|\*{3,}|_{3,})""")
    private val HEADING = Regex("""#{1,6}\s+.*""")
    private val TABLE_SEPARATOR = Regex("""^\|?[\s:|-]+\|?$""")
}
