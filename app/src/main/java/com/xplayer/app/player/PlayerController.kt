package com.xplayer.app.player

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import com.xplayer.app.R
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.datastore.AspectMode
import com.xplayer.app.data.datastore.SettingsRepository
import com.xplayer.app.data.datastore.SubtitlePrefs
import com.xplayer.app.data.local.SourceType
import com.xplayer.app.data.repository.PlaybackRepository
import com.xplayer.app.data.repository.ShaderRepository
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaSource
import com.xplayer.app.data.source.SidecarSubtitle
import com.xplayer.app.player.engine.EngineAvailability
import com.xplayer.app.player.engine.EngineRouter
import com.xplayer.app.player.engine.EngineType
import com.xplayer.app.player.engine.Media3EngineImpl
import com.xplayer.app.player.engine.MpvEngineImpl
import com.xplayer.app.player.engine.PlayerEngine
import com.xplayer.app.player.engine.PlayerUiState
import com.xplayer.app.player.engine.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerNotice(@StringRes val messageRes: Int, val arg: String? = null)

/**
 * 播放编排层：内核路由、无缝热切换、双向异常回退、音轨/字幕恢复、断点持久化与自动连播。
 */
class PlayerController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val playbackRepository: PlaybackRepository,
    private val shaderRepository: ShaderRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 进度落库使用独立作用域，在关闭界面瞬间提交最终写入，并在 release() 后排空 */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _engine = MutableStateFlow<PlayerEngine?>(null)
    val engine: StateFlow<PlayerEngine?> = _engine.asStateFlow()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _notice = MutableSharedFlow<PlayerNotice>(extraBufferCapacity = 8)
    val notice = _notice.asSharedFlow()

    private val _currentSource = MutableStateFlow<MediaSource?>(null)
    val currentSourceFlow: StateFlow<MediaSource?> = _currentSource.asStateFlow()

    private val _canPrevious = MutableStateFlow(false)
    val canPrevious: StateFlow<Boolean> = _canPrevious.asStateFlow()

    private val _canNext = MutableStateFlow(false)
    val canNext: StateFlow<Boolean> = _canNext.asStateFlow()

    var settings: AppSettings = AppSettings()
        private set

    var playlistIndex: Int = 0
        private set

    private var playlistItems: List<MediaSource> = emptyList()
    private var source: MediaSource? = null
    private var hasEmbeddedAss = false
    private var pendingStartMs = 0L
    private var playbackStarted = false
    private var openJob: Job? = null
    private var stateJob: Job? = null
    private var persistJob: Job? = null
    private var manualEngine: EngineType? = null
    private var fallbackAttempted = false
    private var currentSpeed = 1f
    private var currentVolume = 1f
    private var liveSubtitleStyle: SubtitlePrefs = SubtitlePrefs()
    private var liveAspect: AspectMode = AspectMode.FIT
    private var activeShaderPaths: List<String> = emptyList()
    private val extraImportedSubtitles = mutableListOf<SidecarSubtitle>()
    private var pendingAudioId: String? = null
    private var pendingSubtitleId: String? = null
    private var pendingSubtitleExplicit = false
    private var pendingSubtitleDelayMs = 0L

    init {
        scope.launch {
            _state.map { it.ended }.distinctUntilChanged().collect { ended ->
                if (ended && settings.playback.autoPlayNext && playlistIndex < playlistItems.lastIndex) {
                    step(1)
                }
            }
        }
    }

    fun open(
        source: MediaSource,
        startPositionMs: Long,
        hasEmbeddedAss: Boolean = false,
        playlist: List<MediaSource> = listOf(source),
        playlistIndex: Int = 0,
        preferredEngine: EngineType? = null,
        savedAudioId: String? = null,
        savedSubtitleId: String? = null,
        hasSavedTracks: Boolean = false,
        savedSubtitleDelayMs: Long = 0L,
    ) {
        openJob?.cancel()
        openJob = scope.launch {
            settings = settingsRepository.settings.first()
            liveSubtitleStyle = settings.subtitle
            playlistItems = playlist.ifEmpty { listOf(source) }
            val safeIndex = playlistIndex.coerceIn(0, playlistItems.lastIndex)
            this@PlayerController.playlistIndex = safeIndex
            this@PlayerController.source = source
            _currentSource.value = source
            updatePlaylistNav()

            this@PlayerController.hasEmbeddedAss = hasEmbeddedAss
            extraImportedSubtitles.clear()
            pendingStartMs = startPositionMs.coerceAtLeast(0L)
            manualEngine = preferredEngine
            fallbackAttempted = false
            currentSpeed = settings.playback.defaultSpeed
            pendingAudioId = savedAudioId
            pendingSubtitleId = savedSubtitleId
            pendingSubtitleExplicit = hasSavedTracks
            pendingSubtitleDelayMs = savedSubtitleDelayMs
            activeShaderPaths = if (settings.engine.shaderPipelineEnabled) {
                runCatching { shaderRepository.enabledPaths() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val decision = EngineRouter.decide(
                settings = settings,
                source = source,
                hasEmbeddedAss = hasEmbeddedAss,
                hasActiveShaders = activeShaderPaths.isNotEmpty(),
                preferred = preferredEngine,
            )
            val newEngine = createEngine(decision.engine)
            replaceEngine(_engine.value, newEngine)
            if (decision.assAutoRouted) {
                _notice.tryEmit(PlayerNotice(R.string.player_ass_routed))
            }
        }
    }

    /** 视图就绪后由 UI 调用，真正开始解码；同时恢复音轨/字幕偏好与外挂字幕 */
    fun startPlayback() {
        val engine = _engine.value ?: return
        val currentSource = source ?: return
        if (playbackStarted) return
        playbackStarted = true
        when (engine) {
            is Media3EngineImpl -> engine.setPreferredTracks(
                audioId = pendingAudioId,
                subtitleId = pendingSubtitleId,
                subtitleExplicit = pendingSubtitleExplicit,
            )
            is MpvEngineImpl -> engine.setPreferredTracks(
                audioId = pendingAudioId,
                subtitleId = pendingSubtitleId,
                subtitleExplicit = pendingSubtitleExplicit,
                delayMs = pendingSubtitleDelayMs,
            )
        }
        engine.setPreciseSeek(settings.gesture.preciseSeek)
        engine.setAspectRatio(liveAspect)
        engine.setVolume(currentVolume)
        val mergedSource = if (currentSource is LocalFileSource && extraImportedSubtitles.isNotEmpty()) {
            currentSource.copy(
                subtitles = (currentSource.subtitles + extraImportedSubtitles).distinctBy { it.path },
            )
        } else {
            currentSource
        }
        engine.prepare(mergedSource, pendingStartMs, liveSubtitleStyle, currentSpeed)
        if (mergedSource !is LocalFileSource) {
            extraImportedSubtitles.forEach { engine.addExternalSubtitle(it) }
        }
        startPersistence()
    }

    fun retry() {
        val currentSource = source ?: return
        val activeEngine = _engine.value ?: return
        fallbackAttempted = false
        pendingStartMs = _state.value.positionMs.coerceAtLeast(0L)
        playbackStarted = false
        val replacement = createEngine(activeEngine.type)
        replaceEngine(activeEngine, replacement)
        if (currentSource !is ContentUriSource) startPersistence()
    }

    fun switchEngine(target: EngineType) {
        val current = _engine.value ?: return
        if (current.type == target) return
        if (target == EngineType.MPV && !EngineAvailability.mpvAvailable) {
            _notice.tryEmit(PlayerNotice(R.string.player_engine_unavailable))
            return
        }
        if (target == EngineType.MPV && source is ContentUriSource) {
            _notice.tryEmit(PlayerNotice(R.string.player_engine_uri_media3_only))
            return
        }
        snapshotStateForHandoff()
        manualEngine = target
        val newEngine = createEngine(target)
        replaceEngine(current, newEngine)
        _notice.tryEmit(PlayerNotice(R.string.player_switching, target.label()))
    }

    fun close() {
        openJob?.cancel()
        openJob = null
        persistPositionNow()
        persistJob?.cancel()
        persistJob = null
        stateJob?.cancel()
        stateJob = null
        runCatching { _engine.value?.release() }
        _engine.value = null
        playbackStarted = false
    }

    fun release() {
        close()
        scope.cancel()
        ioScope.launch {
            delay(1_500L)
            ioScope.cancel()
        }
    }

    // ---- 基础播控 ----

    fun togglePlayPause() {
        val snap = _state.value
        when {
            snap.ended -> {
                seekTo(0L)
                _engine.value?.play()
            }
            snap.isPlaying -> _engine.value?.pause()
            else -> _engine.value?.play()
        }
    }

    fun pause() = _engine.value?.pause()

    fun seekTo(positionMs: Long, persistImmediate: Boolean = false) {
        val duration = _state.value.durationMs
        val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        _engine.value?.seekTo(target)
        if (persistImmediate) persistPositionNow(allowZero = target == 0L)
    }

    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.25f, 4f)
        _engine.value?.setSpeed(currentSpeed)
    }

    fun setAspect(mode: AspectMode) {
        liveAspect = mode
        _engine.value?.setAspectRatio(mode)
    }

    fun selectAudio(track: TrackInfo) = _engine.value?.selectAudioTrack(track)

    fun selectSubtitle(track: TrackInfo?) = _engine.value?.selectSubtitleTrack(track)

    fun addExternalSubtitle(subtitle: SidecarSubtitle) {
        extraImportedSubtitles.removeAll { it.path == subtitle.path }
        extraImportedSubtitles += subtitle
        _engine.value?.addExternalSubtitle(subtitle)
    }

    fun setSubtitleDelay(delayMs: Long) {
        pendingSubtitleDelayMs = delayMs
        _engine.value?.setSubtitleDelay(delayMs)
    }

    fun applySubtitleStyle(style: SubtitlePrefs) {
        liveSubtitleStyle = style
        _engine.value?.applySubtitleStyle(style)
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 2f)
        _engine.value?.setVolume(currentVolume)
    }

    fun next() = step(1)

    fun previous() = step(-1)

    fun currentSource(): MediaSource? = source

    suspend fun captureFrame(): Bitmap? = _engine.value?.captureFrame()

    private fun step(delta: Int) {
        val target = playlistIndex + delta
        if (target !in playlistItems.indices) return
        val nextSource = playlistItems[target]
        persistPositionNow()
        fallbackAttempted = false
        manualEngine = null
        extraImportedSubtitles.clear()
        pendingAudioId = null
        pendingSubtitleId = null
        pendingSubtitleExplicit = false
        pendingSubtitleDelayMs = 0L

        val assHint = (nextSource as? LocalFileSource)?.hasEmbeddedAss ?: false
        hasEmbeddedAss = assHint
        pendingStartMs = 0L
        playlistIndex = target
        source = nextSource
        _currentSource.value = nextSource
        updatePlaylistNav()

        val decision = EngineRouter.decide(
            settings = settings,
            source = nextSource,
            hasEmbeddedAss = assHint,
            hasActiveShaders = activeShaderPaths.isNotEmpty(),
            preferred = null,
        )
        val currentEngine = _engine.value
        if (currentEngine is Media3EngineImpl && decision.engine == EngineType.MEDIA3) {
            // 同为 Media3 时直接复用 ExoPlayer 与视图，避免剧集连播重建解码器闪烁
            playbackStarted = false
            startPlayback()
        } else {
            val newEngine = createEngine(decision.engine)
            replaceEngine(currentEngine, newEngine)
        }
        if (decision.assAutoRouted) {
            _notice.tryEmit(PlayerNotice(R.string.player_ass_routed))
        }
    }

    private fun updatePlaylistNav() {
        _canPrevious.value = playlistIndex > 0
        _canNext.value = playlistIndex < playlistItems.lastIndex
    }

    private fun snapshotStateForHandoff() {
        val snap = _state.value
        pendingStartMs = snap.positionMs.coerceAtLeast(0L)
        currentSpeed = if (snap.speed > 0f) snap.speed else currentSpeed
        pendingAudioId = snap.selectedAudioId
        pendingSubtitleId = snap.selectedSubtitleId
        pendingSubtitleExplicit = snap.ready
        pendingSubtitleDelayMs = snap.subtitleDelayMs
        persistPositionNow()
    }

    private fun createEngine(type: EngineType): PlayerEngine = when (type) {
        EngineType.MEDIA3 -> Media3EngineImpl(context, settings.engine.frameRateMatching)
        EngineType.MPV -> MpvEngineImpl(
            context = context,
            hwDecoder = settings.engine.mpvHwDecoder,
            mpvConf = if (settings.engine.customMpvConfEnabled) settings.engine.mpvConf else "",
            shaderPaths = activeShaderPaths,
        )
    }

    private fun replaceEngine(previous: PlayerEngine?, next: PlayerEngine) {
        // 先同步停用并释放旧内核（防止中低端 SoC 单硬解槽位冲突），再绑定新内核
        stateJob?.cancel()
        if (previous != null && previous !== next) {
            runCatching {
                previous.pause()
                previous.release()
            }
        }
        playbackStarted = false
        bindEngine(next)
    }

    private fun bindEngine(newEngine: PlayerEngine) {
        stateJob?.cancel()
        _engine.value = newEngine
        _state.value = PlayerUiState(
            engine = newEngine.type,
            speed = currentSpeed,
            supportsSubtitleDelay = newEngine.type == EngineType.MPV,
        )
        stateJob = scope.launch {
            newEngine.state.collect { incoming ->
                _state.value = incoming.copy(
                    speed = if (incoming.speed > 0f) incoming.speed else currentSpeed,
                    supportsSubtitleDelay = newEngine.type == EngineType.MPV,
                )
                if (incoming.error != null) handleEngineError(newEngine, incoming.error)
            }
        }
    }

    private fun handleEngineError(failedEngine: PlayerEngine, error: String) {
        if (failedEngine !== _engine.value) return
        if (source is ContentUriSource || fallbackAttempted) return

        val target = when (failedEngine.type) {
            EngineType.MEDIA3 -> if (EngineAvailability.mpvAvailable) EngineType.MPV else null
            EngineType.MPV -> EngineType.MEDIA3
        } ?: return

        fallbackAttempted = true
        Log.w(TAG, "${failedEngine.type} failed ($error), falling back to $target")
        snapshotStateForHandoff()
        _notice.tryEmit(
            PlayerNotice(
                if (target == EngineType.MPV) R.string.player_error_retry_mpv else R.string.player_error_retry_media3,
            ),
        )
        replaceEngine(failedEngine, createEngine(target))
    }

    private fun startPersistence() {
        persistJob?.cancel()
        persistJob = scope.launch {
            while (isActive) {
                delay(PERSIST_INTERVAL_MS)
                capturePersistSnapshot(allowZero = false)?.let { snapshot ->
                    runCatching { persist(snapshot) }
                }
            }
        }
    }

    private fun persistPositionNow(allowZero: Boolean = false) {
        val snapshot = capturePersistSnapshot(allowZero) ?: return
        ioScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { persist(snapshot) }
            }
        }
    }

    private data class PersistSnapshot(
        val src: MediaSource,
        val uiState: PlayerUiState,
        val preferredEngine: String?,
        val overridePositionMs: Long?,
    )

    private fun capturePersistSnapshot(allowZero: Boolean): PersistSnapshot? {
        val src = source ?: return null
        if (src is ContentUriSource) return null
        val uiState = _state.value
        if (uiState.durationMs <= 0L) return null
        if (!allowZero && uiState.positionMs < 1_000L && !uiState.ended) return null
        return PersistSnapshot(
            src = src,
            uiState = uiState,
            preferredEngine = manualEngine?.name,
            overridePositionMs = if (allowZero) 0L else null,
        )
    }

    private suspend fun persist(snapshot: PersistSnapshot) {
        val src = snapshot.src
        val uiState = snapshot.uiState
        val pos = snapshot.overridePositionMs ?: if (uiState.ended) uiState.durationMs else uiState.positionMs
        playbackRepository.save(
            mediaId = src.key,
            sourceType = if (src.isLocal) SourceType.LOCAL else SourceType.NETWORK,
            title = src.displayName,
            positionMs = pos,
            durationMs = uiState.durationMs,
            preferredEngine = snapshot.preferredEngine,
            subtitleDelayMs = uiState.subtitleDelayMs,
            audioTrackId = uiState.selectedAudioId,
            subtitleTrackId = uiState.selectedSubtitleId,
            tracksLoaded = uiState.ready,
            folderPath = src.folderPath,
        )
    }

    private fun EngineType.label(): String = when (this) {
        EngineType.MEDIA3 -> "Media3"
        EngineType.MPV -> "libmpv"
    }

    companion object {
        private const val TAG = "PlayerController"
        private const val PERSIST_INTERVAL_MS = 3_000L
    }
}
