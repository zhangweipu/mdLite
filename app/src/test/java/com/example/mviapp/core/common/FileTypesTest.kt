package com.example.mviapp.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypesTest {

    @Test
    fun `detects supported document types`() {
        assertTrue("readme.md".isSupportedDocFile())
        assertTrue("page.html".isSupportedDocFile())
        assertTrue("notes.txt".isSupportedDocFile())
        assertTrue("manual.pdf".isSupportedDocFile())
        assertFalse("photo.png".isSupportedDocFile())
        assertFalse("archive.zip".isSupportedDocFile())
    }

    @Test
    fun `detects pdf and txt case insensitive`() {
        assertTrue("MANUAL.PDF".isPdfFile())
        assertTrue("a.Pdf".isPdfFile())
        assertTrue("NOTE.TXT".isTxtFile())
        assertFalse("manual.pdfx".isPdfFile())
    }

    @Test
    fun `detects epub from content uri and percent encoded paths`() {
        // SAF 返回的 document URI：扩展名藏在编码后的末段
        assertTrue(
            ("content://com.android.externalstorage.documents/document/" +
                "primary%3ABooks%2Fnovel.epub").isEpubFile()
        )
        assertTrue("/sdcard/Download/%E4%B8%AD%E6%96%87%E4%B9%A6.EPUB".isEpubFile())
        assertTrue("novel.epub?x=1".isEpubFile())
        assertTrue("小说.epub".isSupportedDocFile())
        assertFalse("novel.epub.bak".isEpubFile())
        // 纯数字 ID 的 content URI 无法从路径判断，需由调用方用文件名/MIME 兜底
        assertFalse("content://media/external/file/12345".isEpubFile())
    }
}
