package com.xplayer.app.player.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.xplayer.app.MainActivity
import com.xplayer.app.data.repository.StreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BackgroundAudioState(
    val uri: String,
    val positionMs: Long,
    val isPlaying: Boolean,
)

/**
 * 后台纯音频播放：启用 FFmpeg 音频扩展与自定义 HTTP 请求头，并持续同步位置供返回前台无缝接续。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var syncJob: Job? = null
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var currentUri: String? = null
    private var currentHeadersJson: String? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            publishLiveState()
            if (playbackState == Player.STATE_ENDED) {
                stopForegroundService()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishLiveState()
        }

        override fun onPlayerError(error: PlaybackException) {
            stopForegroundService()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = buildExoPlayer(emptyMap())
        player = exoPlayer
        session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(launchIntent())
            .build()
        syncJob = scope.launch {
            while (isActive) {
                publishLiveState()
                delay(500L)
            }
        }
    }

    private fun buildExoPlayer(headers: Map<String, String>): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        if (headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory))
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(this, renderersFactory, mediaSourceFactory).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            addListener(listener)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ret = super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                if (!startAudio(intent)) {
                    stopForegroundService()
                }
            }
            ACTION_STOP -> stopForegroundService()
        }
        return ret
    }

    private fun startAudio(intent: Intent): Boolean {
        val uri = intent.getStringExtra(EXTRA_URI) ?: return false
        val position = intent.getLongExtra(EXTRA_POSITION, 0L).coerceAtLeast(0L)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val speed = intent.getFloatExtra(EXTRA_SPEED, 1f).coerceIn(0.25f, 4.0f)
        val headersJson = intent.getStringExtra(EXTRA_HEADERS)

        if (headersJson != currentHeadersJson) {
            currentHeadersJson = headersJson
            val headers = StreamRepository.parseHeaders(headersJson)
            val oldPlayer = player
            val newPlayer = buildExoPlayer(headers)
            player = newPlayer
            session?.player = newPlayer
            oldPlayer?.let {
                it.removeListener(listener)
                it.stop()
                it.release()
            }
        }

        currentUri = uri
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build(),
            )
            .build()
        player?.let { exo ->
            exo.setPlaybackSpeed(speed)
            exo.setMediaItem(item, position)
            exo.prepare()
            exo.playWhenReady = true
        }
        publishLiveState()
        return true
    }

    private fun publishLiveState() {
        val uri = currentUri ?: return
        val exo = player ?: return
        _liveState.value = BackgroundAudioState(
            uri = uri,
            positionMs = exo.currentPosition.coerceAtLeast(0L),
            isPlaying = exo.isPlaying,
        )
    }

    private fun stopForegroundService() {
        publishLiveState()
        player?.stop()
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val exo = player
        if (exo == null || !exo.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        syncJob?.cancel()
        syncJob = null
        scope.cancel()
        session?.release()
        session = null
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        super.onDestroy()
    }

    private fun launchIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_START = "com.xplayer.app.action.START_AUDIO"
        const val ACTION_STOP = "com.xplayer.app.action.STOP_AUDIO"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_HEADERS = "extra_headers"
        private const val USER_AGENT = "XPlayer/1.0 (Android)"

        private val _liveState = MutableStateFlow<BackgroundAudioState?>(null)
        val liveState: StateFlow<BackgroundAudioState?> = _liveState.asStateFlow()

        fun clearLiveState() {
            _liveState.value = null
        }

        fun startIntent(
            context: Context,
            uri: String,
            title: String?,
            positionMs: Long,
            speed: Float = 1f,
            headersJson: String? = null,
        ): Intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_URI, uri)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_POSITION, positionMs)
            putExtra(EXTRA_SPEED, speed)
            if (!headersJson.isNullOrBlank()) putExtra(EXTRA_HEADERS, headersJson)
        }
    }
}
