package com.example.mviapp.core.md

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToMarkdownTest {

    @Test
    fun `converts headings paragraphs and inline styles`() {
        val md = HtmlToMarkdown.convert(
            "<h1>标题</h1>" +
                "<p>这是<strong>粗体</strong>和<em>斜体</em>，" +
                "<a href=\"https://example.com\">链接</a></p>",
        )

        assertEquals(
            "# 标题\n这是**粗体**和*斜体*，[链接](https://example.com)",
            md,
        )
    }

    @Test
    fun `converts table and code block`() {
        val md = HtmlToMarkdown.convert(
            "<table>" +
                "<tr><th>名称</th><th>说明</th></tr>" +
                "<tr><td>表格</td><td>测试</td></tr>" +
                "</table>" +
                "<pre><code>val x = 1</code></pre>",
        )

        val blocks = MarkdownParser.parse(md)
        val table = blocks[0] as MarkdownParser.MdBlock.Table
        assertEquals(
            listOf("名称", "说明"),
            table.header.map { (it.single() as MarkdownParser.MdInline.Plain).text },
        )
        assertEquals(
            listOf("表格", "测试"),
            table.rows[0].map { (it.single() as MarkdownParser.MdInline.Plain).text },
        )
        val code = blocks[1] as MarkdownParser.MdBlock.CodeBlock
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun `strips script and style and decodes entities`() {
        val md = HtmlToMarkdown.convert(
            "<style>body{color:red}</style>" +
                "<script>alert(1)</script>" +
                "<p>A &amp; B &lt; C &gt; D</p>",
        )

        assertFalse(md.contains("alert"))
        assertFalse(md.contains("color:red"))
        assertTrue(md.contains("A & B < C > D"))
    }

    @Test
    fun `html renders through markdown parser`() {
        val blocks = MarkdownParser.parse(
            HtmlToMarkdown.convert("<h1>标题</h1><p>正文内容</p>"),
        )

        assertTrue(blocks[0] is MarkdownParser.MdBlock.Heading)
        assertTrue(blocks[1] is MarkdownParser.MdBlock.Paragraph)
    }
}
