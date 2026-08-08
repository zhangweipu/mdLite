package com.example.mviapp.feature.files

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.mvi.MviViewModel
import com.example.mviapp.core.navigation.Screen
import com.example.mviapp.core.storage.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 文件夹浏览页 ViewModel
 *
 * 从导航参数中取文件夹 URI，调用 [StorageRepository] 列出内容。
 */
@HiltViewModel
class FilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StorageRepository,
) : MviViewModel<FilesIntent, FilesState, FilesEffect>(
    initialState = FilesState.Loading
) {

    private val directoryUri: Uri = Uri.parse(
        savedStateHandle.get<String>(Screen.Files.ARG_TREE_URI).orEmpty(),
    )

    init {
        dispatch(FilesIntent.Load)
    }

    override suspend fun handleIntent(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.Load -> loadDirectory()
            is FilesIntent.OpenDirectory -> openDirectory(intent.uri)
            is FilesIntent.OpenFile -> openFile(intent.uri, intent.title)
            is FilesIntent.Retry -> loadDirectory()
        }
    }

    private fun loadDirectory() {
        updateState { FilesState.Loading }
        viewModelScope.launch {
            runCatching { repository.listDirectory(directoryUri) }
                .onSuccess { directory ->
                    updateState { directory.toFilesState() }
                }
                .onFailure { error ->
                    updateState { FilesState.Error(message = error.message ?: "无法读取文件夹") }
                }
        }
    }

    private fun openDirectory(uri: String) {
        emitEffect(FilesEffect.NavigateToDirectory(uri))
    }

    private fun openFile(uri: String, title: String) {
        emitEffect(FilesEffect.NavigateToReader(uri, title))
    }
}
