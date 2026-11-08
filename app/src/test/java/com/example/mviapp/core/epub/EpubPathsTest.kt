package com.example.mviapp.core.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPathsTest {

    @Test
    fun `resolves href relative to opf directory`() {
        assertEquals("OEBPS/Text/ch1.xhtml", EpubPaths.resolve("OEBPS", "Text/ch1.xhtml"))
        assertEquals("OEBPS/Text/ch1.xhtml", EpubPaths.resolve("OEBPS/Text", "ch1.xhtml"))
        assertEquals("OEBPS/Images/cover.jpg", EpubPaths.resolve("OEBPS/Text", "../Images/cover.jpg"))
        assertEquals("Text/ch1.xhtml", EpubPaths.resolve("", "./Text/ch1.xhtml"))
    }

    @Test
    fun `strips anchors and decodes percent encoded hrefs`() {
        assertEquals(
            "OEBPS/Text/第一章.xhtml",
            EpubPaths.resolve("OEBPS", "Text/%E7%AC%AC%E4%B8%80%E7%AB%A0.xhtml#section2")
        )
        assertEquals(
            "OEBPS/Text/ch 1.xhtml",
            EpubPaths.resolve("OEBPS", "Text/ch%201.xhtml?x=1")
        )
    }

    @Test
    fun `normalizes zip entries and blocks traversal`() {
        assertEquals("OEBPS/ch1.xhtml", EpubPaths.entry("/OEBPS/./ch1.xhtml"))
        assertEquals("ch1.xhtml", EpubPaths.entry("OEBPS/../ch1.xhtml"))
        assertEquals("ch1.xhtml", EpubPaths.entry("../../ch1.xhtml"))
        // 路径永远不会带出缓存目录
        assertFalse(EpubPaths.entry("a/../../b/../../../secret").startsWith(".."))
    }

    @Test
    fun `matches paths ignoring case and encoding`() {
        assertTrue(EpubPaths.matches("OEBPS/Text/ch1.xhtml", "oebps/text/CH1.xhtml"))
        assertTrue(EpubPaths.matches("Text/%E7%AC%AC1.xhtml", "Text/第1.xhtml"))
        assertFalse(EpubPaths.matches("Text/ch1.xhtml", "Text/ch2.xhtml"))
    }

    @Test
    fun `extracts chapter parent directory`() {
        assertEquals("OEBPS/Text", EpubPaths.parentDir("OEBPS/Text/ch1.xhtml"))
        assertEquals("", EpubPaths.parentDir("ch1.xhtml"))
    }
}
