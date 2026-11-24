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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    private var videoView: BaseMPVView? = null
    private var mpv: MPV? = null
    private var initialized = false
    private var subtitleStyle = SubtitlePrefs()

    override fun createVideoView(context: Context): View {
        val view = BaseMPVView(context, null)
        videoView = view
        return view
    }

    override fun prepare(source: MediaSource, startPositionMs: Long, subtitleStyle: SubtitlePrefs, speed: Float) {
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
            view.playFile(target)
            if (startPositionMs > 0) {
                mpv?.command("seek", (startPositionMs / 1000.0).toString(), "absolute+exact")
            }
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
            if (mpvConf.isNotBlank()) {
                File(configDir, "mpv.conf").writeText(mpvConf)
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
        runCatching { mpv?.setPropertyBoolean("pause", false) }
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun pause() {
        runCatching { mpv?.setPropertyBoolean("pause", true) }
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun seekTo(positionMs: Long) {
        runCatching { mpv?.command("seek", (positionMs / 1000.0).toString(), "absolute+exact") }
        _state.value = _state.value.copy(positionMs = positionMs, ended = false)
    }

    override fun setSpeed(speed: Float) {
        runCatching { mpv?.setPropertyDouble("speed", speed.toDouble()) }
        _state.value = _state.value.copy(speed = speed)
    }

    override fun selectAudioTrack(track: TrackInfo) {
        runCatching { mpv?.setPropertyString("aid", track.id) }
        _state.value = _state.value.copy(selectedAudioId = track.id)
    }

    override fun selectSubtitleTrack(track: TrackInfo?) {
        runCatching { mpv?.setPropertyString("sid", track?.id ?: "no") }
        _state.value = _state.value.copy(selectedSubtitleId = track?.id)
    }

    override fun addExternalSubtitle(subtitle: SidecarSubtitle) {
        runCatching {
            mpv?.command("sub-add", subtitle.path, "select", subtitle.name)
        }.onFailure { Log.w(TAG, "sub-add failed: ${it.message}") }
        _state.value = _state.value.copy(subtitleDelayMs = _state.value.subtitleDelayMs)
    }

    override fun setSubtitleDelay(delayMs: Long) {
        runCatching { mpv?.setPropertyDouble("sub-delay", delayMs / 1000.0) }
        _state.value = _state.value.copy(subtitleDelayMs = delayMs)
    }

    override fun applySubtitleStyle(style: SubtitlePrefs) {
        subtitleStyle = style
        val mpv = mpv ?: return
        val scale = style.fontScalePercent / 100f
        val alpha = ((style.colorArgb ushr 24) and 0xFF).toInt()
        val rgb = (style.colorArgb and 0xFFFFFF).toInt()
        val color = String.format("#%02X%02X%02X", (rgb ushr 16) and 0xFF, (rgb ushr 8) and 0xFF, rgb and 0xFF)
        runCatching {
            mpv.setPropertyString("sub-font-size", (52 * scale).toInt().toString())
            mpv.setPropertyString("sub-color", color)
            mpv.setPropertyString("sub-border-size", (2.4f * style.outlinePercent / 100f).toString())
            mpv.setPropertyString("sub-shadow-offset", if (style.shadow) "1.6" else "0")
            mpv.setPropertyString("sub-back-color", if (style.backgroundBar) "#A0000000" else "#00000000")
            mpv.setPropertyString("sub-margin-y", style.bottomMarginDp.toString())
            mpv.setPropertyString("sub-ass-override", if (style.forceAssOverride) "force" else "scale")
            mpv.setPropertyBoolean("sub-ass-vsfilter-blur-compat", alpha > 0)
        }.onFailure { Log.w(TAG, "applySubtitleStyle: ${it.message}") }
    }

    override fun setAspectRatio(mode: AspectMode) {
        val mpv = mpv ?: return
        runCatching {
            when (mode) {
                AspectMode.FIT -> {
                    mpv.setPropertyString("video-aspect-override", "no")
                    mpv.setPropertyBoolean("keepaspect", true)
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectMode.CROP -> {
                    mpv.setPropertyBoolean("keepaspect", true)
                    mpv.setPropertyDouble("panscan", 1.0)
                }
                AspectMode.STRETCH -> {
                    mpv.setPropertyBoolean("keepaspect", false)
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectMode.RATIO_16_9 -> mpv.setPropertyString("video-aspect-override", "16:9")
                AspectMode.RATIO_4_3 -> mpv.setPropertyString("video-aspect-override", "4:3")
                AspectMode.ORIGINAL -> {
                    mpv.setPropertyString("video-aspect-override", "no")
                    mpv.setPropertyBoolean("keepaspect", true)
                    mpv.setPropertyDouble("panscan", 0.0)
                    mpv.setPropertyDouble("video-zoom", 0.0)
                }
            }
        }.onFailure { Log.w(TAG, "setAspectRatio: ${it.message}") }
    }

    override fun setPreciseSeek(enabled: Boolean) {
        // mpv 默认使用精确 seek，无需切换
    }

    override fun setVolume(volume: Float) {
        runCatching { mpv?.setPropertyDouble("volume", (volume * 100f).coerceIn(0f, 200f).toDouble()) }
    }

    override suspend fun captureFrame(): Bitmap? = runCatching { mpv?.grabThumbnail(0) }.getOrNull()

    override fun release() {
        pollJob?.cancel()
        pollJob = null
        runCatching { videoView?.destroy() }
        videoView = null
        mpv = null
        initialized = false
        scope.cancel()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                runCatching { refreshState() }
                delay(POLL_INTERVAL_MS)
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
        val (audio, subtitles, selectedAudio, selectedSubtitle) = readTracks(mpv)

        _state.value = _state.value.copy(
            ready = duration > 0,
            isPlaying = !paused && !eof,
            isBuffering = duration <= 0.0,
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
    }
}
