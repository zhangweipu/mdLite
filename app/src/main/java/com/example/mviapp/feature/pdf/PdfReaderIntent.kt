package com.example.mviapp.feature.pdf

import com.example.mviapp.core.mvi.MviIntent

/** PDF 阅读页 Intent */
sealed interface PdfReaderIntent : MviIntent {

    /** 打开 PDF 并读取页数 */
    data object Load : PdfReaderIntent

    /** 加载失败后重试 */
    data object Retry : PdfReaderIntent

    /** 切换置顶状态 */
    data object TogglePin : PdfReaderIntent

    /** 保存阅读位置（页面离开时派发） */
    data class SavePosition(val page: Int) : PdfReaderIntent
}
