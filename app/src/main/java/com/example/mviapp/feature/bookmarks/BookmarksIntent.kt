package com.example.mviapp.feature.bookmarks

import com.example.mviapp.core.mvi.MviIntent

/** 收藏夹 Intent */
sealed interface BookmarksIntent : MviIntent {

    /** 加载收藏列表 */
    data object Load : BookmarksIntent

    /** 删除一条收藏 */
    data class Remove(val url: String) : BookmarksIntent

    /** 清空全部 */
    data object ClearAll : BookmarksIntent
}
