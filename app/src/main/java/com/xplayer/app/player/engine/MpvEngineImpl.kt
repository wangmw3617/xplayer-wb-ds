package com.xplayer.app.player.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
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
import java.io.File
import java.util.concurrent.Executors
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

/**
 * libmpv 内核：负责 ASS/SSA 特效字幕（libass）、GLSL 着色器、mpv.conf 覆盖与软硬解切换。
 * 通过轮询 mpv 属性刷新 UI 状态，避免依赖事件回调的线程约束。
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

    // 单线程执行器串行化全部 mpv JNI 调用，避免主线程 ANR 及 release() 期间并发访问崩溃/死锁
    private val mpvExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "xplayer-mpv").apply { isDaemon = true }
    }
    private val mpvDispatcher = mpvExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + mpvDispatcher)
    private var pollJob: Job? = null

    @Volatile
    private var released = false
    private var videoView: BaseMPVView? = null
    private var mpv: MPV? = null
    private var initialized = false
    private var subtitleStyle = SubtitlePrefs()
    private var pendingSidecars: List<SidecarSubtitle> = emptyList()
    private var sidecarsLoaded = false
    @Volatile
    private var lastTrackCount = -1
    private var lastAid: String? = null
    private var lastSid: String? = null
    private var cachedTracks = TrackSnapshot(emptyList(), emptyList(), null, null)

    override fun createVideoView(context: Context): View {
        val view = BaseMPVView(context, null)
        videoView = view
        return view
    }

    override fun prepare(source: MediaSource, startPositionMs: Long, subtitleStyle: SubtitlePrefs, speed: Float) {
        if (released) return
        val view = videoView ?: run {
            _state.value = _state.value.copy(error = "MPV_VIEW_MISSING")
            return
        }
        if (!ensureInitialized(view)) return
        if (source is ContentUriSource) {
            // mpv 无法直接打开 content://，路由层已拦截，这里只做兜底
            _state.value = _state.value.copy(
                error = "MPV_CONTENT_URI_UNSUPPORTED",
                isBuffering = false,
            )
            return
        }

        this.subtitleStyle = subtitleStyle
        pendingSidecars = if (source is LocalFileSource) source.subtitles else emptyList()
        sidecarsLoaded = false
        lastTrackCount = -1
        lastAid = null
        lastSid = null
        cachedTracks = TrackSnapshot(emptyList(), emptyList(), null, null)
        runCatching {
            applyOptions()
            applySubtitleStyle(subtitleStyle)
            mpv?.setPropertyDouble("speed", speed.coerceIn(0.25f, 4.0f).toDouble())

            if (source is NetworkUrlSource && source.headers.isNotEmpty()) {
                val fields = source.headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
                mpv?.setOptionString("http-header-fields", fields)
            }
            val target = when (source) {
                is LocalFileSource -> source.path
                is ContentUriSource -> source.uri
                is NetworkUrlSource -> source.url
            }
            if (startPositionMs > 0) {
                mpv?.setOptionString("start", "+${startPositionMs / 1000.0}")
            } else {
                mpv?.setOptionString("start", "none")
            }
            view.playFile(target)
            mpv?.setPropertyBoolean("pause", false)
            _state.value = _state.value.copy(
                ready = false,
                isBuffering = true,
                error = null,
                ended = false,
                speed = speed,
                engine = EngineType.MPV,
            )
            startPolling()
        }.onFailure { error ->
            Log.e(TAG, "mpv prepare failed", error)
            EngineAvailability.mpvAvailable = false
            _state.value = _state.value.copy(error = error.message ?: "MPV_INIT_FAILED", isBuffering = false)
        }
    }

    private fun ensureInitialized(view: BaseMPVView): Boolean {
        if (initialized) return true
        return runCatching {
            val configDir = File(context.filesDir, "mpv").apply { mkdirs() }
            val cacheDir = File(context.cacheDir, "mpv").apply { mkdirs() }
            val confFile = File(configDir, "mpv.conf")
            if (mpvConf.isNotBlank()) {
                confFile.writeText(mpvConf)
            } else if (confFile.exists()) {
                // 未启用或清空自定义配置时清理旧文件，避免 libmpv 继续读取残留 mpv.conf
                confFile.delete()
            }
            view.initialize(configDir.absolutePath, cacheDir.absolutePath)
            mpv = view.mpv
            initialized = true
            applyOptions()
            true
        }.onFailure { error ->
            Log.e(TAG, "mpv init failed", error)
            EngineAvailability.mpvAvailable = false
            _state.value = _state.value.copy(error = error.message ?: "MPV_UNAVAILABLE", isBuffering = false)
        }.getOrDefault(false)
    }

    private fun applyOptions() {
        val mpv = mpv ?: return
        runCatching {
            mpv.setOptionString("hwdec", hwDecoder.value)
            mpv.setOptionString("hwdec-codecs", "all")
            mpv.setOptionString("vo", "gpu")
            mpv.setOptionString("osc", "no")
            mpv.setOptionString("osd-level", "0")
            mpv.setOptionString("keep-open", "yes")
            mpv.setOptionString("sub-auto", "no")
            mpv.setOptionString("user-agent", "XPlayer/1.0")
            mpv.setOptionString("cache", "yes")
            mpv.setOptionString("demuxer-max-bytes", "256MiB")
            if (shaderPaths.isNotEmpty()) {
                mpv.setOptionString("glsl-shaders", shaderPaths.joinToString(":"))
            }
        }.onFailure { Log.w(TAG, "applyOptions: ${it.message}") }
    }

    override fun play() {
        _state.value = _state.value.copy(isPlaying = true)
        launchMpv {
            mpv?.setPropertyBoolean("pause", false)
            refreshState()
        }
    }

    override fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
        launchMpv {
            mpv?.setPropertyBoolean("pause", true)
            refreshState()
        }
    }

    override fun seekTo(positionMs: Long) {
        _state.value = _state.value.copy(positionMs = positionMs, ended = false)
        launchMpv { mpv?.command("seek", (positionMs / 1000.0).toString(), "absolute+exact") }
    }

    override fun setSpeed(speed: Float) {
        _state.value = _state.value.copy(speed = speed)
        launchMpv { mpv?.setPropertyDouble("speed", speed.toDouble()) }
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
        _state.value = _state.value.copy(subtitleDelayMs = _state.value.subtitleDelayMs)
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
        subtitleStyle = style
        launchMpv {
            val instance = mpv ?: return@launchMpv
            val scale = style.fontScalePercent / 100f
            val alpha = ((style.colorArgb ushr 24) and 0xFF).toInt()
            val rgb = (style.colorArgb and 0xFFFFFF).toInt()
            val color = String.format("#%02X%02X%02X", (rgb ushr 16) and 0xFF, (rgb ushr 8) and 0xFF, rgb and 0xFF)
            instance.setPropertyString("sub-font-size", (52 * scale).toInt().toString())
            instance.setPropertyString("sub-color", color)
            instance.setPropertyString("sub-border-size", (2.4f * style.outlinePercent / 100f).toString())
            instance.setPropertyString("sub-shadow-offset", if (style.shadow) "1.6" else "0")
            instance.setPropertyString("sub-back-color", if (style.backgroundBar) "#A0000000" else "#00000000")
            instance.setPropertyString("sub-margin-y", style.bottomMarginDp.toString())
            instance.setPropertyString("sub-ass-override", if (style.forceAssOverride) "force" else "scale")
            instance.setPropertyBoolean("sub-ass-vsfilter-blur-compat", alpha > 0)
        }
    }

    override fun setAspectRatio(mode: AspectMode) {
        launchMpv {
            val instance = mpv ?: return@launchMpv
            when (mode) {
                AspectMode.FIT -> {
                    instance.setPropertyString("video-aspect-override", "no")
                    instance.setPropertyBoolean("keepaspect", true)
                    instance.setPropertyDouble("panscan", 0.0)
                }
                AspectMode.CROP -> {
                    instance.setPropertyBoolean("keepaspect", true)
                    instance.setPropertyDouble("panscan", 1.0)
                }
                AspectMode.STRETCH -> {
                    instance.setPropertyBoolean("keepaspect", false)
                    instance.setPropertyDouble("panscan", 0.0)
                }
                AspectMode.RATIO_16_9 -> instance.setPropertyString("video-aspect-override", "16:9")
                AspectMode.RATIO_4_3 -> instance.setPropertyString("video-aspect-override", "4:3")
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
        // mpv 默认使用精确 seek，无需切换
    }

    override fun setVolume(volume: Float) {
        launchMpv { mpv?.setPropertyDouble("volume", (volume * 100f).coerceIn(0f, 200f).toDouble()) }
    }

    override suspend fun captureFrame(): Bitmap? = withContext(mpvDispatcher) {
        if (released) null else runCatching { mpv?.grabThumbnail(0) }.getOrNull()
    }

    override fun release() {
        if (released) return
        released = true
        val viewToDestroy = videoView
        videoView = null
        pollJob?.cancel()
        pollJob = null
        // 待单线程执行器上正在进行的轮询/命令自然完成后再在主线程销毁 BaseMPVView，防止 libmpv 并发死锁
        CoroutineScope(Dispatchers.Main.immediate).launch {
            runCatching {
                withContext(mpvDispatcher) {
                    mpv = null
                    initialized = false
                }
            }
            runCatching { viewToDestroy?.destroy() }
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
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                runCatching { refreshState() }
                val snap = _state.value
                delay(if (snap.ready && !snap.isPlaying) PAUSED_POLL_INTERVAL_MS else POLL_INTERVAL_MS)
            }
        }
    }

    private fun refreshState() {
        val mpv = mpv ?: return
        val position = mpv.getPropertyDouble("time-pos") ?: 0.0
        val duration = mpv.getPropertyDouble("duration") ?: 0.0
        val paused = mpv.getPropertyBoolean("pause") ?: true
        val speed = mpv.getPropertyDouble("speed") ?: 1.0
        val width = mpv.getPropertyInt("width") ?: 0
        val height = mpv.getPropertyInt("height") ?: 0
        val eof = mpv.getPropertyBoolean("eof-reached") ?: false
        val ready = duration > 0
        if (ready && !sidecarsLoaded) {
            sidecarsLoaded = true
            loadPendingSidecars(mpv)
            lastTrackCount = -1
        }
        val trackCount = mpv.getPropertyInt("track-list/count") ?: -1
        val aid = mpv.getPropertyString("aid")
        val sid = mpv.getPropertyString("sid")
        val (audio, subtitles, selectedAudio, selectedSubtitle) =
            if (!ready || trackCount != lastTrackCount || aid != lastAid || sid != lastSid) {
                readTracks(mpv).also {
                    cachedTracks = it
                    lastTrackCount = trackCount
                    lastAid = aid
                    lastSid = sid
                }
            } else {
                cachedTracks
            }

        _state.value = _state.value.copy(
            ready = ready,
            isPlaying = !paused && !eof,
            isBuffering = !ready,
            positionMs = (position * 1000).toLong().coerceAtLeast(0L),
            durationMs = (duration * 1000).toLong().coerceAtLeast(0L),
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

    private fun loadPendingSidecars(mpv: MPV) {
        if (pendingSidecars.isEmpty()) return
        val activeSid = runCatching { mpv.getPropertyString("sid") }.getOrNull()
        val hasSelectedSub = !activeSid.isNullOrBlank() && activeSid != "no" && activeSid != "false"
        pendingSidecars.forEachIndexed { index, sub ->
            val flag = if (!hasSelectedSub && index == 0) "select" else "auto"
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
                            label = title ?: language?.uppercase() ?: "Audio ${audio.size + 1}",
                            language = language,
                            mimeType = codec,
                            channels = item.number("audio-channels")?.toInt(),
                            groupIndex = id.toIntOrNull() ?: -1,
                        )
                        if (selected) selectedAudio = id
                    }
                    "sub" -> {
                        subtitles += TrackInfo(
                            id = id,
                            label = title ?: language?.uppercase() ?: "Subtitle ${subtitles.size + 1}",
                            language = language,
                            mimeType = codec,
                            isExternal = external,
                            groupIndex = id.toIntOrNull() ?: -1,
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
        private const val PAUSED_POLL_INTERVAL_MS = 800L
    }
}
