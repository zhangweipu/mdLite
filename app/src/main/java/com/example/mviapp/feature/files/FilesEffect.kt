package com.example.mviapp.feature.files

import com.example.mviapp.core.mvi.MviEffect

/**
 * 文件夹浏览页 Effect
 */
sealed interface FilesEffect : MviEffect {

    /** 导航到阅读页 */
    data class NavigateToReader(val uri: String, val title: String) : FilesEffect

    /** 导航到子文件夹 */
    data class NavigateToDirectory(val uri: String) : FilesEffect
}
