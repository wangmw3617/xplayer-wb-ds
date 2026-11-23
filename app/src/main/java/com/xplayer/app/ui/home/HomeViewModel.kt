package com.xplayer.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xplayer.app.AppContainer
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.StreamUrlEntity
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.scanner.ScanState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val videos: StateFlow<List<VideoEntity>> = container.videoRepository.videos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<PlaybackHistoryEntity>> = container.playbackRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val streams: StateFlow<List<StreamUrlEntity>> = container.streamRepository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val scanState: StateFlow<ScanState> = container.mediaScanner.state

    /** 继续观看：最近一条未看完且进度 > 1% 的记录 */
    val continueWatching: StateFlow<PlaybackHistoryEntity?> =
        history.map { list ->
            list.firstOrNull { entry ->
                !entry.completed &&
                    entry.durationMs > 0 &&
                    entry.lastPositionMs > 1_000 &&
                    entry.lastPositionMs < entry.durationMs * 0.95f
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recent: StateFlow<List<PlaybackHistoryEntity>> =
        history.map { it.take(15) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun thumbnailFor(entry: PlaybackHistoryEntity): String? {
        val direct = entry.thumbnailPath
        if (direct != null) return direct
        val match = videos.value.firstOrNull { it.path == entry.mediaId }
        return match?.thumbnailPath
    }

    fun scan() {
        viewModelScope.launch { container.mediaScanner.scan() }
    }

    fun removeHistory(mediaId: String) {
        viewModelScope.launch { container.playbackRepository.remove(mediaId) }
    }

    fun removeStream(id: Long) {
        viewModelScope.launch { container.streamRepository.delete(id) }
    }

    fun rememberStream(url: String, title: String?) {
        viewModelScope.launch { container.streamRepository.remember(url, title, null) }
    }
}
