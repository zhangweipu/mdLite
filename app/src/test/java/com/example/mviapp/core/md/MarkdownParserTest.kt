package com.example.mviapp.core.md

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parse heading paragraph and inline styles`() {
        val blocks = MarkdownParser.parse(
            "# 标题\n" +
                "\n" +
                "这是**粗体**和*斜体*与`代码`，[链接](https://example.com)",
        )

        val heading = blocks[0] as MarkdownParser.MdBlock.Heading
        assertEquals(1, heading.level)
        assertEquals(
            listOf(MarkdownParser.MdInline.Plain("标题")),
            heading.content,
        )

        val paragraph = blocks[1] as MarkdownParser.MdBlock.Paragraph
        assertEquals(
            listOf(
                MarkdownParser.MdInline.Plain("这是"),
                MarkdownParser.MdInline.Bold("粗体"),
                MarkdownParser.MdInline.Plain("和"),
                MarkdownParser.MdInline.Italic("斜体"),
                MarkdownParser.MdInline.Plain("与"),
                MarkdownParser.MdInline.Code("代码"),
                MarkdownParser.MdInline.Plain("，"),
                MarkdownParser.MdInline.Link("链接", "https://example.com"),
            ),
            paragraph.content,
        )
    }

    @Test
    fun `parse code block lists and blockquote`() {
        val blocks = MarkdownParser.parse(
            "```kotlin\nval x = 1\n```\n" +
                "\n" +
                "- 甲\n- 乙\n" +
                "\n" +
                "1. 第一\n2. 第二\n" +
                "\n" +
                "> 引用内容",
        )

        val codeBlock = blocks[0] as MarkdownParser.MdBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertEquals("val x = 1", codeBlock.code)

        val unordered = blocks[1] as MarkdownParser.MdBlock.UnorderedList
        assertEquals(2, unordered.items.size)

        val ordered = blocks[2] as MarkdownParser.MdBlock.OrderedList
        assertEquals(2, ordered.items.size)

        val quote = blocks[3] as MarkdownParser.MdBlock.Blockquote
        assertTrue(quote.content.isNotEmpty())
    }

    @Test
    fun `parse horizontal rule`() {
        val blocks = MarkdownParser.parse("---")
        assertTrue(blocks.single() is MarkdownParser.MdBlock.HorizontalRule)
    }

    @Test
    fun `parse gfm table with alignments`() {
        val markdown = """
            | 功能 | 状态 | 优先级 |
            | :--- | ---: | :---: |
            | 表格 | 完成 | 高 |
            | 图片 | 未做 | 低 |
        """.trimIndent()

        val blocks = MarkdownParser.parse(markdown)
        val table = blocks.single() as MarkdownParser.MdBlock.Table

        assertEquals(
            listOf("功能", "状态", "优先级"),
            table.header.map { (it.single() as MarkdownParser.MdInline.Plain).text },
        )
        assertEquals(
            listOf(
                MarkdownParser.TableAlignment.Left,
                MarkdownParser.TableAlignment.Right,
                MarkdownParser.TableAlignment.Center,
            ),
            table.alignments,
        )
        assertEquals(2, table.rows.size)
        assertEquals(
            listOf("表格", "完成", "高"),
            table.rows[0].map { (it.single() as MarkdownParser.MdInline.Plain).text },
        )
    }

    @Test
    fun `parse table rows without outer pipes and pad missing cells`() {
        val markdown = "名称 | 说明\n--- | ---\n核心 | 解析表格\n缺列 | "

        val blocks = MarkdownParser.parse(markdown)
        val table = blocks.single() as MarkdownParser.MdBlock.Table

        assertEquals(2, table.rows.size)
        assertEquals(
            listOf("核心", "解析表格"),
            table.rows[0].map { (it.single() as MarkdownParser.MdInline.Plain).text },
        )
        // 缺列时补空单元格，保持列数一致
        assertEquals(2, table.rows[1].size)
        assertEquals(
            "缺列",
            (table.rows[1][0].single() as MarkdownParser.MdInline.Plain).text,
        )
        assertTrue(table.rows[1][1].isEmpty())
    }

    @Test
    fun `table requires matching separator cells`() {
        val blocks = MarkdownParser.parse("a | b\n---")

        // 分隔行只有一个单元格时不应视为表格，应解析为段落 + 分割线
        assertTrue(blocks[0] is MarkdownParser.MdBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownParser.MdBlock.HorizontalRule)
    }

    @Test
    fun `chapter indexes use top level headings only`() {
        val blocks = MarkdownParser.parse("# 第一章\n正文内容\n## 小节\n# 第二章")

        assertEquals(listOf(0, 3), MarkdownParser.chapterBlockIndexes(blocks))
    }

    @Test
    fun `chapter indexes fall back when only sub headings exist`() {
        val blocks = MarkdownParser.parse("## 甲\n内容\n## 乙")

        assertEquals(listOf(0, 2), MarkdownParser.chapterBlockIndexes(blocks))
    }

    @Test
    fun `single document title plus sections uses section level`() {
        val blocks = MarkdownParser.parse("# 文档标题\n## 第一章\n内容\n## 第二章")

        // 只有 1 个一级标题时，取二级标题作为章节
        assertEquals(listOf(1, 3), MarkdownParser.chapterBlockIndexes(blocks))
    }

    @Test
    fun `no headings means no chapters`() {
        val blocks = MarkdownParser.parse("普通段落，没有标题")

        assertTrue(MarkdownParser.chapterBlockIndexes(blocks).isEmpty())
    }
}
