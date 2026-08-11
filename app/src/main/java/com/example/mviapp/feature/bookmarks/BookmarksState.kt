package com.example.mviapp.feature.bookmarks

import com.example.mviapp.core.bookmark.Bookmark
import com.example.mviapp.core.mvi.MviState

/** 收藏夹 UI 状态 */
data class BookmarksState(
    val items: List<Bookmark> = emptyList(),
    val isLoading: Boolean = false,
) : MviState {
    val isEmpty: Boolean get() = items.isEmpty()
}
