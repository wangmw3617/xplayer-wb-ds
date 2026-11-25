package com.xplayer.app.player.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.xplayer.app.R
import com.xplayer.app.data.datastore.AspectMode
import com.xplayer.app.data.datastore.SubtitlePrefs
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaSource
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.data.source.SidecarSubtitle
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Media3 内核：优先硬解 + FFmpeg 音频扩展，支持音频焦点/拔出耳机暂停、
 * 杜比视界/HDR10 直通、多轨选择与子线程 PixelCopy 截帧。
 */
@OptIn(UnstableApi::class)
class Media3EngineImpl(
    private val context: Context,
    private val frameRateMatching: Boolean = true,
) : PlayerEngine {

    override val type: EngineType = EngineType.MEDIA3

    private val _state = MutableStateFlow(PlayerUiState(engine = EngineType.MEDIA3, supportsSubtitleDelay = false))
    override val state = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null
    private var copyThread: HandlerThread? = null
    private var copyHandler: Handler? = null

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var aspectFrame: AspectRatioFrameLayout? = null

    private var source: MediaSource? = null
    private val externalSubtitles = mutableListOf<SidecarSubtitle>()
    private var subtitleStyle = SubtitlePrefs()
    private var aspectMode = AspectMode.FIT
    private var pendingSpeed = 1f
    private var pendingVolume = 1f
    private var preciseSeek = false
    private var pendingSelectAudioId: String? = null
    private var pendingSelectSubtitleId: String? = null
    private var pendingSelectSubtitleExplicit = false
    private var pendingSelectExternalSubPath: String? = null
    private var pixelWidthHeightRatio = 1f

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val p = player ?: return
            val safeDuration = p.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: _state.value.durationMs
            _state.value = _state.value.copy(
                isBuffering = playbackState == Player.STATE_BUFFERING,
                ready = playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED,
                ended = playbackState == Player.STATE_ENDED,
                positionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = safeDuration,
                bufferedMs = p.bufferedPosition.coerceAtLeast(0L),
                isPlaying = p.isPlaying,
            )
            if (playbackState == Player.STATE_READY) {
                readTracks(p)
                applyPendingTrackSelections(p)
                applyFrameRate()
            } else if (playbackState == Player.STATE_ENDED) {
                stopPositionLoop()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) startPositionLoop() else stopPositionLoop()
        }

        override fun onTracksChanged(tracks: Tracks) {
            player?.let { p ->
                readTracks(p)
                applyPendingTrackSelections(p)
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            pixelWidthHeightRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
            _state.value = _state.value.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height,
            )
            if (aspectMode == AspectMode.ORIGINAL) setAspectRatio(AspectMode.ORIGINAL)
            applyFrameRate()
        }

        override fun onPlayerError(error: PlaybackException) {
            stopPositionLoop()
            Log.e(TAG, "playback error ${error.errorCodeName}: ${error.message}")
            _state.value = _state.value.copy(
                error = error.errorCodeName,
                isBuffering = false,
                isPlaying = false,
            )
        }
    }

    override fun createVideoView(context: Context): View {
        playerView?.player = null
        val view = PlayerView(context).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setShutterBackgroundColor(Color.TRANSPARENT)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            keepScreenOn = true
        }
        playerView = view
        val frame = AspectRatioFrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        aspectFrame = frame
        player?.let { view.player = it }
        applySubtitleStyle(subtitleStyle)
        setAspectRatio(aspectMode)
        return frame
    }

    override fun prepare(source: MediaSource, startPositionMs: Long, subtitleStyle: SubtitlePrefs, speed: Float) {
        val isSameSource = this.source?.key == source.key
        this.source = source
        this.subtitleStyle = subtitleStyle
        this.pendingSpeed = speed
        externalSubtitles.clear()
        if (source is LocalFileSource) {
            externalSubtitles += source.subtitles
        }
        if (!isSameSource) {
            pendingSelectExternalSubPath = null
        }
        applySubtitleStyle(subtitleStyle)
        startPlayback(startPositionMs, speed)
    }

    fun setPreferredTracks(audioId: String?, subtitleId: String?, subtitleExplicit: Boolean) {
        pendingSelectAudioId = audioId
        pendingSelectSubtitleId = subtitleId
        pendingSelectSubtitleExplicit = subtitleExplicit
    }

    private fun startPlayback(startPositionMs: Long, speed: Float, playWhenReady: Boolean = true) {
        val mediaSource = source ?: return
        releasePlayer()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        if (mediaSource is NetworkUrlSource && mediaSource.headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(mediaSource.headers)
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val exoPlayer = ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
            .setSeekParameters(if (preciseSeek) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                volume = pendingVolume.coerceIn(0f, 2f)
            }
        exoPlayer.addListener(listener)
        player = exoPlayer
        playerView?.player = exoPlayer
        exoPlayer.setPlaybackSpeed(speed)

        val item = buildMediaItem(mediaSource)
        exoPlayer.setMediaItem(item, startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
        _state.value = _state.value.copy(
            ready = false,
            isPlaying = false,
            isBuffering = true,
            error = null,
            ended = false,
            speed = speed,
            positionMs = startPositionMs.coerceAtLeast(0L),
            engine = EngineType.MEDIA3,
        )
        startPositionLoop()
    }

    private fun buildMediaItem(source: MediaSource): MediaItem {
        val builder = MediaItem.Builder()
        when (source) {
            is LocalFileSource -> builder.setUri(Uri.fromFile(File(source.path)))
            is ContentUriSource -> builder.setUri(Uri.parse(source.uri))
            is NetworkUrlSource -> builder.setUri(Uri.parse(source.url))
        }
        val subtitles = buildList {
            if (source is LocalFileSource) addAll(source.subtitles)
            addAll(externalSubtitles)
        }.distinctBy { it.path }
        if (subtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(
                subtitles.map { sub ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(sub.path)))
                        .setId("$EXT_SUB_PREFIX${sub.path}")
                        .setMimeType(sub.mimeType)
                        .setLanguage(languageOf(sub.name))
                        .setLabel(sub.name)
                        .setSelectionFlags(C.SELECTION_FLAG_AUTOSELECT)
                        .build()
                },
            )
        }
        return builder.build()
    }

    override fun play() {
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekTo(positionMs: Long) {
        player?.let { p ->
            val duration = p.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: _state.value.durationMs
            val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            p.seekTo(target)
            _state.value = _state.value.copy(positionMs = target, ended = false)
        }
    }

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        player?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    override fun selectAudioTrack(track: TrackInfo) {
        val p = player ?: return
        val group = p.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
    }

    override fun selectSubtitleTrack(track: TrackInfo?) {
        val p = player ?: return
        val builder = p.trackSelectionParameters.buildUpon()
        if (track == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val group = p.currentTracks.groups.getOrNull(track.groupIndex) ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
        }
        p.trackSelectionParameters = builder.build()
        _state.value = _state.value.copy(selectedSubtitleId = track?.id)
    }

    override fun addExternalSubtitle(subtitle: SidecarSubtitle) {
        externalSubtitles.removeAll { it.path == subtitle.path }
        externalSubtitles += subtitle
        val p = player
        val position = p?.currentPosition?.coerceAtLeast(0L) ?: _state.value.positionMs
        val keepPlaying = p?.playWhenReady ?: true
        pendingSelectAudioId = _state.value.selectedAudioId
        pendingSelectExternalSubPath = subtitle.path
        startPlayback(position, pendingSpeed, playWhenReady = keepPlaying)
    }

    /** Media3 暂不支持独立字幕时间轴偏移 */
    override fun setSubtitleDelay(delayMs: Long) = Unit

    override fun applySubtitleStyle(style: SubtitlePrefs) {
        subtitleStyle = style
        val subtitleView = playerView?.subtitleView ?: return
        subtitleView.setApplyEmbeddedStyles(!style.forceAssOverride)
        subtitleView.setApplyEmbeddedFontSizes(!style.forceAssOverride)
        subtitleView.setStyle(
            CaptionStyleCompat(
                style.colorArgb.toInt(),
                if (style.backgroundBar) 0x99000000.toInt() else Color.TRANSPARENT,
                Color.TRANSPARENT,
                when {
                    style.outlinePercent > 0 && !style.shadow -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                    style.shadow -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                    else -> CaptionStyleCompat.EDGE_TYPE_NONE
                },
                Color.BLACK,
                null,
            ),
        )
        subtitleView.setFractionalTextSize(0.0533f * (style.fontScalePercent / 100f))
        subtitleView.setBottomPaddingFraction((style.bottomMarginDp.coerceIn(0, 120) / 120f) * 0.25f)
    }

    override fun setAspectRatio(mode: AspectMode) {
        aspectMode = mode
        val view = playerView ?: return
        val frame = aspectFrame ?: return
        when (mode) {
            AspectMode.FIT -> {
                frame.setAspectRatio(0f)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            AspectMode.CROP -> {
                frame.setAspectRatio(0f)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            AspectMode.STRETCH -> {
                frame.setAspectRatio(0f)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            AspectMode.RATIO_16_9 -> {
                frame.setAspectRatio(16f / 9f)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            AspectMode.RATIO_4_3 -> {
                frame.setAspectRatio(4f / 3f)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            AspectMode.ORIGINAL -> {
                val w = state.value.videoWidth
                val h = state.value.videoHeight
                val ratio = if (w > 0 && h > 0) (w.toFloat() * pixelWidthHeightRatio) / h else 0f
                frame.setAspectRatio(ratio)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    }

    override fun setPreciseSeek(enabled: Boolean) {
        preciseSeek = enabled
        player?.setSeekParameters(if (enabled) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
    }

    override fun setVolume(volume: Float) {
        pendingVolume = volume.coerceIn(0f, 2f)
        player?.volume = pendingVolume
    }

    override suspend fun captureFrame(): Bitmap? {
        val surfaceView = playerView?.videoSurfaceView as? SurfaceView ?: return null
        if (!surfaceView.isAttachedToWindow || surfaceView.holder.surface?.isValid != true) return null
        val width = surfaceView.width
        val height = surfaceView.height
        if (width <= 0 || height <= 0) return null

        val handler = ensureCopyHandler()
        return suspendCancellableCoroutine { continuation ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val finished = AtomicBoolean(false)

            continuation.invokeOnCancellation {
                if (finished.compareAndSet(false, true) && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            try {
                PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { result ->
                        if (finished.compareAndSet(false, true)) {
                            if (result == PixelCopy.SUCCESS && continuation.isActive) {
                                continuation.resume(bitmap)
                            } else {
                                if (!bitmap.isRecycled) bitmap.recycle()
                                if (continuation.isActive) continuation.resume(null)
                            }
                        } else if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    },
                    handler,
                )
            } catch (_: Throwable) {
                if (finished.compareAndSet(false, true) && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    override fun release() {
        stopPositionLoop()
        releasePlayer()
        playerView = null
        aspectFrame = null
        copyThread?.quitSafely()
        copyThread = null
        copyHandler = null
        scope.cancel()
    }

    private fun ensureCopyHandler(): Handler {
        copyHandler?.let { return it }
        val thread = HandlerThread("xplayer-pixelcopy").apply { start() }
        copyThread = thread
        return Handler(thread.looper).also { copyHandler = it }
    }

    private fun releasePlayer() {
        playerView?.player = null
        player?.let { p ->
            p.removeListener(listener)
            p.stop()
            p.release()
        }
        player = null
    }

    private fun readTracks(p: Player) {
        val audio = mutableListOf<TrackInfo>()
        val subtitles = mutableListOf<TrackInfo>()
        var selectedAudio: String? = null
        var selectedSubtitle: String? = null

        p.currentTracks.groups.forEachIndexed { groupIndex, group ->
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val id = format.id ?: "$groupIndex:$index"
                val selected = group.isTrackSelected(index)
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        audio += TrackInfo(
                            id = id,
                            label = format.label ?: format.codecs
                                ?: context.getString(R.string.player_track_audio_fallback, audio.size + 1),
                            language = format.language,
                            mimeType = format.sampleMimeType,
                            channels = format.channelCount.takeIf { it > 0 },
                            groupIndex = groupIndex,
                            trackIndex = index,
                        )
                        if (selected) selectedAudio = id
                    }
                    C.TRACK_TYPE_TEXT -> {
                        val isExt = id.startsWith(EXT_SUB_PREFIX)
                        subtitles += TrackInfo(
                            id = id,
                            label = format.label
                                ?: context.getString(R.string.player_track_subtitle_fallback, subtitles.size + 1),
                            language = format.language,
                            mimeType = format.sampleMimeType,
                            isExternal = isExt,
                            groupIndex = groupIndex,
                            trackIndex = index,
                        )
                        if (selected) selectedSubtitle = id
                    }
                }
            }
        }
        _state.value = _state.value.copy(
            audioTracks = audio,
            subtitleTracks = subtitles,
            selectedAudioId = selectedAudio,
            selectedSubtitleId = selectedSubtitle,
        )
    }

    private fun applyPendingTrackSelections(p: Player) {
        val audioTargetId = pendingSelectAudioId
        if (audioTargetId != null) {
            val match = _state.value.audioTracks.firstOrNull { it.id == audioTargetId }
            pendingSelectAudioId = null
            if (match != null && _state.value.selectedAudioId != match.id) {
                selectAudioTrack(match)
            }
        }

        val extPath = pendingSelectExternalSubPath
        if (extPath != null) {
            val expectedId = "$EXT_SUB_PREFIX$extPath"
            val match = _state.value.subtitleTracks.firstOrNull { it.id == expectedId }
            pendingSelectExternalSubPath = null
            if (match != null) {
                selectSubtitleTrack(match)
                pendingSelectSubtitleId = null
                pendingSelectSubtitleExplicit = false
                return
            }
        }

        if (pendingSelectSubtitleExplicit) {
            val targetId = pendingSelectSubtitleId
            pendingSelectSubtitleId = null
            pendingSelectSubtitleExplicit = false
            if (targetId == null) {
                selectSubtitleTrack(null)
            } else {
                _state.value.subtitleTracks.firstOrNull { it.id == targetId }?.let { selectSubtitleTrack(it) }
            }
        }
    }

    private fun startPositionLoop() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                player?.let { p ->
                    val duration = p.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: _state.value.durationMs
                    _state.value = _state.value.copy(
                        positionMs = p.currentPosition.coerceAtLeast(0L),
                        durationMs = duration,
                        bufferedMs = p.bufferedPosition.coerceAtLeast(0L),
                        isPlaying = p.isPlaying,
                    )
                }
                delay(POSITION_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionLoop() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun applyFrameRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val surfaceView = playerView?.videoSurfaceView as? SurfaceView ?: return
        val surface = surfaceView.holder.surface
        if (surface == null || !surface.isValid) return
        val fps = if (frameRateMatching) detectFrameRate() else 0f
        runCatching {
            surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }

    private fun detectFrameRate(): Float {
        val p = player ?: return 0f
        val format = p.videoFormat ?: return 0f
        return if (format.frameRate > 0f) format.frameRate else 0f
    }

    private fun languageOf(name: String): String {
        val parts = name.substringBeforeLast('.').split('.', '_', '-', ' ')
        return parts.lastOrNull { it.length in 2..3 }?.lowercase(Locale.US) ?: "und"
    }

    companion object {
        private const val TAG = "Media3Engine"
        private const val POSITION_INTERVAL_MS = 400L
        private const val USER_AGENT = "XPlayer/1.0 (Android)"
        private const val EXT_SUB_PREFIX = "xplayer_ext:"
    }
}
