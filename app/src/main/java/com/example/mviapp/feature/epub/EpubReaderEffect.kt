package com.example.mviapp.feature.epub

import com.example.mviapp.core.mvi.MviEffect

/** EPUB 阅读器一次性事件 */
sealed interface EpubReaderEffect : MviEffect {
    data class ShowToast(val message: String) : EpubReaderEffect
}
