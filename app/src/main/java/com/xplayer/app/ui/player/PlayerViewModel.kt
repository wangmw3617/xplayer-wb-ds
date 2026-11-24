package com.xplayer.app.ui.player

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xplayer.app.AppContainer
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.datastore.AspectMode
import com.xplayer.app.data.datastore.SubtitlePrefs
import com.xplayer.app.data.local.SourceType
import com.xplayer.app.data.media.MediaInfoProbe
import com.xplayer.app.data.media.techSummary
import com.xplayer.app.data.scanner.SubtitleFinder
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaSource
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.player.PlayerController
import com.xplayer.app.player.engine.EngineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val container: AppContainer,
    private val sourceType: String,
    private val key: String,
    private val titleArg: String,
    private val startArg: Long,
) : ViewModel() {

    val controller = PlayerController(
        context = container.application,
        settingsRepository = container.settingsRepository,
        playbackRepository = container.playbackRepository,
        shaderRepository = container.shaderRepository,
    )

    val engine = controller.engine
    val state = controller.state
    val notice = controller.notice

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    var locked by mutableStateOf(false)
        private set

    var subtitleStyle by mutableStateOf(SubtitlePrefs())
        private set

    var aspectMode by mutableStateOf(AspectMode.FIT)
        private set

    /** 断点续播后提示用 */
    val resumePosition = MutableStateFlow(0L)
    var resumeNotified = false
        private set

    val isLocal: Boolean = sourceType == "local"

    /** 外部应用「打开方式」传入的 content:// 源 */
    private val isExternalUri: Boolean = sourceType == "uri"

    /** 供「媒体信息」页复用当前源 */
    val sourceKind: String get() = sourceType
    val sourceKey: String get() = key
    val sourceTitle: String get() = titleArg

    /** 播放页顶部的一行技术摘要（容器/编码/分辨率/帧率/HDR/音轨/码率） */
    val mediaSummary = MutableStateFlow<String?>(null)

    init {
        load()
    }

    /** 只影响顶部那行字，探测失败就整行不显示，不打断播放 */
    private fun loadSummary(source: MediaSource) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = runCatching {
                MediaInfoProbe.probe(container.application, source)
            }.getOrNull() ?: return@launch
            mediaSummary.value = info.techSummary().takeIf { it.isNotBlank() }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val snapshot = container.settingsRepository.settings.first()
            subtitleStyle = snapshot.subtitle

            if (isLocal) {
                val video = container.videoRepository.find(key)
                val siblings = if (video != null) {
                    container.videoRepository.videosInFolder(video.folderPath)
                        .sortedBy { it.title.lowercase() }
                } else {
                    emptyList()
                }
                val playlist: List<MediaSource> = siblings.map { item ->
                    LocalFileSource(
                        path = item.path,
                        displayName = item.title,
                        durationMs = item.durationMs,
                        width = item.width,
                        height = item.height,
                        subtitles = SubtitleFinder.find(item.path),
                        folderPath = item.folderPath,
                    )
                }
                val index = playlist.indexOfFirst { it.key == key }.coerceAtLeast(0)
                val current = playlist.getOrNull(index) ?: LocalFileSource(
                    path = key,
                    displayName = titleArg,
                    subtitles = SubtitleFinder.find(key),
                    folderPath = key.substringBeforeLast('/', ""),
                )
                val history = container.playbackRepository.find(key)
                val start = when {
                    history == null -> startArg
                    history.completed -> 0L
                    else -> history.lastPositionMs
                }
                resumePosition.value = start
                loadSummary(current)
                controller.open(
                    source = current,
                    startPositionMs = start,
                    hasEmbeddedAss = video?.hasAssSubtitle ?: false,
                    playlist = playlist.ifEmpty { listOf(current) },
                    playlistIndex = index,
                    preferredEngine = history?.preferredEngine?.let { raw ->
                        runCatching { EngineType.valueOf(raw) }.getOrNull()
                    },
                )
            } else if (isExternalUri) {
                resumePosition.value = startArg
                val source = ContentUriSource(
                    uri = key,
                    displayName = titleArg.ifBlank { key },
                )
                loadSummary(source)
                controller.open(
                    source = source,
                    startPositionMs = startArg,
                )
            } else {
                val history = container.playbackRepository.find(key, SourceType.NETWORK)
                val start = history?.lastPositionMs ?: startArg
                resumePosition.value = start
                controller.open(
                    source = NetworkUrlSource(url = key, displayName = titleArg.ifBlank { key }),
                    startPositionMs = start,
                    preferredEngine = history?.preferredEngine?.let { raw ->
                        runCatching { EngineType.valueOf(raw) }.getOrNull()
                    },
                )
            }
        }
    }

    fun startPlayback() = controller.startPlayback()

    fun toggleLock() {
        locked = !locked
    }

    fun setAspect(mode: AspectMode) {
        aspectMode = mode
        controller.setAspect(mode)
    }

    fun applySubtitleStyle(style: SubtitlePrefs) {
        subtitleStyle = style
        controller.applySubtitleStyle(style)
    }

    fun switchEngine(target: EngineType) = controller.switchEngine(target)

    suspend fun capture(): Bitmap? = controller.captureFrame()

    fun markResumeNotified() {
        resumeNotified = true
    }

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}
