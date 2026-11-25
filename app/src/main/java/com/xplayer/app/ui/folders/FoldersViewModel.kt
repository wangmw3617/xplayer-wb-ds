package com.xplayer.app.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xplayer.app.AppContainer
import com.xplayer.app.R
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.scanner.ScanState
import com.xplayer.app.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
    val formattedSize: String,
    val latestThumbnail: String?,
    val videos: List<VideoEntity>,
)

class FoldersViewModel(private val container: AppContainer) : ViewModel() {

    val scanState: StateFlow<ScanState> = container.mediaScanner.state

    val folders: StateFlow<List<FolderItem>> = container.videoRepository.videos
        .map { videos ->
            videos.groupBy { it.folderPath }
                .map { (path, list) ->
                    val sorted = list.sortedByDescending { it.dateModified }
                    val totalSize = list.sumOf { it.sizeBytes }
                    FolderItem(
                        path = path,
                        name = File(path).name.ifBlank { path },
                        videoCount = list.size,
                        totalSize = totalSize,
                        formattedSize = Formatters.size(totalSize),
                        latestThumbnail = sorted.firstOrNull { it.thumbnailPath != null }?.thumbnailPath,
                        videos = sorted,
                    )
                }
                .sortedByDescending { it.videoCount }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyByPath: StateFlow<Map<String, PlaybackHistoryEntity>> = container.playbackRepository.history
        .map { list -> list.associateBy { it.mediaId } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val hiddenFolders: StateFlow<List<String>> = container.folderRepository.blacklisted
        .map { list -> list.map { it.folderPath } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    fun scan() {
        container.mediaScanner.triggerScan()
    }

    fun hide(path: String) {
        viewModelScope.launch {
            container.folderRepository.hide(path)
            container.videoRepository.deleteInFolder(path)
        }
    }

    fun unhide(path: String) {
        viewModelScope.launch {
            container.folderRepository.unhide(path)
            container.mediaScanner.triggerScan()
        }
    }

    fun rename(video: VideoEntity, newName: String) {
        viewModelScope.launch {
            val result = container.videoRepository.rename(video, newName)
            val msg = when {
                result.isSuccess -> R.string.videos_renamed
                result.exceptionOrNull() is IllegalArgumentException -> R.string.videos_rename_invalid
                result.exceptionOrNull()?.message == "exists" -> R.string.videos_rename_exists
                else -> R.string.videos_operation_failed
            }
            _messages.emit(msg)
        }
    }

    fun delete(video: VideoEntity) {
        viewModelScope.launch {
            val result = container.videoRepository.delete(video)
            _messages.emit(if (result.isSuccess) R.string.videos_deleted else R.string.videos_operation_failed)
        }
    }
}
