package com.example.mviapp.core.epub

/** 打开 EPUB 时应恢复到的位置：章节索引 + 章内垂直滚动像素 */
data class EpubResumePoint(val chapterIndex: Int, val scrollY: Int)

/**
 * 把持久化的阅读进度转换成安全的恢复位置。
 *
 * 书籍可能被替换成章节数不同的同名文件，或记录来自旧版本，
 * 因此章节索引越界 / 书籍为空时一律从第 1 章顶部开始，避免加载不存在的章节、
 * 或把上一本书的章内偏移套到新书上。
 */
fun resolveEpubResumePoint(
    chapterCount: Int,
    savedChapterIndex: Int?,
    savedScrollY: Int?,
): EpubResumePoint {
    if (chapterCount <= 0 ||
        savedChapterIndex == null ||
        savedChapterIndex !in 0 until chapterCount
    ) {
        return EpubResumePoint(chapterIndex = 0, scrollY = 0)
    }
    return EpubResumePoint(
        chapterIndex = savedChapterIndex,
        scrollY = (savedScrollY ?: 0).coerceAtLeast(0),
    )
}
