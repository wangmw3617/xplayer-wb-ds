package com.xplayer.app.player.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.xplayer.app.R
import com.xplayer.app.data.datastore.AspectMode
import com.xplayer.app.data.datastore.MpvHwDecoder
import com.xplayer.app.data.datastore.SubtitlePrefs
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaSource
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.data.source.SidecarSubtitle
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * libmpv 内核：负责 ASS/SSA 特效字幕（libass）、GLSL 着色器、mpv.conf 覆盖与软硬解切换。
 * 全部磁盘 I/O 与 JNI 属性调用均收敛在单线程 mpvDispatcher，暂停时停止轮询实现零空转。
 */
class MpvEngineImpl(
    private val context: Context,
    private val hwDecoder: MpvHwDecoder = MpvHwDecoder.MEDIACODEC,
    private val mpvConf: String = "",
    private val shaderPaths: List<String> = emptyList(),
) : PlayerEngine {

    override val type: EngineType = EngineType.MPV

    private val _state = MutableStateFlow(
        PlayerUiState(engine = EngineType.MPV, supportsSubtitleDelay = true),
    )
    override val state = _state.asStateFlow()

    private val mpvExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "xplayer-mpv").apply { isDaemon = true }
    }
    private val mpvDispatcher = mpvExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + mpvDispatcher)
    private var pollJob: Job? = null

    private val audioFocusHelper = PlaybackAudioFocusHelper(
        context = context,
        onPauseRequired = { pause() },
        onVolumeDuckChanged = { multiplier ->
            duckMultiplier = multiplier
            applyEffectiveVolume()
        },
    )

    @Volatile
    private var released = false
    private var videoView: BaseMPVView? = null
    private var mpv: MPV? = null
    private var initialized = false
    private var pendingSidecars: List<SidecarSubtitle> = emptyList()
    private var sidecarsLoaded = false
    private var preciseSeek = false
    private var userVolume = 1f
    private var duckMultiplier = 1f
    private var pendingAudioId: String? = null
    private var pendingSubtitleId: String? = null
    private var pendingSubtitleExplicit = false
    private var pendingSubtitleDelayMs = 0L

    @Volatile
    private var lastTrackCount = -1
    private var lastAid: String? = null
    private var lastSid: String? = null
    private var cachedTracks = TrackSnapshot(emptyList(), emptyList(), null, null)

    override fun createVideoView(context: Context): View {
        videoView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            existing.keepScreenOn = true
            return existing
        }
        val view = BaseMPVView(context, null).apply {
            keepScreenOn = true
        }
        videoView = view
        return view
    }

    fun setPreferredTracks(audioId: String?, subtitleId: String?, subtitleExplicit: Boolean, delayMs: Long) {
        pendingAudioId = audioId
        pendingSubtitleId = subtitleId
        pendingSubtitleExplicit = subtitleExplicit
        pendingSubtitleDelayMs = delayMs
    }

    override fun prepare(source: MediaSource, startPositionMs: Long, subtitleStyle: SubtitlePrefs, speed: Float) {
        if (released) return
        val view = videoView ?: run {
            _state.value = _state.value.copy(error = "MPV_VIEW_MISSING")
            return
        }
        if (source is ContentUriSource) {
            _state.value = _state.value.copy(error = "MPV_CONTENT_URI_UNSUPPORTED", isBuffering = false)
            return
        }

        val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
        _state.value = _state.value.copy(
            ready = false,
            isBuffering = true,
            isPlaying = false,
            error = null,
            ended = false,
            positionMs = startPositionMs.coerceAtLeast(0L),
            speed = clampedSpeed,
            engine = EngineType.MPV,
        )

        scope.launch {
            if (released) return@launch
            val configDir = File(context.filesDir, "mpv").apply { mkdirs() }
            val cacheDir = File(context.cacheDir, "mpv").apply { mkdirs() }
            val confFile = File(configDir, "mpv.conf")
            runCatching {
                if (mpvConf.isNotBlank()) {
                    confFile.writeText(mpvConf)
                } else if (confFile.exists()) {
                    confFile.delete()
                }
            }

            if (!initialized) {
                val initResult = runCatching {
                    withContext(Dispatchers.Main.immediate) {
                        if (!released) {
                            view.initialize(configDir.absolutePath, cacheDir.absolutePath)
                        }
                    }
                    mpv = view.mpv
                    initialized = mpv != null
                }
                val err = initResult.exceptionOrNull()
                if (err != null || !initialized) {
                    Log.e(TAG, "mpv init failed", err)
                    if (err is LinkageError) {
                        EngineAvailability.mpvAvailable = false
                    }
                    _state.value = _state.value.copy(
                        error = err?.message ?: "MPV_UNAVAILABLE",
                        isBuffering = false,
                    )
                    return@launch
                }
            }

            if (released) return@launch
            val instance = mpv ?: return@launch
            pendingSidecars = if (source is LocalFileSource) source.subtitles else emptyList()
            sidecarsLoaded = false
            lastTrackCount = -1
            lastAid = null
            lastSid = null
            cachedTracks = TrackSnapshot(emptyList(), emptyList(), null, null)

            runCatching {
                applyOptions(instance)
                applySubtitleStyleSync(instance, subtitleStyle)
                instance.setPropertyDouble("speed", clampedSpeed.toDouble())
                instance.setPropertyDouble("volume", effectiveMpvVolume())

                if (source is NetworkUrlSource && source.headers.isNotEmpty()) {
                    // mpv 中带逗号的 header 值需整段双引号转义
                    val fields = source.headers.entries.joinToString(",") { (k, v) ->
                        val escaped = "$k: $v".replace("\"", "\\\"")
                        if (',' in escaped) "\"$escaped\"" else escaped
                    }
                    instance.setOptionString("http-header-fields", fields)
                }
                val target = when (source) {
                    is LocalFileSource -> source.path
                    is NetworkUrlSource -> source.url
                    is ContentUriSource -> return@launch
                }
                if (startPositionMs > 0L) {
                    instance.setOptionString("start", "+${startPositionMs / 1000.0}")
                } else {
                    instance.setOptionString("start", "none")
                }
                view.playFile(target)
                audioFocusHelper.acquireOnPlay()
                instance.setPropertyBoolean("pause", false)
                if (pendingSubtitleDelayMs != 0L) {
                    instance.setPropertyDouble("sub-delay", pendingSubtitleDelayMs / 1000.0)
                    _state.value = _state.value.copy(subtitleDelayMs = pendingSubtitleDelayMs)
                }
                startPolling()
            }.onFailure { error ->
                Log.e(TAG, "mpv prepare failed", error)
                if (error is LinkageError) {
                    EngineAvailability.mpvAvailable = false
                }
                _state.value = _state.value.copy(
                    error = error.message ?: "MPV_PREPARE_FAILED",
                    isBuffering = false,
                    isPlaying = false,
                )
            }
        }
    }

    private fun applyOptions(mpv: MPV) {
        runCatching {
            if (!hasCustomOption("hwdec")) mpv.setOptionString("hwdec", hwDecoder.value)
            if (!hasCustomOption("hwdec-codecs")) mpv.setOptionString("hwdec-codecs", "all")
            if (!hasCustomOption("vo")) mpv.setOptionString("vo", "gpu")
            mpv.setOptionString("osc", "no")
            mpv.setOptionString("osd-level", "0")
            mpv.setOptionString("keep-open", "yes")
            mpv.setOptionString("sub-auto", "no")
            mpv.setOptionString("hr-seek", if (preciseSeek) "yes" else "no")
            if (!hasCustomOption("user-agent")) mpv.setOptionString("user-agent", "XPlayer/1.0")
            if (!hasCustomOption("cache")) mpv.setOptionString("cache", "yes")
            if (!hasCustomOption("demuxer-max-bytes")) mpv.setOptionString("demuxer-max-bytes", "256MiB")
            if (shaderPaths.isNotEmpty()) {
                mpv.setOptionString("glsl-shaders", shaderPaths.joinToString(":"))
            }
        }.onFailure { Log.w(TAG, "applyOptions: ${it.message}") }
    }

    private fun hasCustomOption(key: String): Boolean {
        if (mpvConf.isBlank()) return false
        return mpvConf.lineSequence().any { raw ->
            val line = raw.trim()
            !line.startsWith("#") && (line.startsWith("$key=") || line.startsWith("$key "))
        }
    }

    override fun play() {
        _state.value = _state.value.copy(isPlaying = true, ended = false)
        launchMpv {
            audioFocusHelper.acquireOnPlay()
            mpv?.setPropertyBoolean("pause", false)
            refreshState()
            startPolling()
        }
    }

    override fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
        launchMpv {
            mpv?.setPropertyBoolean("pause", true)
            refreshState()
            if (_state.value.ready && !_state.value.isBuffering) {
                pollJob?.cancel()
                pollJob = null
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        val duration = _state.value.durationMs
        val clamped = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        _state.value = _state.value.copy(positionMs = clamped, ended = false)
        launchMpv {
            mpv?.command("seek", (clamped / 1000.0).toString(), "absolute+exact")
            refreshState()
            startPolling()
        }
    }

    override fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 4.0f)
        _state.value = _state.value.copy(speed = clamped)
        launchMpv { mpv?.setPropertyDouble("speed", clamped.toDouble()) }
    }

    override fun selectAudioTrack(track: TrackInfo) {
        _state.value = _state.value.copy(selectedAudioId = track.id)
        launchMpv {
            mpv?.setPropertyString("aid", track.id)
            lastTrackCount = -1
            refreshState()
        }
    }

    override fun selectSubtitleTrack(track: TrackInfo?) {
        _state.value = _state.value.copy(selectedSubtitleId = track?.id)
        launchMpv {
            mpv?.setPropertyString("sid", track?.id ?: "no")
            lastTrackCount = -1
            refreshState()
        }
    }

    override fun addExternalSubtitle(subtitle: SidecarSubtitle) {
        launchMpv {
            runCatching {
                mpv?.command("sub-add", subtitle.path, "select", subtitle.name)
                lastTrackCount = -1
                refreshState()
            }.onFailure { Log.w(TAG, "sub-add failed: ${it.message}") }
        }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        _state.value = _state.value.copy(subtitleDelayMs = delayMs)
        launchMpv { mpv?.setPropertyDouble("sub-delay", delayMs / 1000.0) }
    }

    override fun applySubtitleStyle(style: SubtitlePrefs) {
        launchMpv {
            mpv?.let { applySubtitleStyleSync(it, style) }
        }
    }

    private fun applySubtitleStyleSync(instance: MPV, style: SubtitlePrefs) {
        val scale = style.fontScalePercent / 100f
        val alpha = ((style.colorArgb ushr 24) and 0xFF).toInt()
        val rgb = (style.colorArgb and 0xFFFFFF).toInt()
        val color = String.format(
            Locale.US,
            "#%02X%02X%02X%02X",
            alpha,
            (rgb ushr 16) and 0xFF,
            (rgb ushr 8) and 0xFF,
            rgb and 0xFF,
        )
        instance.setPropertyString("sub-font-size", (52 * scale).toInt().toString())
        instance.setPropertyString("sub-scale", scale.toString())
        instance.setPropertyString("sub-color", color)
        instance.setPropertyString("sub-border-size", (2.4f * style.outlinePercent / 100f).toString())
        instance.setPropertyString("sub-shadow-offset", if (style.shadow) "1.6" else "0")
        instance.setPropertyString("sub-back-color", if (style.backgroundBar) "#A0000000" else "#00000000")
        instance.setPropertyString("sub-margin-y", style.bottomMarginDp.toString())
        instance.setPropertyString("sub-ass-override", if (style.forceAssOverride) "force" else "scale")
    }

    override fun setAspectRatio(mode: AspectMode) {
        launchMpv {
            val instance = mpv ?: return@launchMpv
            when (mode) {
                AspectMode.FIT -> {
                    instance.setPropertyString("video-aspect-override", "no")
                    instance.setPropertyBoolean("keepaspect", true)
                    instance.setPropertyDouble("panscan", 0.0)
                    instance.setPropertyDouble("video-zoom", 0.0)
                }
                AspectMode.CROP -> {
                    instance.setPropertyString("video-aspect-override", "no")
                    instance.setPropertyBoolean("keepaspect", true)
                    instance.setPropertyDouble("panscan", 1.0)
                    instance.setPropertyDouble("video-zoom", 0.0)
                }
                AspectMode.STRETCH -> {
                    instance.setPropertyString("video-aspect-override", "no")
                    instance.setPropertyBoolean("keepaspect", false)
                    instance.setPropertyDouble("panscan", 0.0)
                    instance.setPropertyDouble("video-zoom", 0.0)
                }
                AspectMode.RATIO_16_9 -> {
                    instance.setPropertyBoolean("keepaspect", true)
                    instance.setPropertyDouble("panscan", 0.0)
                    instance.setPropertyDouble("video-zoom", 0.0)
                    instance.setPropertyString("video-aspect-override", "16:9")
                }
                AspectMode.RATIO_4_3 -> {
                    instance.setPropertyBoolean("keepaspect", true)
                    instance.setPropertyDouble("panscan", 0.0)
                    instance.setPropertyDouble("video-zoom", 0.0)
                    instance.setPropertyString("video-aspect-override", "4:3")
                }
                AspectMode.ORIGINAL -> {
                    instance.setPropertyString("video-aspect-override", "no")
                    instance.setPropertyBoolean("keepaspect", true)
                    instance.setPropertyDouble("panscan", 0.0)
                    instance.setPropertyDouble("video-zoom", 0.0)
                }
            }
        }
    }

    override fun setPreciseSeek(enabled: Boolean) {
        preciseSeek = enabled
        launchMpv { mpv?.setOptionString("hr-seek", if (enabled) "yes" else "no") }
    }

    override fun setVolume(volume: Float) {
        userVolume = volume.coerceIn(0f, 2f)
        applyEffectiveVolume()
    }

    private fun effectiveMpvVolume(): Double =
        (userVolume * duckMultiplier * 100f).coerceIn(0f, 200f).toDouble()

    private fun applyEffectiveVolume() {
        launchMpv { mpv?.setPropertyDouble("volume", effectiveMpvVolume()) }
    }

    override suspend fun captureFrame(): Bitmap? = withContext(mpvDispatcher) {
        if (released || !initialized || !isActive) return@withContext null
        val bmp = runCatching { mpv?.grabThumbnail(0) }.getOrNull() ?: return@withContext null
        if (!isActive || released) {
            if (!bmp.isRecycled) bmp.recycle()
            null
        } else {
            bmp
        }
    }

    override fun release() {
        if (released) return
        released = true
        audioFocusHelper.release()
        val viewToDestroy = videoView
        val wasInitialized = initialized
        videoView = null
        pollJob?.cancel()
        pollJob = null
        CoroutineScope(Dispatchers.Main.immediate).launch {
            runCatching {
                withContext(mpvDispatcher) {
                    mpv = null
                    initialized = false
                }
            }
            if (wasInitialized && viewToDestroy != null) {
                (viewToDestroy.parent as? ViewGroup)?.removeView(viewToDestroy)
                runCatching { viewToDestroy.destroy() }
            }
            scope.cancel()
            runCatching { mpvExecutor.shutdownNow() }
        }
    }

    private fun launchMpv(block: () -> Unit) {
        if (released) return
        scope.launch {
            if (!released) {
                runCatching(block).onFailure { Log.w(TAG, "mpv op: ${it.message}") }
            }
        }
    }

    private fun startPolling() {
        if (released || pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                runCatching { refreshState() }
                val snap = _state.value
                if ((snap.ready && !snap.isPlaying && !snap.isBuffering) || snap.ended) {
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
            pollJob = null
        }
    }

    private fun refreshState() {
        val mpv = mpv ?: return
        val rawPosition = mpv.getPropertyDouble("time-pos")
        val duration = mpv.getPropertyDouble("duration") ?: 0.0
        val paused = mpv.getPropertyBoolean("pause") ?: true
        val pausedForCache = mpv.getPropertyBoolean("paused-for-cache") ?: false
        val cacheDuration = mpv.getPropertyDouble("demuxer-cache-duration") ?: 0.0
        val speed = mpv.getPropertyDouble("speed") ?: 1.0
        val width = mpv.getPropertyInt("width") ?: 0
        val height = mpv.getPropertyInt("height") ?: 0
        val eof = mpv.getPropertyBoolean("eof-reached") ?: false

        val ready = duration > 0.0 || width > 0 || !paused || rawPosition != null
        val trackCount = mpv.getPropertyInt("track-list/count") ?: 0
        if (ready && !sidecarsLoaded) {
            sidecarsLoaded = true
            loadPendingSidecars(mpv)
            lastTrackCount = -1
        }
        val aid = mpv.getPropertyString("aid")
        val sid = mpv.getPropertyString("sid")
        val (audio, subtitles, selectedAudio, selectedSubtitle) =
            if (trackCount > 0 && (trackCount != lastTrackCount || aid != lastAid || sid != lastSid)) {
                readTracks(mpv).also { snapshot ->
                    cachedTracks = snapshot
                    lastTrackCount = trackCount
                    lastAid = aid
                    lastSid = sid
                    applyPendingTracksIfNeeded(mpv, snapshot)
                }
            } else {
                cachedTracks
            }

        val resolvedPositionMs = rawPosition?.let { (it * 1000.0).toLong().coerceAtLeast(0L) } ?: _state.value.positionMs
        val durationMs = (duration * 1000.0).toLong().coerceAtLeast(0L)
        val bufferedMs = if (cacheDuration > 0.0) {
            resolvedPositionMs + (cacheDuration * 1000.0).toLong()
        } else {
            _state.value.bufferedMs
        }

        _state.value = _state.value.copy(
            ready = ready,
            isPlaying = !paused && !eof,
            isBuffering = !ready || pausedForCache,
            positionMs = resolvedPositionMs,
            durationMs = durationMs,
            bufferedMs = if (durationMs > 0L) bufferedMs.coerceAtMost(durationMs) else bufferedMs,
            speed = speed.toFloat(),
            videoWidth = width,
            videoHeight = height,
            ended = eof,
            audioTracks = audio,
            subtitleTracks = subtitles,
            selectedAudioId = selectedAudio,
            selectedSubtitleId = selectedSubtitle,
        )
    }

    private fun applyPendingTracksIfNeeded(mpv: MPV, snapshot: TrackSnapshot) {
        pendingAudioId?.let { targetAid ->
            pendingAudioId = null
            if (snapshot.audio.any { it.id == targetAid } && snapshot.selectedAudio != targetAid) {
                runCatching { mpv.setPropertyString("aid", targetAid) }
            }
        }
        if (pendingSubtitleExplicit) {
            val targetSid = pendingSubtitleId
            pendingSubtitleId = null
            pendingSubtitleExplicit = false
            if (targetSid == null) {
                runCatching { mpv.setPropertyString("sid", "no") }
            } else if (snapshot.subtitles.any { it.id == targetSid } && snapshot.selectedSubtitle != targetSid) {
                runCatching { mpv.setPropertyString("sid", targetSid) }
            }
        }
    }

    private fun loadPendingSidecars(mpv: MPV) {
        if (pendingSidecars.isEmpty()) return
        val activeSid = runCatching { mpv.getPropertyString("sid") }.getOrNull()
        val hasSelectedSub = !activeSid.isNullOrBlank() && activeSid != "no" && activeSid != "false"
        pendingSidecars.forEachIndexed { index, sub ->
            val flag = if (!hasSelectedSub && !pendingSubtitleExplicit && index == 0) "select" else "auto"
            runCatching { mpv.command("sub-add", sub.path, flag, sub.name) }
                .onFailure { Log.w(TAG, "load sidecar sub failed: ${it.message}") }
        }
    }

    private data class TrackSnapshot(
        val audio: List<TrackInfo>,
        val subtitles: List<TrackInfo>,
        val selectedAudio: String?,
        val selectedSubtitle: String?,
    )

    private fun readTracks(mpv: MPV): TrackSnapshot {
        val audio = mutableListOf<TrackInfo>()
        val subtitles = mutableListOf<TrackInfo>()
        var selectedAudio: String? = null
        var selectedSubtitle: String? = null
        runCatching {
            val node = mpv.getPropertyNode("track-list") ?: return@runCatching
            val items = node.asArray() ?: return@runCatching
            items.forEach { item ->
                val type = item.text("type") ?: return@forEach
                val id = item.number("id")?.toString() ?: return@forEach
                val title = item.text("title")
                val language = item.text("lang")
                val codec = item.text("codec")
                val external = item.bool("external") ?: false
                val selected = item.bool("selected") ?: false
                when (type) {
                    "audio" -> {
                        audio += TrackInfo(
                            id = id,
                            label = title ?: language?.uppercase(Locale.US)
                                ?: context.getString(R.string.player_track_audio_fallback, audio.size + 1),
                            language = language,
                            mimeType = codec,
                            channels = item.number("audio-channels")?.toInt(),
                        )
                        if (selected) selectedAudio = id
                    }
                    "sub" -> {
                        subtitles += TrackInfo(
                            id = id,
                            label = title ?: language?.uppercase(Locale.US)
                                ?: context.getString(R.string.player_track_subtitle_fallback, subtitles.size + 1),
                            language = language,
                            mimeType = codec,
                            isExternal = external,
                        )
                        if (selected) selectedSubtitle = id
                    }
                }
            }
        }.onFailure { Log.w(TAG, "readTracks: ${it.message}") }
        return TrackSnapshot(audio, subtitles, selectedAudio, selectedSubtitle)
    }

    private fun MPVNode.text(key: String): String? = runCatching { get(key)?.asString() }.getOrNull()

    private fun MPVNode.number(key: String): Long? = runCatching { get(key)?.asInt() }.getOrNull()

    private fun MPVNode.bool(key: String): Boolean? = runCatching { get(key)?.asBoolean() }.getOrNull()

    companion object {
        private const val TAG = "MpvEngine"
        private const val POLL_INTERVAL_MS = 300L
    }
}
