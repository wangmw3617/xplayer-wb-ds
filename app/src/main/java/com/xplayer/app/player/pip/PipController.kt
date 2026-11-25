package com.xplayer.app.player.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import com.xplayer.app.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

enum class PlayerCommand { TOGGLE_PLAY, REWIND, FORWARD }

/** PiP 窗口里的 RemoteAction 通过广播回到正在运行的播放页 */
object PlayerCommandBus {
    private val _commands = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 8)
    val commands = _commands.asSharedFlow()

    fun send(command: PlayerCommand) {
        _commands.tryEmit(command)
    }
}

class PlayerCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.action?.let { action ->
            PlayerCommand.entries.firstOrNull { it.name == action }
        } ?: return
        PlayerCommandBus.send(command)
    }
}

object PipController {

    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enter(
        activity: Activity,
        videoWidth: Int,
        videoHeight: Int,
        isPlaying: Boolean,
        autoEnter: Boolean = false,
        sourceBounds: Rect? = null,
    ): Boolean {
        if (!isSupported(activity)) return false
        return runCatching {
            val params = buildParams(
                activity = activity,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                isPlaying = isPlaying,
                autoEnter = autoEnter,
                sourceBounds = sourceBounds,
            )
            activity.enterPictureInPictureMode(params)
        }.getOrDefault(false)
    }

    fun updateActions(
        activity: Activity,
        videoWidth: Int,
        videoHeight: Int,
        isPlaying: Boolean,
        autoEnter: Boolean = isPlaying,
        sourceBounds: Rect? = null,
    ) {
        if (!isSupported(activity)) return
        runCatching {
            val params = buildParams(
                activity = activity,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                isPlaying = isPlaying,
                autoEnter = autoEnter,
                sourceBounds = sourceBounds,
            )
            activity.setPictureInPictureParams(params)
        }
    }

    private fun buildParams(
        activity: Activity,
        videoWidth: Int,
        videoHeight: Int,
        isPlaying: Boolean,
        autoEnter: Boolean,
        sourceBounds: Rect?,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setActions(buildActions(activity, isPlaying))
        clampedAspectRatio(videoWidth, videoHeight)?.let(builder::setAspectRatio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            (sourceBounds ?: defaultSourceRectHint(activity))?.let(builder::setSourceRectHint)
        }
        return builder.build()
    }

    /** Android PiP 要求 Rational 严格在 [1/2.39, 2.39] 区间内，超宽片源需钳位防止崩溃 */
    private fun clampedAspectRatio(width: Int, height: Int): Rational? {
        if (width <= 0 || height <= 0) return null
        val clamped = (width.toFloat() / height.toFloat()).coerceIn(0.42f, 2.38f)
        return Rational((clamped * 1000f).roundToInt(), 1000)
    }

    private fun defaultSourceRectHint(activity: Activity): Rect? {
        val view = activity.window?.decorView ?: return null
        val rect = Rect()
        return if (view.getGlobalVisibleRect(rect) && !rect.isEmpty) rect else null
    }

    private fun buildActions(activity: Activity, isPlaying: Boolean): List<RemoteAction> {
        val playPause = remoteAction(
            activity = activity,
            command = PlayerCommand.TOGGLE_PLAY,
            iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            title = activity.getString(if (isPlaying) R.string.pip_pause else R.string.pip_play),
            requestCode = 1,
        )
        val rewind = remoteAction(
            activity = activity,
            command = PlayerCommand.REWIND,
            iconRes = android.R.drawable.ic_media_rew,
            title = activity.getString(R.string.pip_rewind_10),
            requestCode = 2,
        )
        val forward = remoteAction(
            activity = activity,
            command = PlayerCommand.FORWARD,
            iconRes = android.R.drawable.ic_media_ff,
            title = activity.getString(R.string.pip_forward_10),
            requestCode = 3,
        )
        return listOf(rewind, playPause, forward)
    }

    private fun remoteAction(
        activity: Activity,
        command: PlayerCommand,
        iconRes: Int,
        title: String,
        requestCode: Int,
    ): RemoteAction {
        val intent = Intent(activity, PlayerCommandReceiver::class.java).setAction(command.name)
        val pending = PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(activity, iconRes)
        return RemoteAction(icon, title, title, pending)
    }
}
