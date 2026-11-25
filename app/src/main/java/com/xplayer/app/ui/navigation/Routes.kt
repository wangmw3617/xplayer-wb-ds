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

    const val MEDIA_INFO = "media/info?type={type}&key={key}&title={title}"

    val mainTabs = listOf(HOME, VIDEOS, FOLDERS, SETTINGS)

    fun player(
        sourceType: String,
        key: String,
        title: String,
        startMs: Long = 0L,
        hasEmbeddedAss: Boolean = false,
    ): String {
        val safeTitle = sanitizeArg(title)
        return "player?type=${Uri.encode(sourceType)}&key=${Uri.encode(key)}&title=${Uri.encode(safeTitle)}" +
            "&start=$startMs&ass=$hasEmbeddedAss"
    }

    fun mediaInfo(sourceType: String, key: String, title: String): String =
        "media/info?type=${Uri.encode(sourceType)}&key=${Uri.encode(key)}&title=${Uri.encode(sanitizeArg(title))}"

    // NavDeepLink Regex 校验默认禁止换行，提前替换防止特殊文件名崩溃
    private fun sanitizeArg(raw: String): String = raw.replace('\n', ' ').replace('\r', ' ')
}
