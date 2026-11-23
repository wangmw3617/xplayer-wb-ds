package com.xplayer.app.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val VIDEOS = "videos"
    const val FOLDERS = "folders"
    const val SETTINGS = "settings"

    const val APPEARANCE = "settings/appearance"
    const val ENGINE = "settings/engine"
    const val GESTURE = "settings/gesture"
    const val SUBTITLE = "settings/subtitle"
    const val PLAYBACK = "settings/playback"
    const val MPV_LAB = "settings/mpv"
    const val CODEC = "settings/codec"
    const val HIDDEN_FOLDERS = "settings/hidden"
    const val ABOUT = "settings/about"

    const val PLAYER = "player?type={type}&key={key}&title={title}&start={start}&ass={ass}"

    val mainTabs = listOf(HOME, VIDEOS, FOLDERS, SETTINGS)

    fun player(
        sourceType: String,
        key: String,
        title: String,
        startMs: Long = 0L,
        hasEmbeddedAss: Boolean = false,
    ): String = "player?type=$sourceType&key=${Uri.encode(key)}&title=${Uri.encode(title)}" +
        "&start=$startMs&ass=$hasEmbeddedAss"
}
