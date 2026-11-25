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
import com.xplayer.app.data.source.MediaSource
import com.xplayer.app.data.source.SidecarSubtitle
import com.xplayer.app.player.engine.EngineAvailability
import com.xplayer.app.player.engine.EngineReason
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
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext

data class PlayerNotice(@StringRes val messageRes: Int, val arg: String? = null)

/**
 * 播放编排层：内核选择、热切换、断点持久化与自动连播都在这里，
 * Compose 播放页只消费 [state] 并调用这里的方法。
 */
class PlayerController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val playbackRepository: PlaybackRepository,
    private val shaderRepository: ShaderRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 进度落库使用独立作用域，避免退出播放时被取消 */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val router = EngineRouter()

    private val _engine = MutableStateFlow<PlayerEngine?>(null)
    val engine: StateFlow<PlayerEngine?> = _engine.asStateFlow()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _notice = MutableSharedFlow<PlayerNotice>(extraBufferCapacity = 8)
    val notice = _notice.asSharedFlow()

    private val _playlist = MutableStateFlow<List<MediaSource>>(emptyList())
    val playlist: StateFlow<List<MediaSource>> = _playlist.asStateFlow()

    var settings: AppSettings = AppSettings()
        private set

    var playlistIndex: Int = 0
        private set

    var routeReason: EngineReason? = null
        private set

    private var source: MediaSource? = null
    private var hasEmbeddedAss = false
    private var pendingStartMs = 0L
    private var playbackStarted = false
    private var stateJob: Job? = null
    private var persistJob: Job? = null
    private var manualEngine: EngineType? = null
    private var fallbackAttempted = false
    private var currentSpeed = 1f
    private var liveSubtitleStyle: SubtitlePrefs = SubtitlePrefs()
    private var liveAspect: AspectMode = AspectMode.FIT
    private var activeShaderPaths: List<String> = emptyList()

    val currentEngineType: EngineType get() = _engine.value?.type ?: EngineType.MEDIA3

    val canSwitchToMpv: Boolean get() = EngineAvailability.mpvAvailable

    init {
        scope.launch {
            _state.map { it.ended }.distinctUntilChanged().collect { ended ->
                if (ended && settings.playback.autoPlayNext && _playlist.value.size > 1) {
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
    ) {
        scope.launch {
            settings = settingsRepository.settings.first()
            liveSubtitleStyle = settings.subtitle
            this@PlayerController.source = source
            this@PlayerController.hasEmbeddedAss = hasEmbeddedAss
            this@PlayerController.playlistIndex = playlistIndex.coerceAtLeast(0)
            _playlist.value = playlist.ifEmpty { listOf(source) }
            pendingStartMs = startPositionMs
            manualEngine = preferredEngine
            fallbackAttempted = false
            currentSpeed = settings.playback.defaultSpeed
            activeShaderPaths = if (settings.engine.shaderPipelineEnabled) {
                runCatching { shaderRepository.enabledPaths() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val decision = router.decide(settings, source, hasEmbeddedAss, preferredEngine)
            routeReason = decision.reason
            val newEngine = createEngine(decision.engine)
            replaceEngine(_engine.value, newEngine)
            if (decision.reason == EngineReason.ASS_AUTO_MPV) {
                _notice.tryEmit(PlayerNotice(R.string.player_ass_routed))
            }
        }
    }

    /** 视图就绪后由 UI 调用，真正开始解码 */
    fun startPlayback() {
        val engine = _engine.value ?: return
        val currentSource = source ?: return
        if (playbackStarted) return
        playbackStarted = true
        engine.setPreciseSeek(settings.gesture.preciseSeek)
        engine.applySubtitleStyle(liveSubtitleStyle)
        engine.setAspectRatio(liveAspect)
        engine.prepare(currentSource, pendingStartMs, liveSubtitleStyle, currentSpeed)
        startPersistence()
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
        val position = _state.value.positionMs
        val resumeSpeed = if (_state.value.speed > 0f) _state.value.speed else currentSpeed
        persistPositionNow()
        manualEngine = target
        pendingStartMs = position
        currentSpeed = resumeSpeed
        val newEngine = createEngine(target)
        replaceEngine(current, newEngine)
        _notice.tryEmit(PlayerNotice(R.string.player_switching, target.name))
    }

    fun close() {
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
    }

    // ---- 基础播控 ----

    fun togglePlayPause() {
        val state = _state.value
        if (state.isPlaying) _engine.value?.pause() else _engine.value?.play()
    }

    fun play() = _engine.value?.play()

    fun pause() = _engine.value?.pause()

    fun seekTo(positionMs: Long) {
        val duration = _state.value.durationMs
        val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        _engine.value?.seekTo(target)
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

    fun addExternalSubtitle(subtitle: SidecarSubtitle) = _engine.value?.addExternalSubtitle(subtitle)

    fun setSubtitleDelay(delayMs: Long) = _engine.value?.setSubtitleDelay(delayMs)

    fun applySubtitleStyle(style: SubtitlePrefs) {
        liveSubtitleStyle = style
        _engine.value?.applySubtitleStyle(style)
    }

    fun setVolume(volume: Float) = _engine.value?.setVolume(volume)

    fun next() = step(1)

    fun previous() = step(-1)

    val hasNext: Boolean get() = playlistIndex < _playlist.value.lastIndex

    val hasPrevious: Boolean get() = playlistIndex > 0

    fun currentSource(): MediaSource? = source

    suspend fun captureFrame(): Bitmap? = _engine.value?.captureFrame()

    private fun step(delta: Int) {
        val list = _playlist.value
        val target = playlistIndex + delta
        if (target !in list.indices) return
        val nextSource = list[target]
        persistPositionNow()
        fallbackAttempted = false
        hasEmbeddedAss = false
        pendingStartMs = 0L
        playlistIndex = target
        source = nextSource
        val decision = router.decide(settings, nextSource, hasEmbeddedAss = false, preferred = manualEngine)
        routeReason = decision.reason
        val newEngine = createEngine(decision.engine)
        replaceEngine(_engine.value, newEngine)
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
        runCatching { previous?.pause() }
        bindEngine(next)
        playbackStarted = false
        if (previous != null && previous !== next) {
            scope.launch {
                // 等待 Compose 将旧视频渲染视图移出树再销毁底层播放器，且退出取消时仍通过 NonCancellable 释放
                try {
                    delay(120)
                } finally {
                    withContext(NonCancellable) {
                        runCatching { previous.release() }
                    }
                }
            }
        }
    }

    private fun bindEngine(newEngine: PlayerEngine) {
        stateJob?.cancel()
        _engine.value = newEngine
        _state.value = _state.value.copy(engine = newEngine.type, error = null)
        stateJob = scope.launch {
            newEngine.state.collect { incoming ->
                _state.value = incoming.copy(
                    speed = if (incoming.speed > 0f) incoming.speed else currentSpeed,
                    subtitleDelayMs = incoming.subtitleDelayMs,
                    supportsSubtitleDelay = newEngine.type == EngineType.MPV,
                )
                if (incoming.error != null) handleEngineError(newEngine, incoming.error)
            }
        }
    }

    private fun handleEngineError(failedEngine: PlayerEngine, error: String) {
        if (failedEngine !== _engine.value) return
        // content:// 换 mpv 也没用，直接停在报错上
        if (source is ContentUriSource) return
        if (failedEngine.type == EngineType.MEDIA3 && !fallbackAttempted && EngineAvailability.mpvAvailable) {
            fallbackAttempted = true
            Log.w(TAG, "Media3 failed ($error), falling back to libmpv")
            _notice.tryEmit(PlayerNotice(R.string.player_error_retry_mpv))
            val position = _state.value.positionMs
            pendingStartMs = position
            val mpvEngine = createEngine(EngineType.MPV)
            replaceEngine(failedEngine, mpvEngine)
        }
    }

    private fun startPersistence() {
        persistJob?.cancel()
        persistJob = scope.launch {
            while (isActive) {
                delay(PERSIST_INTERVAL_MS)
                capturePersistSnapshot()?.let { snapshot ->
                    runCatching { persist(snapshot) }
                }
            }
        }
    }

    private fun persistPositionNow() {
        val snapshot = capturePersistSnapshot() ?: return
        ioScope.launch { runCatching { persist(snapshot) } }
    }

    private data class PersistSnapshot(
        val src: MediaSource,
        val uiState: PlayerUiState,
        val preferredEngine: String?,
    )

    private fun capturePersistSnapshot(): PersistSnapshot? {
        val src = source ?: return null
        // 外部 content:// 的临时授权重启后失效，落库只会留下点不开的僵尸记录
        if (src is ContentUriSource) return null
        val uiState = _state.value
        if (uiState.durationMs <= 0) return null
        if (uiState.positionMs < 1_000L && !uiState.ended) return null
        return PersistSnapshot(src = src, uiState = uiState, preferredEngine = manualEngine?.name)
    }

    private suspend fun persist(snapshot: PersistSnapshot) {
        val src = snapshot.src
        val uiState = snapshot.uiState
        playbackRepository.save(
            mediaId = src.key,
            sourceType = if (src.isLocal) SourceType.LOCAL else SourceType.NETWORK,
            title = src.displayName,
            positionMs = if (uiState.ended) uiState.durationMs else uiState.positionMs,
            durationMs = uiState.durationMs,
            preferredEngine = snapshot.preferredEngine,
            subtitleDelayMs = uiState.subtitleDelayMs,
            audioTrackId = uiState.selectedAudioId,
            subtitleTrackId = uiState.selectedSubtitleId,
            thumbnailPath = null,
            folderPath = src.folderPath,
        )
    }

    companion object {
        private const val TAG = "PlayerController"
        private const val PERSIST_INTERVAL_MS = 3_000L
    }
}
