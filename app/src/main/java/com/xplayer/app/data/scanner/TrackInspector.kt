package com.xplayer.app.data.scanner

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.xplayer.app.data.scanner.MediaFormatUtils.intOrNull
import com.xplayer.app.data.scanner.MediaFormatUtils.stringOrNull
import com.xplayer.app.util.Formatters
import java.util.Locale

data class TrackDescription(
    val type: String,
    val mime: String?,
    val language: String?,
    val channels: Int?,
    val sampleRate: Int?,
    val title: String?,
)

/** 视频详情弹窗用：读取容器内所有音轨 / 字幕轨的参数 */
object TrackInspector {

    fun inspect(path: String): List<TrackDescription> {
        val extractor = MediaExtractor()
        val result = mutableListOf<TrackDescription>()
        try {
            extractor.setDataSource(path)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.stringOrNull(MediaFormat.KEY_MIME) ?: continue
                val type = when {
                    mime.startsWith("audio/", ignoreCase = true) -> "audio"
                    MediaFormatUtils.isSubtitleMime(mime) -> "text"
                    else -> continue
                }
                result += TrackDescription(
                    type = type,
                    mime = mime,
                    language = MediaFormatUtils.trackLanguage(format),
                    channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.takeIf { it > 0 },
                    sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it > 0 },
                    title = MediaFormatUtils.trackTitle(format),
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
        track.title?.let { parts += it }
        track.language?.let { parts += it.uppercase(Locale.US) }
        track.channels?.let { channels ->
            parts += when (channels) {
                1 -> "1.0"
                2 -> "2.0"
                6 -> "5.1"
                8 -> "7.1"
                else -> "${channels}ch"
            }
        }
        track.sampleRate?.let { parts += Formatters.sampleRateLabel(it) }
        Formatters.codecLabel(track.mime)?.let { parts += it }
        return parts.joinToString(" · ")
    }
}
