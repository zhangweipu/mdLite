package com.example.mviapp.feature.files

import com.example.mviapp.core.mvi.MviState
import com.example.mviapp.core.storage.FileEntry
import com.example.mviapp.core.storage.StorageDirectory

/**
 * 文件夹浏览页 UI 状态
 */
sealed interface FilesState : MviState {

    /** 加载中 */
    data object Loading : FilesState

    /** 加载成功 */
    data class Success(
        val directoryName: String,
        val entries: List<FileEntry> = emptyList(),
    ) : FilesState

    /** 加载失败 */
    data class Error(val message: String) : FilesState
}

/** 由 [StorageDirectory] 转为 UI 状态 */
fun StorageDirectory.toFilesState(): FilesState.Success =
    FilesState.Success(
        directoryName = name,
        entries = entries,
    )
