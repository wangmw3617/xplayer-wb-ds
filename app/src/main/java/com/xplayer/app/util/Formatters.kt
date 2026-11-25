package com.xplayer.app.util

import com.xplayer.app.data.local.HdrType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    fun duration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = ms / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return buildString(8) {
            if (hours > 0L) {
                append(hours)
                append(':')
            }
            if (minutes < 10L) append('0')
            append(minutes)
            append(':')
            if (seconds < 10L) append('0')
            append(seconds)
        }
    }

    fun size(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < SIZE_UNITS.lastIndex) {
            value /= 1024.0
            index++
        }
        return if (index == 0) {
            "${value.toInt()} ${SIZE_UNITS[0]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, SIZE_UNITS[index])
        }
    }

    fun speed(value: Float): String {
        val rounded = Math.round(value * 100f) / 100f
        return if (rounded % 1f == 0f) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    fun dateTime(timeMillis: Long): String {
        if (timeMillis <= 0L) return "-"
        return Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault())
            .format(DATE_TIME_FORMATTER)
    }

    /** 4K / 1080P 等分辨率简写（同时兼容超宽银幕与竖屏视频） */
    fun resolutionLabel(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return ""
        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height)
        return when {
            longSide >= 3840 || shortSide >= 2160 -> "4K"
            longSide >= 2560 || shortSide >= 1440 -> "2K"
            longSide >= 1920 || shortSide >= 1080 -> "1080P"
            longSide >= 1280 || shortSide >= 720 -> "720P"
            longSide >= 854 || shortSide >= 480 -> "480P"
            else -> "${width}×${height}"
        }
    }

    fun sampleRateLabel(hz: Int): String {
        if (hz <= 0) return ""
        return if (hz % 1000 == 0) {
            "${hz / 1000} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", hz / 1000.0).trimEnd('0').trimEnd('.')
        }
    }

    fun hdrShortBadge(hdr: HdrType): String = when (hdr) {
        HdrType.NONE -> ""
        HdrType.DOLBY_VISION -> "DV"
        HdrType.HDR10_PLUS -> "HDR10+"
        HdrType.HDR10 -> "HDR10"
        HdrType.HLG -> "HLG"
    }

    /** 统一视频/音频/字幕编码显示名称；compact=true 时 Dolby Vision 简写为 DV */
    fun codecLabel(mimeOrCodec: String?, compact: Boolean = false): String? {
        val raw = mimeOrCodec ?: return null
        fun has(token: String) = raw.contains(token, ignoreCase = true)
        return when {
            has("dolby-vision") || has("dvhe") || has("dvh1") || has("dav1") ->
                if (compact) "DV" else "Dolby Vision"
            has("hevc") || has("h265") || has("h.265") -> "HEVC"
            has("avc") || has("h264") || has("h.264") -> "H.264"
            has("av01") || has("av1") -> "AV1"
            has("vp9") || has("vp09") -> "VP9"
            has("vp8") -> "VP8"
            has("mpeg4") -> "MPEG-4"
            has("mpeg2") -> "MPEG-2"
            has("vc-1") || has("vc1") -> "VC-1"
            has("avs") -> "AVS"
            has("mjpeg") -> "Motion JPEG"
            has("eac3") || has("e-ac-3") -> "E-AC-3"
            has("ac3") || has("ac-3") -> "AC-3"
            has("dts") -> "DTS"
            has("truehd") -> "TrueHD"
            has("mlp") -> "MLP"
            has("flac") -> "FLAC"
            has("alac") -> "ALAC"
            has("opus") -> "Opus"
            has("vorbis") -> "Vorbis"
            has("mp3") || (has("mpeg") && raw.startsWith("audio/", ignoreCase = true)) -> "MP3"
            has("aac") || has("mp4a") -> "AAC"
            has("amr") -> "AMR"
            has("pcm") || has("raw") -> "PCM"
            has("wma") -> "WMA"
            has("ape") -> "APE"
            has("subrip") || has("srt") -> "SubRip"
            has("ssa") || has("ass") -> "ASS"
            has("webvtt") || has("vtt") -> "WebVTT"
            has("pgs") || has("hdmv") -> "PGS"
            has("dvd") && raw.startsWith("text/", ignoreCase = true) -> "DVD Subtitle"
            raw.startsWith("text/", ignoreCase = true) -> raw.substringAfter('/').uppercase(Locale.US)
            else -> null
        }
    }

    fun videoCodecLabel(mimeOrCodec: String?): String? = codecLabel(mimeOrCodec, compact = true)
}
