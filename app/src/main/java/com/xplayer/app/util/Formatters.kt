package com.xplayer.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {

    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun duration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun durationClock(ms: Long): String = duration(ms)

    fun size(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) {
            "${value.toInt()} ${units[index]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[index])
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
        if (timeMillis <= 0) return "-"
        return Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)
    }

    fun relativeTime(timeMillis: Long): String {
        if (timeMillis <= 0) return ""
        val diff = System.currentTimeMillis() - timeMillis
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
            diff < TimeUnit.DAYS.toMillis(30) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d"
            else -> dateTime(timeMillis).substringBefore(' ')
        }
    }

    /** 4K / 1080P 等分辨率简写 */
    fun resolutionLabel(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return ""
        val shortSide = minOf(width, height)
        return when {
            shortSide >= 2160 -> "4K"
            shortSide >= 1440 -> "2K"
            shortSide >= 1080 -> "1080P"
            shortSide >= 720 -> "720P"
            shortSide >= 480 -> "480P"
            else -> "${width}×${height}"
        }
    }

    fun videoCodecLabel(mimeOrCodec: String?): String? {
        val value = mimeOrCodec?.lowercase(Locale.US) ?: return null
        return when {
            value.contains("hevc") || value.contains("h265") || value.contains("h.265") -> "HEVC"
            value.contains("avc") || value.contains("h264") || value.contains("h.264") -> "H.264"
            value.contains("av01") || value.contains("av1") -> "AV1"
            value.contains("vp9") || value.contains("vp09") -> "VP9"
            value.contains("vp8") -> "VP8"
            value.contains("mpeg4") -> "MPEG-4"
            value.contains("dolby-vision") || value.contains("dvhe") || value.contains("dvh1") -> "DV"
            else -> null
        }
    }
}
