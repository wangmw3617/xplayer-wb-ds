package com.xplayer.app.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xplayer.app.AppContainer
import com.xplayer.app.data.local.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class FolderItem(
    val path: String,
    val name: String,
    val videoCount: Int,
    val totalSize: Long,
    val videos: List<VideoEntity>,
) {
    val latestThumbnail: String? get() = videos.firstOrNull { it.thumbnailPath != null }?.thumbnailPath
}

class FoldersViewModel(private val container: AppContainer) : ViewModel() {

    val folders: StateFlow<List<FolderItem>> = container.videoRepository.videos
        .map { videos ->
            videos.groupBy { it.folderPath }
                .map { (path, list) ->
                    val sorted = list.sortedByDescending { it.dateModified }
                    FolderItem(
                        path = path,
                        name = File(path).name.ifBlank { path },
                        videoCount = list.size,
                        totalSize = list.sumOf { it.sizeBytes },
                        videos = sorted,
                    )
                }
                .sortedByDescending { it.videoCount }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hiddenFolders: StateFlow<List<String>> = container.folderRepository.blacklisted
        .map { list -> list.map { it.folderPath } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    fun hide(path: String) {
        viewModelScope.launch {
            container.folderRepository.hide(path)
            container.videoRepository.deleteInFolder(path)
            _messages.emit(com.xplayer.app.R.string.folders_hide)
        }
    }

    fun unhide(path: String) {
        viewModelScope.launch {
            container.folderRepository.unhide(path)
            container.mediaScanner.scan()
        }
    }
}
