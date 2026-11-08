package com.example.mviapp.core.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubHtmlFormatterTest {

    private val chapter = "OEBPS/Text/ch1.xhtml"

    @Test
    fun `removes book viewport and injects mobile viewport`() {
        val raw = """
            <html><head>
            <meta name='viewport' content='width=320'>
            <meta charset="gbk">
            </head><body><p>正文</p></body></html>
        """.trimIndent()

        val result = EpubHtmlFormatter.format(raw, chapter)

        assertFalse(result.fixedLayout)
        assertTrue(result.html.contains("width=device-width"))
        assertFalse(result.html.contains("width=320"))
        assertEquals(1, Regex("name=\"viewport\"").findAll(result.html).count())
        assertTrue(result.html.contains("charset=\"UTF-8\""))
    }

    @Test
    fun `injects css blocking horizontal overflow and fixing images`() {
        val html = EpubHtmlFormatter.format(
            "<html><head></head><body><img src=\"a.jpg\" width=\"900\"></body></html>",
            chapter,
        ).html

        assertTrue(html.contains("overflow-x: hidden"))
        assertTrue(html.contains("overflow-x: clip"))
        assertTrue(html.contains("box-sizing: border-box"))
        assertTrue(html.contains("max-width: 100% !important"))
        assertTrue(html.contains("height: auto !important"))
        assertTrue(html.contains("float: none !important"))
        assertTrue(html.contains("table-layout: fixed"))
        assertTrue(html.contains("white-space: pre"))
        assertTrue(html.contains("overflow-x: auto"))
        assertTrue(html.contains("overflow-wrap: anywhere"))
        assertTrue(html.contains(EpubHtmlFormatter.THEME_PLACEHOLDER))
    }

    @Test
    fun `rewrites root absolute image src to chapter relative url`() {
        val raw = """<html><head></head><body><img src="/OEBPS/Images/cover.jpg" width="800"></body></html>"""

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("src=\"../Images/cover.jpg\""))
        assertFalse(html.contains("src=\"/OEBPS"))
    }

    @Test
    fun `keeps external data and relative urls untouched`() {
        val raw = """
            <body>
            <img src="https://example.com/y.png">
            <img src="data:image/png;base64,AAA">
            <img src="../Images/a.jpg">
            <a href="#note1">注</a>
            </body>
        """.trimIndent()

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("src=\"https://example.com/y.png\""))
        assertTrue(html.contains("src=\"data:image/png;base64,AAA\""))
        assertTrue(html.contains("src=\"../Images/a.jpg\""))
        assertTrue(html.contains("href=\"#note1\""))
    }

    @Test
    fun `falls back to real asset path by file name`() {
        val raw = """<html><head></head><body><img src="../Images/cover.jpg"></body></html>"""
        val assets = setOf("Images/Cover.JPG", "OEBPS/Text/ch1.xhtml")

        val html = EpubHtmlFormatter.format(raw, chapter, assets).html

        assertTrue(html.contains("src=\"../../Images/Cover.JPG\""))
    }

    @Test
    fun `falls back to real stylesheet path for link href`() {
        val raw = """<html><head><link rel="stylesheet" href="../Styles/main.css"></head><body>x</body></html>"""
        // ZIP 里真实路径在根目录的 Styles/ 下，HTML 写的是 OEBPS/Styles/
        val assets = setOf("Styles/MAIN.CSS", "OEBPS/Text/ch1.xhtml")

        val html = EpubHtmlFormatter.format(raw, chapter, assets).html

        assertTrue(html.contains("href=\"../../Styles/MAIN.CSS\""))
    }

    @Test
    fun `keeps url unchanged when asset already exists`() {
        val raw = """<html><head></head><body><img src="../Images/cover.jpg"></body></html>"""
        val assets = setOf("OEBPS/Images/cover.jpg")

        val html = EpubHtmlFormatter.format(raw, chapter, assets).html

        assertTrue(html.contains("src=\"../Images/cover.jpg\""))
    }

    @Test
    fun `does not apply asset fallback to chapter links`() {
        val raw = """<html><head></head><body><a href="ch2.xhtml">下一章</a></body></html>"""
        val assets = setOf("OEBPS/Text/CH2.XHTML")

        val html = EpubHtmlFormatter.format(raw, chapter, assets).html

        assertTrue(html.contains("href=\"ch2.xhtml\""))
    }

    @Test
    fun `detects fixed layout by viewport width and keeps design width`() {
        val raw = """
            <html><head><meta name="viewport" content="width=1200, height=1600"/></head>
            <body><div style="position:absolute"><img src="page1.jpg"></div></body></html>
        """.trimIndent()

        val result = EpubHtmlFormatter.format(raw, "page1.xhtml")

        assertTrue(result.fixedLayout)
        assertTrue(result.html.contains("width=1200"))
        assertFalse(result.html.contains("body *"))
        assertEquals(1, Regex("name=\"viewport\"").findAll(result.html).count())
    }

    @Test
    fun `detects pre-paginated layout without numeric viewport`() {
        val raw = """
            <html><head><meta property="rendition:layout" content="pre-paginated"></head>
            <body>x</body></html>
        """.trimIndent()

        val result = EpubHtmlFormatter.format(raw, "page1.xhtml")

        assertTrue(result.fixedLayout)
        assertTrue(result.html.contains("width=1000"))
    }

    @Test
    fun `wraps html fragment without head`() {
        val html = EpubHtmlFormatter.format(
            """<p>只有正文</p><img src="a.png">""",
            "ch1.xhtml",
        ).html

        assertTrue(html.startsWith("<html><head>"))
        assertTrue(html.contains("<body>"))
        assertTrue(html.contains("只有正文"))
        assertTrue(html.contains("src=\"a.png\""))
    }

    @Test
    fun `normalizes backslashes and svg xlink href`() {
        val raw = """<html><head></head><body><svg><image xlink:href="Images\a.png"/></svg></body></html>"""

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("xlink:href=\"Images/a.png\""))
    }

    @Test
    fun `normalizes gbk xml declaration`() {
        val raw = """<?xml version="1.0" encoding="GBK"?><html><head></head><body>x</body></html>"""

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("encoding=\"UTF-8\""))
        assertFalse(html.contains("GBK"))
    }

    @Test
    fun `formats code blocks with monospace card without breaking identifiers`() {
        val raw = """
            <html><head></head><body>
            <pre><code>  fun main() {
                println("hello")
            }</code></pre>
            <p>使用 <code>val count = 1</code> 定义变量</p>
            </body></html>
        """.trimIndent()

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("monospace"))
        assertTrue(html.contains("white-space: pre"))
        assertTrue(html.contains("overflow-x: auto"))
        assertTrue(html.contains("tab-size: 4"))
        assertTrue(html.contains("word-break: normal"))
        assertTrue(html.contains("overflow-wrap: break-word"))
        assertTrue(html.contains("pre code"))
        assertTrue(html.contains("background-color: #F5F6F8"))
        assertFalse(html.contains("word-break: break-all"))
    }

    @Test
    fun `does not rewrite literal urls inside code samples`() {
        val raw = """
            <html><head></head><body>
            <pre>&lt;img src="/images/a.png"&gt;</pre>
            <img src="/OEBPS/Images/real.jpg">
            </body></html>
        """.trimIndent()

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("&lt;img src=\"/images/a.png\"&gt;"))
        assertTrue(html.contains("src=\"../Images/real.jpg\""))
        assertFalse(html.contains("MVI_CODE_"))
    }

    @Test
    fun `fixed layout keeps monospace but does not force code wrapping`() {
        val raw = """<html><head><meta name="viewport" content="width=1200"/></head><body><pre>code</pre></body></html>"""

        val html = EpubHtmlFormatter.format(raw, "page1.xhtml").html

        assertTrue(html.contains("monospace"))
        assertFalse(html.contains("white-space: pre-wrap"))
    }

    @Test
    fun `converts markdown fenced paragraphs into a real code block`() {
        // 取自设备上实际打开的 tripLite-项目总览.epub
        val raw = """
            <html><head></head><body>
            <p>例：</p>
            <p>```kotlin</p>
            <p>// HomeState.kt</p>
            <p>val visiblePlans: List&lt;TripPlan&gt;</p>
            <p>get() = when (filter) { ALL -&gt; plans; }</p>
            <p>```</p>
            <p>说明文字</p>
            </body></html>
        """.trimIndent()

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("<pre class=\"mvi-code\" data-lang=\"kotlin\"><code>// HomeState.kt"))
        assertTrue(html.contains("val visiblePlans: List&lt;TripPlan&gt;"))
        assertTrue(html.contains("get() = when (filter) { ALL -&gt; plans; }"))
        assertTrue(html.contains("说明文字"))
        assertTrue(html.contains("例："))
        assertFalse(html.contains("```"))
    }

    @Test
    fun `fenced code keeps indentation blank lines and entities`() {
        val raw = """
            <html><head></head><body><p>```</p>
            <p>  fun main() {</p>
            <p>&nbsp;&nbsp;&nbsp;&nbsp;println("hi")</p>
            <p></p>
            <p>  }</p>
            <p>```</p></body></html>
        """.trimIndent()

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("  fun main() {"))
        assertTrue(html.contains("    println(\"hi\")"))
        assertTrue(html.contains("println(\"hi\")\n\n  }"))
        assertFalse(html.contains("&nbsp;"))
    }

    @Test
    fun `marks box drawing diagrams as wide blocks`() {
        val raw = """
            <html><head></head><body><p>```</p>
            <p>app</p>
            <p>┌────┬────┐</p>
            <p>home  ai</p>
            <p>```</p></body></html>
        """.trimIndent()

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("mvi-code-wide"))
        assertTrue(html.contains("┌────┬────┐"))
        assertTrue(html.contains("左右滑动"))
    }

    @Test
    fun `supports tilde fences and keeps language label`() {
        val raw = """<html><head></head><body><p>~~~python</p><p>print("hi")</p><p>~~~</p></body></html>"""

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("data-lang=\"python\""))
        assertTrue(html.contains("print(\"hi\")"))
        assertFalse(html.contains("~~~"))
    }

    @Test
    fun `removes unpaired fence markers`() {
        val raw = """<html><head></head><body><p>正文</p><p>```</p><p>尾段</p></body></html>"""

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertFalse(html.contains("```"))
        assertTrue(html.contains("尾段"))
    }

    @Test
    fun `leaves documents without fences untouched`() {
        val raw = """<html><head></head><body><p>普通段落</p><code>inline</code></body></html>"""

        val html = EpubHtmlFormatter.format(raw, chapter).html

        assertTrue(html.contains("<p>普通段落</p>"))
        assertTrue(html.contains("<code>inline</code>"))
    }

    @Test
    fun `handles single quoted and self closing viewport meta`() {
        val raw = """<html><head><META NAME="viewport" CONTENT='width=980'/></head><body>x</body></html>"""

        val result = EpubHtmlFormatter.format(raw, chapter)

        assertTrue(result.fixedLayout)
        assertTrue(result.html.contains("width=980"))
        assertEquals(1, Regex("name=\"viewport\"", RegexOption.IGNORE_CASE).findAll(result.html).count())
    }

    @Test
    fun `high contrast css is appended after book styles via theme placeholder`() {
        val raw = """<html><head><style>p{color:blue !important}</style></head><body><p>x</p></body></html>"""

        val formatted = EpubHtmlFormatter.format(raw, chapter).html

        // 主题/墨水屏样式由渲染层替换占位符注入，章节缓存不因切换开关失效
        assertTrue(formatted.contains(EpubHtmlFormatter.THEME_PLACEHOLDER))
        val themed = formatted.replace(
            EpubHtmlFormatter.THEME_PLACEHOLDER,
            "html,body{color:#202124 !important;}" +
                com.example.mviapp.core.ui.display.ReaderDisplayCss.highContrast(),
        )
        // 后注入的纯黑规则必须排在书籍自带彩色规则之后
        assertTrue(themed.indexOf("p{color:blue") < themed.indexOf("电子墨水屏高对比模式"))
        assertTrue(themed.contains("font-size: 20px"))
        assertTrue(themed.contains("color: #000000 !important"))
    }
}
