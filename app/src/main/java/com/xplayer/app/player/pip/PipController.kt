package com.xplayer.app.player.pip

import android.app.Activity
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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

    fun enter(activity: Activity, videoWidth: Int, videoHeight: Int, isPlaying: Boolean): Boolean {
        if (!isSupported(activity)) return false
        return runCatching {
            val builder = PictureInPictureParams.Builder()
            if (videoWidth > 0 && videoHeight > 0) {
                builder.setAspectRatio(Rational(videoWidth, videoHeight))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
                sourceRectHint(activity)?.let(builder::setSourceRectHint)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setActions(buildActions(activity, isPlaying))
            }
            activity.enterPictureInPictureMode(builder.build())
        }.getOrDefault(false)
    }

    fun updateActions(
        activity: Activity,
        videoWidth: Int,
        videoHeight: Int,
        isPlaying: Boolean,
        autoEnter: Boolean = isPlaying,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val builder = PictureInPictureParams.Builder()
            if (videoWidth > 0 && videoHeight > 0) {
                builder.setAspectRatio(Rational(videoWidth, videoHeight))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(autoEnter)
                sourceRectHint(activity)?.let(builder::setSourceRectHint)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setActions(buildActions(activity, isPlaying))
            }
            activity.setPictureInPictureParams(builder.build())
        }
    }

    /** 让系统做进入动画时知道从哪块区域放大，避免全屏突变 */
    private fun sourceRectHint(activity: Activity): Rect? {
        val view = activity.window?.decorView ?: return null
        val rect = Rect()
        return if (view.getGlobalVisibleRect(rect) && !rect.isEmpty) rect else null
    }

    private fun buildActions(activity: Activity, isPlaying: Boolean): List<RemoteAction> {
        val playPause = remoteAction(
            activity = activity,
            command = PlayerCommand.TOGGLE_PLAY,
            iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            title = if (isPlaying) "Pause" else "Play",
            requestCode = 1,
        )
        val rewind = remoteAction(
            activity = activity,
            command = PlayerCommand.REWIND,
            iconRes = android.R.drawable.ic_media_rew,
            title = "-10s",
            requestCode = 2,
        )
        val forward = remoteAction(
            activity = activity,
            command = PlayerCommand.FORWARD,
            iconRes = android.R.drawable.ic_media_ff,
            title = "+10s",
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
        val pending = android.app.PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(activity, iconRes)
        return RemoteAction(icon, title, title, pending)
    }
}
