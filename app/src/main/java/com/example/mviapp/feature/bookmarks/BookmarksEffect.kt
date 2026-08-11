package com.example.mviapp.feature.bookmarks

import com.example.mviapp.core.mvi.MviEffect

/** 收藏夹一次性事件 */
sealed interface BookmarksEffect : MviEffect {
    data class ShowToast(val message: String) : BookmarksEffect

    /** 打开指定网址（通知外部跳转到浏览器） */
    data class OpenUrl(val url: String) : BookmarksEffect
}
