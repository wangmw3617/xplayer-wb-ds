package com.xplayer.app.ui.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xplayer.app.AppContainer
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.VideoEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VideoSort { DATE, NAME, SIZE, DURATION }

data class VideoListItem(
    val video: VideoEntity,
    val positionMs: Long,
    val progress: Float,
) {
    val durationLabel: String get() = ""
}

class VideosViewModel(private val container: AppContainer) : ViewModel() {

    private val videos = container.videoRepository.videos
    private val history = container.playbackRepository.history

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(VideoSort.DATE)
    val grid = MutableStateFlow(true)

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val items: StateFlow<List<VideoListItem>> = combine(
        videos,
        history,
        query,
        sort,
    ) { videoList, historyList, rawQuery, sortMode ->
        val progressMap = historyList.associateBy { it.mediaId }
        val keyword = rawQuery.trim()
        videoList.asSequence()
            .filter { keyword.isEmpty() || it.title.contains(keyword, ignoreCase = true) }
            .map { video ->
                val entry: PlaybackHistoryEntity? = progressMap[video.path]
                VideoListItem(
                    video = video,
                    positionMs = entry?.lastPositionMs ?: 0L,
                    progress = entry?.progress ?: 0f,
                )
            }
            .sortedWith(
                when (sortMode) {
                    VideoSort.DATE -> compareByDescending { it.video.dateModified }
                    VideoSort.NAME -> compareBy { it.video.title.lowercase() }
                    VideoSort.SIZE -> compareByDescending { it.video.sizeBytes }
                    VideoSort.DURATION -> compareByDescending { it.video.durationMs }
                },
            )
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: VideoSort) {
        sort.value = value
    }

    fun toggleView() {
        grid.value = !grid.value
    }

    fun rename(video: VideoEntity, newName: String) {
        viewModelScope.launch {
            val result = container.videoRepository.rename(video, newName)
            _messages.emit(if (result.isSuccess) com.xplayer.app.R.string.videos_renamed else com.xplayer.app.R.string.videos_operation_failed)
        }
    }

    fun delete(video: VideoEntity) {
        viewModelScope.launch {
            val result = container.videoRepository.delete(video)
            _messages.emit(if (result.isSuccess) com.xplayer.app.R.string.videos_deleted else com.xplayer.app.R.string.videos_operation_failed)
        }
    }

    fun scan() {
        viewModelScope.launch { container.mediaScanner.scan() }
    }
}
