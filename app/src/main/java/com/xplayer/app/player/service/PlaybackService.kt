package com.xplayer.app.player.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.xplayer.app.MainActivity

/**
 * 后台纯音频播放：MediaSessionService 负责通知栏媒体卡片、
 * 蓝牙耳机按键与音频焦点，视频画面在后台时自动停止渲染。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
        }
        player = exoPlayer
        session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(launchIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAudio(intent)
            ACTION_STOP -> {
                player?.stop()
                stopSelf()
            }
            ACTION_TOGGLE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
            }
        }
        // MediaSessionService 依赖父类完成通知栏 / 前台服务登记
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startAudio(intent: Intent) {
        val uri = intent.getStringExtra(EXTRA_URI) ?: return
        val position = intent.getLongExtra(EXTRA_POSITION, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build(),
            )
            .build()
        player?.let { exo ->
            exo.setMediaItem(item, position.coerceAtLeast(0L))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val exo = player
        if (exo == null || !exo.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun launchIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_START = "com.xplayer.app.action.START_AUDIO"
        const val ACTION_STOP = "com.xplayer.app.action.STOP_AUDIO"
        const val ACTION_TOGGLE = "com.xplayer.app.action.TOGGLE_AUDIO"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_POSITION = "extra_position"

        fun startIntent(
            context: android.content.Context,
            uri: String,
            title: String?,
            positionMs: Long,
        ): Intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_URI, uri)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_POSITION, positionMs)
        }

        fun stopIntent(context: android.content.Context): Intent =
            Intent(context, PlaybackService::class.java).apply { action = ACTION_STOP }

        fun toggleIntent(context: android.content.Context): Intent =
            Intent(context, PlaybackService::class.java).apply { action = ACTION_TOGGLE }

        @Suppress("unused")
        private val EMPTY_EXTRAS = Bundle.EMPTY
    }
}
