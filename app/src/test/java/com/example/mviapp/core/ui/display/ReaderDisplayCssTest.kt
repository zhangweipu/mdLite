package com.example.mviapp.core.ui.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDisplayCssTest {

    @Test
    fun `forces pure black text on pure white background`() {
        val css = ReaderDisplayCss.highContrast()
        assertTrue(css.contains("color: #000000 !important"))
        assertTrue(css.contains("background-color: #FFFFFF !important"))
        // 彩色滤光片层分辨率低，文字必须回到黑白层
        assertTrue(css.contains("html body, html body *"))
    }

    @Test
    fun `thins strokes are reinforced without fake bold scaling`() {
        val css = ReaderDisplayCss.highContrast()
        assertTrue(css.contains("-webkit-text-stroke: 0.3px #000000 !important"))
        assertTrue(css.contains("font-weight: 500 !important"))
        assertTrue(css.contains("sans-serif !important"))
    }

    @Test
    fun `font size uses css px and follows scale`() {
        assertTrue(ReaderDisplayCss.highContrast(1f).contains("font-size: 20px !important"))
        assertTrue(ReaderDisplayCss.highContrast(1.18f).contains("font-size: 23.6px !important"))
        assertTrue(ReaderDisplayCss.highContrast(1.36f).contains("font-size: 27.2px !important"))
    }

    @Test
    fun `extreme scale is clamped`() {
        assertTrue(ReaderDisplayCss.highContrast(9f).contains("font-size: 32px !important"))
        assertTrue(ReaderDisplayCss.highContrast(0.1f).contains("font-size: 16px !important"))
    }

    @Test
    fun `color eink keeps color images while monochrome screen converts to gray`() {
        assertFalse(ReaderDisplayCss.highContrast().contains("grayscale"))
        assertTrue(ReaderDisplayCss.highContrast(monochrome = true).contains("grayscale(1)"))
    }

    @Test
    fun `links use underline instead of color and code uses black border`() {
        val css = ReaderDisplayCss.highContrast()
        assertTrue(css.contains("text-decoration: underline !important"))
        assertTrue(css.contains("html body pre"))
        assertTrue(css.contains("border: 2px solid #000000 !important"))
        assertTrue(css.contains("html body pre code"))
    }

    @Test
    fun `animations shadows and background images are removed`() {
        val css = ReaderDisplayCss.highContrast()
        assertTrue(css.contains("animation: none !important"))
        assertTrue(css.contains("transition: none !important"))
        assertTrue(css.contains("box-shadow: none !important"))
        assertTrue(css.contains("background-image: none !important"))
        assertTrue(css.contains("scroll-behavior: auto !important"))
    }

    @Test
    fun `injectInto appends style before head close and replaces wide viewport`() {
        val raw = """
            <html><head>
            <meta name="viewport" content="width=1200">
            <style>p{color:blue}</style>
            </head><body><p>hi</p></body></html>
        """.trimIndent()
        val result = ReaderDisplayCss.injectInto(raw, "body{color:#000}")

        assertFalse(result.contains("width=1200"))
        assertTrue(result.contains("width=device-width"))
        assertTrue(result.contains("body{color:#000}"))
        assertTrue(result.indexOf("<style id=\"mvi-eink-style\">") < result.indexOf("</head>"))
    }

    @Test
    fun `injectInto wraps headless fragment`() {
        val result = ReaderDisplayCss.injectInto("<p>片段</p>", "p{color:#000}")
        assertTrue(result.startsWith("<html><head>"))
        assertTrue(result.contains("<body><p>片段</p></body></html>"))
        assertTrue(result.contains("charset=\"UTF-8\""))
    }

    @Test
    fun `injectInto leaves html untouched when css is blank`() {
        val raw = "<html><head><meta name=\"viewport\" content=\"width=1200\"></head></html>"
        assertEquals(raw, ReaderDisplayCss.injectInto(raw, ""))
    }
}
