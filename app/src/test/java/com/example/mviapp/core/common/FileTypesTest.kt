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
}
