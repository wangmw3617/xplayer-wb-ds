package com.xplayer.app.data.scanner

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.util.Locale

data class TrackDescription(
    val type: String,
    val mime: String?,
    val language: String?,
    val channels: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val title: String?,
    val isDefault: Boolean,
)

/** 视频信息弹窗用：读取容器内所有音轨 / 字幕轨的参数 */
object TrackInspector {

    fun inspect(path: String): List<TrackDescription> {
        val extractor = MediaExtractor()
        val result = mutableListOf<TrackDescription>()
        try {
            extractor.setDataSource(path)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                val type = when {
                    mime.startsWith("video/") -> "video"
                    mime.startsWith("audio/") -> "audio"
                    else -> "text"
                }
                if (type == "video") continue
                result += TrackDescription(
                    type = type,
                    mime = mime,
                    language = format.getString(MediaFormat.KEY_LANGUAGE)?.takeIf { it != "und" },
                    channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrNull(),
                    sampleRate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull(),
                    bitrate = runCatching { format.getInteger(MediaFormat.KEY_BIT_RATE) }.getOrNull(),
                    title = runCatching { format.getString("title") }.getOrNull()
                        ?: runCatching { format.getString("track-name") }.getOrNull(),
                    isDefault = runCatching {
                        format.getInteger(MediaFormat.KEY_IS_DEFAULT) == 1
                    }.getOrDefault(false),
                )
            }
        } catch (error: Throwable) {
            Log.w("TrackInspector", "inspect failed: ${error.message}")
        } finally {
            runCatching { extractor.release() }
        }
        return result
    }

    fun describe(track: TrackDescription): String {
        val parts = mutableListOf<String>()
        track.language?.let { parts += it.uppercase(Locale.US) }
        track.channels?.let { channels ->
            parts += when (channels) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> "$channels ch"
            }
        }
        track.sampleRate?.let { parts += "${it / 1000} kHz" }
        track.mime?.substringAfter('/')?.let { parts += it.uppercase(Locale.US) }
        return parts.joinToString(" · ")
    }
}
