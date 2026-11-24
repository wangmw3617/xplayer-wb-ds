package com.xplayer.app.data.media

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.xplayer.app.data.local.HdrType
import com.xplayer.app.data.scanner.SubtitleFinder
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaSource
import com.xplayer.app.data.source.MediaUriResolver
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.data.source.SidecarSubtitle
import java.io.File
import java.util.Locale

enum class MediaTrackKind { VIDEO, AUDIO, SUBTITLE }

/** 单条轨道（视频/音频/字幕）的完整参数 */
data class MediaTrackInfo(
    val index: Int,
    val kind: MediaTrackKind,
    val mime: String?,
    val codec: String?,
    val codecString: String?,
    val profile: String?,
    val level: String?,
    val title: String?,
    val language: String?,
    val isDefault: Boolean,
    val bitrate: Long?,
    // 视频
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val rotation: Int? = null,
    val hdr: HdrType = HdrType.NONE,
    val colorStandard: String? = null,
    val colorTransfer: String? = null,
    val colorRange: String? = null,
    // 音频
    val channels: Int? = null,
    val sampleRate: Int? = null,
)

/** 一个媒体文件的整体信息 */
data class MediaInfo(
    val displayName: String,
    val location: String,
    val isContentUri: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val container: String?,
    val durationMs: Long,
    val overallBitrate: Long,
    val tracks: List<MediaTrackInfo>,
    val sidecarSubtitles: List<SidecarSubtitle>,
) {
    val videoTracks: List<MediaTrackInfo> get() = tracks.filter { it.kind == MediaTrackKind.VIDEO }
    val audioTracks: List<MediaTrackInfo> get() = tracks.filter { it.kind == MediaTrackKind.AUDIO }
    val subtitleTracks: List<MediaTrackInfo> get() = tracks.filter { it.kind == MediaTrackKind.SUBTITLE }

    val hdr: HdrType get() = videoTracks.maxByOrNull { hdrRank(it.hdr) }?.hdr ?: HdrType.NONE
}

private fun hdrRank(hdr: HdrType): Int = when (hdr) {
    HdrType.NONE -> 0
    HdrType.HLG -> 1
    HdrType.HDR10 -> 2
    HdrType.HDR10_PLUS -> 3
    HdrType.DOLBY_VISION -> 4
}

/**
 * 媒体信息分析：用 MediaExtractor 逐轨读取真实参数（不是从文件名猜），
 * 支持本地文件与外部 content:// 来源，纯音频文件同样适用。
 */
object MediaInfoProbe {

    private const val TAG = "MediaInfoProbe"

    fun probe(context: Context, source: MediaSource): MediaInfo? {
        val file = (source as? LocalFileSource)?.let { File(it.path) }
        // 本地文件读不到直接判失败，避免页面显示一堆 0
        if (file != null && (!file.isFile || !file.canRead())) return null
        val size = when {
            file != null -> file.length()
            source is ContentUriSource -> MediaUriResolver.sizeOf(context, Uri.parse(source.uri))
            else -> -1L
        }
        val modified = file?.lastModified()
            ?: (source as? ContentUriSource)?.let {
                MediaUriResolver.modifiedOf(context, Uri.parse(it.uri))
            }
            ?: 0L
        val sidecars = when (source) {
            is LocalFileSource -> source.subtitles.ifEmpty { SubtitleFinder.find(source.path) }
            else -> emptyList()
        }

        val tracks = mutableListOf<MediaTrackInfo>()
        var durationUs = 0L
        var containerMime: String? = null

        runCatching {
            val handle = openHandle(context, source) ?: return@runCatching
            handle.use {
                val extractor = it.extractor
                repeat(extractor.trackCount) { index ->
                    val format = extractor.getTrackFormat(index)
                    val mime = format.string(MediaFormat.KEY_MIME) ?: return@repeat
                    if (containerMime == null) containerMime = mime
                    durationUs = maxOf(durationUs, format.long(MediaFormat.KEY_DURATION) ?: 0L)
                    val kind = when {
                        mime.startsWith("video/") -> MediaTrackKind.VIDEO
                        mime.startsWith("audio/") -> MediaTrackKind.AUDIO
                        else -> MediaTrackKind.SUBTITLE
                    }
                    tracks += readTrack(index, kind, mime, format)
                }
            }
        }.onFailure { Log.w(TAG, "probe failed for ${source.key}: ${it.message}") }

        val durationMs = durationUs / 1000
        val overallBitrate = when {
            durationMs > 0 && size > 0 -> size * 8_000 / durationMs
            else -> tracks.sumOf { it.bitrate ?: 0L }
        }

        return MediaInfo(
            displayName = source.displayName,
            location = source.key,
            isContentUri = source is ContentUriSource,
            sizeBytes = size,
            modifiedAt = modified,
            container = containerLabel(source, containerMime),
            durationMs = durationMs,
            overallBitrate = overallBitrate,
            tracks = tracks,
            sidecarSubtitles = sidecars,
        )
    }

    private class ExtractHandle(
        val extractor: MediaExtractor,
        private val resource: java.io.Closeable?,
    ) : java.io.Closeable {
        override fun close() {
            runCatching { extractor.release() }
            runCatching { resource?.close() }
        }
    }

    private fun openHandle(context: Context, source: MediaSource): ExtractHandle? = when (source) {
        is LocalFileSource -> runCatching {
            ExtractHandle(MediaExtractor().apply { setDataSource(source.path) }, null)
        }.getOrNull()

        is ContentUriSource -> openUriHandle(context, Uri.parse(source.uri))

        // 网络流的轨道参数必须拉流才知道，这里只给基础信息，避免信息页卡住
        is NetworkUrlSource -> null
    }

    /**
     * 外部「打开方式」传来的 content:// 由各家 provider 实现，兼容性差异很大，
     * 依次尝试三条路：ContentResolver 直接打开 → AssetFileDescriptor（带 offset/length）
     * → 裸 fd（长度未知的网盘 provider）。全失败才判定读不到参数。
     */
    private fun openUriHandle(context: Context, uri: Uri): ExtractHandle? {
        runCatching {
            MediaExtractor().apply { setDataSource(context, uri, null) }
        }.getOrNull()?.let { return ExtractHandle(it, null) }

        val descriptor = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")
        }.getOrNull()
        if (descriptor != null) {
            val extractor = runCatching {
                MediaExtractor().apply {
                    if (descriptor.length >= 0) {
                        setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    } else {
                        setDataSource(descriptor.fileDescriptor)
                    }
                }
            }.recoverCatching {
                MediaExtractor().apply { setDataSource(descriptor.fileDescriptor) }
            }.getOrNull()
            if (extractor != null) return ExtractHandle(extractor, descriptor)
            runCatching { descriptor.close() }
        }

        val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (pfd != null) {
            val extractor = runCatching {
                MediaExtractor().apply { setDataSource(pfd.fileDescriptor) }
            }.getOrNull()
            if (extractor != null) return ExtractHandle(extractor, pfd)
            runCatching { pfd.close() }
        }
        return null
    }

    private fun readTrack(
        index: Int,
        kind: MediaTrackKind,
        mime: String,
        format: MediaFormat,
    ): MediaTrackInfo {
        val profileValue = format.int(MediaFormat.KEY_PROFILE)
        val levelValue = format.int(MediaFormat.KEY_LEVEL)
        val transfer = format.int(MediaFormat.KEY_COLOR_TRANSFER)
        val standard = format.int(MediaFormat.KEY_COLOR_STANDARD)
        val hdr = when {
            mime.isDolbyVision() -> HdrType.DOLBY_VISION
            format.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO) -> HdrType.HDR10_PLUS
            transfer == MediaFormat.COLOR_TRANSFER_ST2084 -> HdrType.HDR10
            transfer == MediaFormat.COLOR_TRANSFER_HLG -> HdrType.HLG
            standard == MediaFormat.COLOR_STANDARD_BT2020 -> HdrType.HDR10
            else -> HdrType.NONE
        }
        return MediaTrackInfo(
            index = index,
            kind = kind,
            mime = mime,
            codec = codecLabel(mime),
            codecString = format.string(MediaFormat.KEY_CODECS_STRING),
            profile = profileName(mime, profileValue),
            level = levelName(mime, levelValue),
            title = format.string("title") ?: format.string("track-name"),
            language = format.string(MediaFormat.KEY_LANGUAGE)?.takeIf { it.isNotBlank() && it != "und" },
            isDefault = format.int(MediaFormat.KEY_IS_DEFAULT) == 1,
            bitrate = format.long(MediaFormat.KEY_BIT_RATE)?.takeIf { it > 0 },
            width = format.int(MediaFormat.KEY_WIDTH)?.takeIf { it > 0 },
            height = format.int(MediaFormat.KEY_HEIGHT)?.takeIf { it > 0 },
            frameRate = format.number(MediaFormat.KEY_FRAME_RATE)?.toFloat()?.takeIf { it > 0f },
            rotation = format.int("rotation-degrees")?.takeIf { it != 0 },
            hdr = hdr,
            colorStandard = colorStandardName(standard),
            colorTransfer = colorTransferName(transfer),
            colorRange = when (format.int(MediaFormat.KEY_COLOR_RANGE)) {
                MediaFormat.COLOR_RANGE_LIMITED -> "Limited"
                MediaFormat.COLOR_RANGE_FULL -> "Full"
                else -> null
            },
            channels = format.int(MediaFormat.KEY_CHANNEL_COUNT)?.takeIf { it > 0 },
            sampleRate = format.int(MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it > 0 },
        )
    }

    private fun containerLabel(source: MediaSource, mime: String?): String? {
        val name = source.displayName
        val extension = name.substringAfterLast('.', "").uppercase(Locale.US)
        if (extension.isNotEmpty() && extension.length <= 5 && extension.none { it.isDigit() }) return extension
        return when {
            mime == null -> null
            mime.contains("matroska") -> "MKV"
            mime.contains("mp4") -> "MP4"
            mime.startsWith("audio/") -> "AUDIO"
            else -> mime.substringAfter('/').uppercase(Locale.US)
        }
    }

    // ---- 编码名映射 ----

    fun codecLabel(mime: String?): String? {
        val value = mime?.lowercase(Locale.US) ?: return null
        return when {
            value.contains("dolby-vision") || value.contains("dvhe") || value.contains("dvh1") -> "Dolby Vision"
            value.contains("hevc") || value.contains("h265") -> "HEVC"
            value.contains("avc") || value.contains("h264") -> "H.264"
            value.contains("av01") -> "AV1"
            value.contains("vp9") || value.contains("vp09") -> "VP9"
            value.contains("vp8") -> "VP8"
            value.contains("mpeg4") -> "MPEG-4"
            value.contains("mpeg2") -> "MPEG-2"
            value.contains("vc-1") || value.contains("vc1") -> "VC-1"
            value.contains("avs") -> "AVS"
            value.contains("mjpeg") -> "Motion JPEG"
            value.contains("eac3") || value.contains("e-ac-3") -> "E-AC-3"
            value.contains("ac3") || value.contains("ac-3") -> "AC-3"
            value.contains("dts") -> "DTS"
            value.contains("truehd") -> "TrueHD"
            value.contains("mlp") -> "MLP"
            value.contains("flac") -> "FLAC"
            value.contains("alac") -> "ALAC"
            value.contains("opus") -> "Opus"
            value.contains("vorbis") -> "Vorbis"
            value.contains("mp3") || value.contains("mpeg") && value.startsWith("audio/") -> "MP3"
            value.contains("aac") || value.contains("mp4a") -> "AAC"
            value.contains("amr") -> "AMR"
            value.contains("pcm") || value.contains("raw") -> "PCM"
            value.contains("wma") -> "WMA"
            value.contains("ape") -> "APE"
            value.contains("subrip") || value.contains("srt") -> "SubRip"
            value.contains("ssa") || value.contains("ass") -> "ASS"
            value.contains("webvtt") || value.contains("vtt") -> "WebVTT"
            value.contains("pgs") || value.contains("hdmv") -> "PGS"
            value.contains("dvd") && value.startsWith("text/") -> "DVD Subtitle"
            value.startsWith("text/") -> value.substringAfter('/').uppercase(Locale.US)
            else -> null
        }
    }

    private fun profileName(mime: String, profile: Int?): String? {
        if (profile == null) return null
        return when {
            mime.contains("avc") || mime.contains("h264") -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "Baseline"
                MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "Main"
                MediaCodecInfo.CodecProfileLevel.AVCProfileExtended -> "Extended"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "High"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "High 10"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 -> "High 4:2:2"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444 -> "High 4:4:4"
                else -> null
            }
            mime.contains("hevc") || mime.contains("h265") -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> "Main"
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
                else -> null
            }
            mime.contains("av01") -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8 -> "Main 8-bit"
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 -> "Main 10-bit"
                else -> null
            }
            mime.contains("vp9") -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.VP9Profile0 -> "Profile 0"
                MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR -> "Profile 2 HDR"
                else -> null
            }
            mime.startsWith("audio/") -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.AACObjectLC -> "LC"
                MediaCodecInfo.CodecProfileLevel.AACObjectHE -> "HE-AAC"
                MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS -> "HE-AAC v2"
                MediaCodecInfo.CodecProfileLevel.AACObjectELD -> "ELD"
                MediaCodecInfo.CodecProfileLevel.AACObjectXHE -> "xHE-AAC"
                else -> null
            }
            else -> null
        }
    }

    private fun levelName(mime: String, level: Int?): String? {
        if (level == null || level <= 0) return null
        val isAvc = mime.contains("avc") || mime.contains("h264")
        val isHevc = mime.contains("hevc") || mime.contains("h265")
        return if (isAvc || isHevc) {
            String.format(Locale.US, "L%d.%d", level / 10, level % 10)
        } else {
            "L$level"
        }
    }

    private fun colorStandardName(value: Int?): String? = when (value) {
        MediaFormat.COLOR_STANDARD_BT709 -> "BT.709"
        MediaFormat.COLOR_STANDARD_BT601_PAL -> "BT.601 PAL"
        MediaFormat.COLOR_STANDARD_BT601_NTSC -> "BT.601 NTSC"
        MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
        else -> null
    }

    private fun colorTransferName(value: Int?): String? = when (value) {
        MediaFormat.COLOR_TRANSFER_LINEAR -> "Linear"
        MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR"
        MediaFormat.COLOR_TRANSFER_ST2084 -> "PQ (ST2084)"
        MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
        else -> null
    }

    // ---- MediaFormat 安全读取 ----

    private fun String.isDolbyVision(): Boolean =
        contains("dolby-vision") || contains("dvhe") || contains("dvh1") || contains("dav1")

    private fun MediaFormat.int(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.long(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null

    private fun MediaFormat.number(key: String): Number? =
        if (containsKey(key)) runCatching { getNumber(key) }.getOrNull() else null

    private fun MediaFormat.string(key: String): String? =
        if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null
}
