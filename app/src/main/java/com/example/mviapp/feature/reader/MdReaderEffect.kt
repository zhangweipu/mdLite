package com.example.mviapp.feature.reader

import com.example.mviapp.core.mvi.MviEffect

/**
 * Markdown 阅读页 Effect
 *
 * 当前阅读页没有一次性副作用，保留类型以便后续扩展
 * （如分享、复制、链接跳转等）。
 */
sealed interface MdReaderEffect : MviEffect
