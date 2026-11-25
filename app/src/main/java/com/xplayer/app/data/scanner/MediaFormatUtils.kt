package com.xplayer.app.data.scanner

import android.media.MediaFormat
import com.xplayer.app.data.local.HdrType
import java.util.Locale

/**
 * MetadataExtractor / TrackInspector / MediaInfoProbe 共用的 MediaFormat 解析工具，
 * 统一 HDR、ASS 内封字幕与安全字段读取逻辑。
 */
internal object MediaFormatUtils {

    val ASS_MIMES = setOf(
        "text/x-ssa",
        "text/x-ass",
        "text/ssa",
        "text/ass",
        "application/x-ass",
        "application/x-ssa",
    )

    private val SUBTITLE_MIMES = ASS_MIMES + setOf(
        "application/x-subrip",
        "text/vtt",
        "text/x-microdvd",
        "application/ttml+xml",
        "application/pgs",
        "application/dvbsubs",
        "application/vobsub",
        "application/cea-608",
        "application/cea-708",
    )

    fun isAssMime(mime: String): Boolean = mime.lowercase(Locale.US) in ASS_MIMES

    fun isSubtitleMime(mime: String): Boolean =
        mime.startsWith("text/", ignoreCase = true) || mime.lowercase(Locale.US) in SUBTITLE_MIMES

    fun isDolbyVisionMime(mime: String): Boolean =
        mime.contains("dolby-vision", ignoreCase = true) ||
            mime.contains("dvhe", ignoreCase = true) ||
            mime.contains("dvh1", ignoreCase = true) ||
            mime.contains("dav1", ignoreCase = true)

    fun detectHdrType(format: MediaFormat, mime: String): HdrType {
        val transfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
        return when {
            isDolbyVisionMime(mime) -> HdrType.DOLBY_VISION
            format.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO) -> HdrType.HDR10_PLUS
            transfer == MediaFormat.COLOR_TRANSFER_ST2084 -> HdrType.HDR10
            transfer == MediaFormat.COLOR_TRANSFER_HLG -> HdrType.HLG
            else -> HdrType.NONE
        }
    }

    fun trackTitle(format: MediaFormat): String? =
        (format.stringOrNull("title") ?: format.stringOrNull("track-name"))?.takeIf { it.isNotBlank() }

    fun trackLanguage(format: MediaFormat): String? =
        format.stringOrNull(MediaFormat.KEY_LANGUAGE)?.takeIf { it.isNotBlank() && it != "und" }

    fun trackIsDefault(format: MediaFormat): Boolean =
        format.intOrNull(MediaFormat.KEY_IS_DEFAULT) == 1

    fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    fun MediaFormat.longOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null

    fun MediaFormat.numberOrNull(key: String): Number? =
        if (containsKey(key)) runCatching { getNumber(key) }.getOrNull() else null

    fun MediaFormat.stringOrNull(key: String): String? =
        if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null
}
