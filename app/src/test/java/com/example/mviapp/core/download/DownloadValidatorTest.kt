package com.example.mviapp.core.download

import com.example.mviapp.core.common.FileSignatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadValidatorTest {

    private val zipHead = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0, 0, 0)
    private val htmlHead = "<!DOCTYPE html><html><body>login</body></html>".toByteArray()
    private val jsonHead = "{\"message\":\"forbidden\"}".toByteArray()

    @Test
    fun `zero byte response is rejected`() {
        val problem = DownloadValidator.validate("book.epub", 0, -1, "application/epub+zip", byteArrayOf())
        assertNotNull(problem)
        assertTrue(problem!!.contains("0 字节"))
    }

    @Test
    fun `truncated response is rejected`() {
        val problem = DownloadValidator.validate("book.epub", 100, 200, "application/epub+zip", zipHead)
        assertNotNull(problem)
        assertTrue(problem!!.contains("下载不完整"))
    }

    @Test
    fun `html error page is rejected for non epub binary`() {
        val problem = DownloadValidator.validate(
            "book.pdf", 200, 200, "text/html; charset=utf-8", htmlHead,
        )
        assertNotNull(problem)
        assertTrue(problem!!.contains("text/html"))
    }

    @Test
    fun `valid zip is accepted even if server mislabels content type`() {
        assertNull(
            DownloadValidator.validate("book.epub", 200, 200, "text/html; charset=utf-8", zipHead)
        )
    }

    @Test
    fun `html body is rejected even when content type looks binary`() {
        val problem = DownloadValidator.validate(
            "book.epub", 200, 200, "application/octet-stream", htmlHead,
        )
        assertNotNull(problem)
        assertTrue(problem!!.contains("HTML 网页"))
        assertTrue(problem.contains("不是 EPUB 压缩包"))
    }

    @Test
    fun `json error page is rejected for epub`() {
        val problem = DownloadValidator.validate("book.epub", 18, -1, null, jsonHead)
        assertNotNull(problem)
        assertTrue(problem!!.contains("JSON 文本"))
    }

    @Test
    fun `valid epub passes`() {
        assertNull(
            DownloadValidator.validate("book.epub", 4096, 4096, "application/epub+zip", zipHead)
        )
    }

    @Test
    fun `unknown content length still passes`() {
        assertNull(DownloadValidator.validate("book.epub", 4096, -1, null, zipHead))
        assertNull(DownloadValidator.validate("photo.png", 4096, -1, "image/png", byteArrayOf(1, 2)))
    }

    @Test
    fun `html document is allowed to have html mime type`() {
        assertNull(DownloadValidator.validate("page.html", 200, 200, "text/html", htmlHead))
    }

    @Test
    fun `signature detection`() {
        assertTrue(FileSignatures.isZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertTrue(FileSignatures.isZip(byteArrayOf(0x50, 0x4B, 0x05, 0x06)))
        assertTrue(FileSignatures.isZip(byteArrayOf(0x50, 0x4B, 0x07, 0x08)))
        assertFalse(FileSignatures.isZip(htmlHead))
        assertFalse(FileSignatures.isZip(byteArrayOf(0x50, 0x4B)))
    }

    @Test
    fun `describe and preview are readable`() {
        assertEquals("HTML 网页", FileSignatures.describe(htmlHead))
        assertEquals("JSON 文本", FileSignatures.describe(jsonHead))
        assertEquals("ZIP 压缩包", FileSignatures.describe(zipHead))
        assertEquals("空文件", FileSignatures.describe(byteArrayOf()))
        assertEquals("PK..", FileSignatures.preview(zipHead, 4))
    }
}
