package com.xplayer.app.ui.player

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xplayer.app.AppContainer
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.datastore.AspectMode
import com.xplayer.app.data.datastore.RotationMode
import com.xplayer.app.data.datastore.SubtitlePrefs
import com.xplayer.app.data.local.SourceType
import com.xplayer.app.data.repository.StreamRepository
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class PlayerViewModel(
    private val container: AppContainer,
    val sourceKind: String,
    val sourceKey: String,
    val sourceTitle: String,
    private val startArg: Long,
    private val assHintFromNav: Boolean = false,
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
    val canPrevious = controller.canPrevious
    val canNext = controller.canNext

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    var locked by mutableStateOf(false)
        private set

    var subtitleStyle by mutableStateOf(SubtitlePrefs())
        private set

    var aspectMode by mutableStateOf(AspectMode.FIT)
        private set

    var rotationMode by mutableStateOf(RotationMode.AUTO)
        private set

    var zoomScale by mutableFloatStateOf(1f)
        private set

    var zoomOffset by mutableStateOf(Offset.Zero)
        private set

    var muted by mutableStateOf(false)
        private set

    var backgroundServiceActive by mutableStateOf(false)

    var orientationApplied = false

    /** 当前实际在播片源标识，随播放列表上一集/下一集实时更新 */
    var activeTitle by mutableStateOf(sourceTitle)
        private set
    var activeKey by mutableStateOf(sourceKey)
        private set
    var activeHeadersJson: String? = null
        private set

    /** 断点续播后提示用 */
    val resumePosition = MutableStateFlow(0L)
    var resumeNotified = false
        private set

    private val isLocal: Boolean = sourceKind == "local"
    private val isExternalUri: Boolean = sourceKind == "uri"

    init {
        container.mediaScanner.onPlaybackStarted()
        viewModelScope.launch {
            controller.currentSourceFlow.collectLatest { src ->
                if (src != null) {
                    activeTitle = src.displayName
                    activeKey = src.key
                    if (src.key != sourceKey) {
                        resumePosition.value = 0L
                        resumeNotified = true
                        orientationApplied = false
                    }
                }
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val snapshot = container.settingsRepository.settings.first()
            subtitleStyle = snapshot.subtitle

            if (isLocal) {
                val localData = withContext(Dispatchers.IO) {
                    val video = container.videoRepository.find(sourceKey)
                    val siblings = if (video != null) {
                        container.videoRepository.videosInFolder(video.folderPath)
                            .sortedBy { it.title.lowercase(Locale.US) }
                    } else {
                        emptyList()
                    }
                    val folderPath = video?.folderPath ?: sourceKey.substringBeforeLast('/', "")
                    val paths = (siblings.map { it.path } + sourceKey).distinct()
                    val subtitlesByPath = if (folderPath.isNotBlank()) {
                        SubtitleFinder.findForFolder(folderPath, paths)
                    } else {
                        emptyMap()
                    }
                    val playlist: List<MediaSource> = siblings.map { item ->
                        LocalFileSource(
                            path = item.path,
                            displayName = item.title,
                            subtitles = subtitlesByPath[item.path].orEmpty(),
                            folderPath = item.folderPath,
                            hasEmbeddedAss = item.hasAssSubtitle,
                        )
                    }
                    val index = playlist.indexOfFirst { it.key == sourceKey }.coerceAtLeast(0)
                    val hasAss = (video?.hasAssSubtitle ?: false) || assHintFromNav
                    val current = playlist.getOrNull(index) ?: LocalFileSource(
                        path = sourceKey,
                        displayName = sourceTitle,
                        subtitles = subtitlesByPath[sourceKey] ?: SubtitleFinder.find(sourceKey),
                        folderPath = folderPath,
                        hasEmbeddedAss = hasAss,
                    )
                    val history = container.playbackRepository.find(sourceKey, SourceType.LOCAL)
                    Triple(
                        playlist.ifEmpty { listOf(current) } to index,
                        current to hasAss,
                        history,
                    )
                }
                val (playlistPair, currentPair, history) = localData
                val (playlist, index) = playlistPair
                val (current, hasAss) = currentPair
                val start = when {
                    startArg > 0L -> startArg
                    history == null || history.completed -> 0L
                    else -> history.lastPositionMs
                }
                resumePosition.value = if (startArg > 0L) 0L else start
                val hasSavedTracks = history != null &&
                    (history.audioTrackId != null || history.subtitleTrackId != null)
                controller.open(
                    source = current,
                    startPositionMs = start,
                    hasEmbeddedAss = hasAss,
                    playlist = playlist,
                    playlistIndex = index,
                    preferredEngine = history?.preferredEngine?.let { raw ->
                        runCatching { EngineType.valueOf(raw) }.getOrNull()
                    },
                    savedAudioId = history?.audioTrackId,
                    savedSubtitleId = history?.subtitleTrackId,
                    hasSavedTracks = hasSavedTracks,
                    savedSubtitleDelayMs = history?.subtitleDelayMs ?: 0L,
                )
            } else if (isExternalUri) {
                resumePosition.value = 0L
                val source = ContentUriSource(
                    uri = sourceKey,
                    displayName = sourceTitle.ifBlank { sourceKey },
                )
                controller.open(
                    source = source,
                    startPositionMs = startArg,
                )
            } else {
                val (history, stream) = withContext(Dispatchers.IO) {
                    container.playbackRepository.find(sourceKey, SourceType.NETWORK) to
                        container.streamRepository.find(sourceKey)
                }
                val headersJson = stream?.headersJson ?: history?.customHeaders
                activeHeadersJson = headersJson
                val start = when {
                    startArg > 0L -> startArg
                    history == null || history.completed -> 0L
                    else -> history.lastPositionMs
                }
                resumePosition.value = if (startArg > 0L) 0L else start
                val hasSavedTracks = history != null &&
                    (history.audioTrackId != null || history.subtitleTrackId != null)
                controller.open(
                    source = NetworkUrlSource(
                        url = sourceKey,
                        displayName = stream?.customTitle ?: sourceTitle.ifBlank { sourceKey },
                        headers = StreamRepository.parseHeaders(headersJson),
                    ),
                    startPositionMs = start,
                    preferredEngine = history?.preferredEngine?.let { raw ->
                        runCatching { EngineType.valueOf(raw) }.getOrNull()
                    },
                    savedAudioId = history?.audioTrackId,
                    savedSubtitleId = history?.subtitleTrackId,
                    hasSavedTracks = hasSavedTracks,
                    savedSubtitleDelayMs = history?.subtitleDelayMs ?: 0L,
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

    fun setRotation(mode: RotationMode) {
        rotationMode = mode
    }

    fun updateZoom(scale: Float, offset: Offset) {
        zoomScale = scale
        zoomOffset = offset
    }

    fun resetZoom() {
        zoomScale = 1f
        zoomOffset = Offset.Zero
    }

    fun setMutedState(value: Boolean) {
        muted = value
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

    fun syncFromBackgroundAudio(positionMs: Long, resumePlaying: Boolean) {
        backgroundServiceActive = false
        if (positionMs >= 0L) {
            controller.seekTo(positionMs)
        }
        if (resumePlaying && !state.value.isPlaying) {
            controller.togglePlayPause()
        }
    }

    override fun onCleared() {
        container.mediaScanner.onPlaybackStopped()
        controller.release()
        super.onCleared()
    }
}
