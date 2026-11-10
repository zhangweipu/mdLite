package com.example.mviapp.core.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubReadingProgressTest {

    @Test
    fun `no saved record starts from first chapter top`() {
        val point = resolveEpubResumePoint(
            chapterCount = 18,
            savedChapterIndex = null,
            savedScrollY = null,
        )

        assertEquals(EpubResumePoint(0, 0), point)
    }

    @Test
    fun `restores both chapter and in-chapter scroll`() {
        val point = resolveEpubResumePoint(
            chapterCount = 18,
            savedChapterIndex = 8,
            savedScrollY = 2430,
        )

        assertEquals(EpubResumePoint(8, 2430), point)
    }

    @Test
    fun `last chapter is a valid resume point`() {
        val point = resolveEpubResumePoint(
            chapterCount = 5,
            savedChapterIndex = 4,
            savedScrollY = 120,
        )

        assertEquals(EpubResumePoint(4, 120), point)
    }

    @Test
    fun `out of range chapter falls back to first chapter top`() {
        val tooLarge = resolveEpubResumePoint(5, 12, 900)
        val negative = resolveEpubResumePoint(5, -1, 900)
        val emptyBook = resolveEpubResumePoint(0, 0, 900)

        assertEquals(EpubResumePoint(0, 0), tooLarge)
        assertEquals(EpubResumePoint(0, 0), negative)
        assertEquals(EpubResumePoint(0, 0), emptyBook)
    }

    @Test
    fun `negative or missing scroll offset is clamped to top`() {
        assertEquals(
            EpubResumePoint(3, 0),
            resolveEpubResumePoint(5, 3, -120),
        )
        assertEquals(
            EpubResumePoint(3, 0),
            resolveEpubResumePoint(5, 3, null),
        )
    }
}
