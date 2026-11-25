package com.xplayer.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xplayer.app.AppContainer
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.StreamUrlEntity
import com.xplayer.app.data.repository.PlaybackRepository
import com.xplayer.app.data.repository.StreamRepository
import com.xplayer.app.data.scanner.ScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val videoCount: StateFlow<Int> = container.videoRepository.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val history = container.playbackRepository.history

    val streams: StateFlow<List<StreamUrlEntity>> = container.streamRepository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanState: StateFlow<ScanState> = container.mediaScanner.state

    /** 继续观看：最近一条未看完且进度 > 1s 的记录 */
    val continueWatching: StateFlow<PlaybackHistoryEntity?> =
        history.map { list ->
            list.firstOrNull { entry ->
                !entry.completed &&
                    entry.durationMs > 0 &&
                    entry.lastPositionMs > 1_000 &&
                    entry.lastPositionMs < entry.durationMs * PlaybackRepository.COMPLETION_THRESHOLD
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recent: StateFlow<List<PlaybackHistoryEntity>> =
        history.map { it.take(15) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentThumbnails: StateFlow<Map<String, String?>> = combine(
        recent,
        continueWatching,
        container.videoRepository.videos,
    ) { recentList, continueItem, videoList ->
        val needed = HashSet<String>(recentList.size + 1)
        if (continueItem != null && continueItem.thumbnailPath == null) {
            needed += continueItem.mediaId
        }
        recentList.forEach { entry ->
            if (entry.thumbnailPath == null) needed += entry.mediaId
        }
        if (needed.isEmpty()) return@combine emptyMap()
        val result = HashMap<String, String?>(needed.size)
        for (video in videoList) {
            if (video.path in needed) {
                result[video.path] = video.thumbnailPath
                if (result.size == needed.size) break
            }
        }
        result
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun scan() {
        container.mediaScanner.triggerScan()
    }

    fun removeHistory(entry: PlaybackHistoryEntity) {
        viewModelScope.launch { container.playbackRepository.remove(entry.mediaId, entry.sourceType) }
    }

    fun removeStream(id: Long) {
        viewModelScope.launch { container.streamRepository.delete(id) }
    }

    fun rememberStream(
        url: String,
        title: String?,
        rawHeaders: String? = null,
        overwriteMeta: Boolean = false,
    ) {
        val headersJson = rawHeaders?.let { StreamRepository.encodeHeadersText(it) }
        viewModelScope.launch {
            container.streamRepository.remember(
                url = url,
                title = title,
                headersJson = headersJson,
                overwriteMeta = overwriteMeta,
            )
        }
    }
}
