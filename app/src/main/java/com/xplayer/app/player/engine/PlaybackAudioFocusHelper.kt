package com.xplayer.app.player.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat

/**
 * 为 libmpv 等非 Media3 引擎提供统一音频焦点管理与耳机拔出（ACTION_AUDIO_BECOMING_NOISY）自动暂停。
 */
internal class PlaybackAudioFocusHelper(
    private val context: Context,
    private val onPauseRequired: () -> Unit,
    private val onVolumeDuckChanged: (duckMultiplier: Float) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusGranted = false
    private var noisyRegistered = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                onVolumeDuckChanged(1f)
                onPauseRequired()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onVolumeDuckChanged(0.35f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                onVolumeDuckChanged(1f)
            }
        }
    }

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPauseRequired()
            }
        }
    }

    fun acquireOnPlay(): Boolean {
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focusGranted = granted
        if (!noisyRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    noisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                noisyRegistered = true
            }
        }
        return granted
    }

    fun release() {
        if (noisyRegistered) {
            runCatching { context.unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
        if (focusGranted) {
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
            focusGranted = false
        }
    }
}
