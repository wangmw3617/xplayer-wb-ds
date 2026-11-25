package com.xplayer.app.data.media

import android.content.Context
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.xplayer.app.data.local.HdrType
import com.xplayer.app.data.scanner.MediaFormatUtils
import com.xplayer.app.data.scanner.MediaFormatUtils.intOrNull
import com.xplayer.app.data.scanner.MediaFormatUtils.longOrNull
import com.xplayer.app.data.scanner.MediaFormatUtils.numberOrNull
import com.xplayer.app.data.scanner.MediaFormatUtils.stringOrNull
import com.xplayer.app.data.scanner.SubtitleFinder
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaSource
import com.xplayer.app.data.source.MediaUriResolver
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.data.source.SidecarSubtitle
import com.xplayer.app.util.Formatters
import java.io.File
import java.util.Locale

enum class MediaTrackKind { VIDEO, AUDIO, SUBTITLE }

/** 单条轨道（视频/音频/字幕）的完整参数 */
data class MediaTrackInfo(
    val kind: MediaTrackKind,
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
    val videoTracks: List<MediaTrackInfo> = tracks.filter { it.kind == MediaTrackKind.VIDEO }
    val audioTracks: List<MediaTrackInfo> = tracks.filter { it.kind == MediaTrackKind.AUDIO }
    val subtitleTracks: List<MediaTrackInfo> = tracks.filter { it.kind == MediaTrackKind.SUBTITLE }

    val hdr: HdrType = videoTracks.maxByOrNull { hdrRank(it.hdr) }?.hdr ?: HdrType.NONE
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

        val contentMeta = if (source is ContentUriSource) {
            MediaUriResolver.queryContentMeta(context, Uri.parse(source.uri))
        } else {
            null
        }
        val size = file?.length() ?: contentMeta?.sizeBytes ?: -1L
        val modified = file?.lastModified() ?: contentMeta?.modifiedAt ?: 0L
        val sidecars = when (source) {
            is LocalFileSource -> source.subtitles.ifEmpty { SubtitleFinder.find(source.path) }
            else -> emptyList()
        }

        val tracks = mutableListOf<MediaTrackInfo>()
        var durationUs = 0L
        var containerMime: String? = null

        if (source !is NetworkUrlSource) {
            val handle = openHandle(context, source) ?: return null
            val parsedOk = runCatching {
                handle.use {
                    val extractor = it.extractor
                    repeat(extractor.trackCount) { index ->
                        val format = extractor.getTrackFormat(index)
                        val mime = format.stringOrNull(MediaFormat.KEY_MIME) ?: return@repeat
                        if (containerMime == null) containerMime = mime
                        durationUs = maxOf(durationUs, format.longOrNull(MediaFormat.KEY_DURATION) ?: 0L)
                        val kind = when {
                            mime.startsWith("video/", ignoreCase = true) -> MediaTrackKind.VIDEO
                            mime.startsWith("audio/", ignoreCase = true) -> MediaTrackKind.AUDIO
                            MediaFormatUtils.isSubtitleMime(mime) -> MediaTrackKind.SUBTITLE
                            else -> return@repeat
                        }
                        tracks += readTrack(kind, mime, format)
                    }
                }
            }.onFailure { Log.w(TAG, "probe failed for ${source.key}: ${it.message}") }.isSuccess
            if (!parsedOk) return null
        }

        val durationMs = durationUs / 1000L
        val overallBitrate = when {
            durationMs > 0L && size > 0L -> size * 8_000L / durationMs
            else -> tracks.sumOf { it.bitrate ?: 0L }
        }

        return MediaInfo(
            displayName = contentMeta?.displayName ?: source.displayName,
            location = source.key,
            isContentUri = source is ContentUriSource,
            sizeBytes = size,
            modifiedAt = modified,
            container = containerLabel(source, containerMime, contentMeta?.displayName),
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

    private inline fun tryExtractor(block: MediaExtractor.() -> Unit): MediaExtractor? {
        val extractor = MediaExtractor()
        return runCatching {
            extractor.block()
            extractor
        }.getOrElse {
            runCatching { extractor.release() }
            null
        }
    }

    private fun openHandle(context: Context, source: MediaSource): ExtractHandle? = when (source) {
        is LocalFileSource -> tryExtractor { setDataSource(source.path) }?.let { ExtractHandle(it, null) }
        is ContentUriSource -> openUriHandle(context, Uri.parse(source.uri))
        // 网络流的轨道参数必须拉流才知道，这里只给基础信息，避免信息页卡住
        is NetworkUrlSource -> null
    }

    /**
     * 外部「打开方式」传来的 content:// 由各家 provider 实现，兼容性差异很大，
     * 依次尝试三条路：ContentResolver 直接打开 → AssetFileDescriptor（带 offset/length）
     * → 裸 fd（长度未知的网盘 provider）。每次失败都立刻 release 临时 MediaExtractor。
     */
    private fun openUriHandle(context: Context, uri: Uri): ExtractHandle? {
        tryExtractor { setDataSource(context, uri, null) }?.let { return ExtractHandle(it, null) }

        val descriptor = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")
        }.getOrNull()
        if (descriptor != null) {
            val extractor = (
                if (descriptor.length >= 0L) {
                    tryExtractor { setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length) }
                } else {
                    null
                }
                ) ?: tryExtractor { setDataSource(descriptor.fileDescriptor) }
            if (extractor != null) return ExtractHandle(extractor, descriptor)
            runCatching { descriptor.close() }
        }

        val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (pfd != null) {
            val extractor = tryExtractor { setDataSource(pfd.fileDescriptor) }
            if (extractor != null) return ExtractHandle(extractor, pfd)
            runCatching { pfd.close() }
        }
        return null
    }

    private fun readTrack(
        kind: MediaTrackKind,
        mime: String,
        format: MediaFormat,
    ): MediaTrackInfo {
        val profileValue = format.intOrNull(MediaFormat.KEY_PROFILE)
        val levelValue = format.intOrNull(MediaFormat.KEY_LEVEL)
        val transfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
        val standard = format.intOrNull(MediaFormat.KEY_COLOR_STANDARD)
        return MediaTrackInfo(
            kind = kind,
            codec = Formatters.codecLabel(mime, compact = false),
            codecString = format.stringOrNull(MediaFormat.KEY_CODECS_STRING),
            profile = profileName(mime, profileValue),
            level = levelName(mime, levelValue),
            title = MediaFormatUtils.trackTitle(format),
            language = MediaFormatUtils.trackLanguage(format),
            isDefault = MediaFormatUtils.trackIsDefault(format),
            bitrate = format.longOrNull(MediaFormat.KEY_BIT_RATE)?.takeIf { it > 0 },
            width = format.intOrNull(MediaFormat.KEY_WIDTH)?.takeIf { it > 0 },
            height = format.intOrNull(MediaFormat.KEY_HEIGHT)?.takeIf { it > 0 },
            frameRate = format.numberOrNull(MediaFormat.KEY_FRAME_RATE)?.toFloat()?.takeIf { it > 0f },
            rotation = format.intOrNull("rotation-degrees")?.takeIf { it != 0 },
            hdr = if (kind == MediaTrackKind.VIDEO) MediaFormatUtils.detectHdrType(format, mime) else HdrType.NONE,
            colorStandard = colorStandardName(standard),
            colorTransfer = colorTransferName(transfer),
            colorRange = when (format.intOrNull(MediaFormat.KEY_COLOR_RANGE)) {
                MediaFormat.COLOR_RANGE_LIMITED -> "Limited"
                MediaFormat.COLOR_RANGE_FULL -> "Full"
                else -> null
            },
            channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.takeIf { it > 0 },
            sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it > 0 },
        )
    }

    private fun containerLabel(
        source: MediaSource,
        mime: String?,
        resolvedContentName: String?,
    ): String? {
        val rawName = when (source) {
            is LocalFileSource -> File(source.path).name
            is ContentUriSource -> resolvedContentName ?: source.displayName
            is NetworkUrlSource -> source.url.substringBefore('?').substringAfterLast('/')
        }
        val ext = rawName.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext in KNOWN_CONTAINER_EXTS) return ext.uppercase(Locale.US)
        return when {
            mime == null -> null
            mime.contains("matroska", ignoreCase = true) -> "MKV"
            mime.contains("webm", ignoreCase = true) -> "WebM"
            mime.contains("mp4", ignoreCase = true) -> "MP4"
            mime.contains("mp2t", ignoreCase = true) -> "TS"
            mime.contains("x-msvideo", ignoreCase = true) -> "AVI"
            mime.contains("x-flv", ignoreCase = true) -> "FLV"
            mime.contains("quicktime", ignoreCase = true) -> "MOV"
            else -> null
        }
    }

    private fun profileName(mime: String, profile: Int?): String? {
        if (profile == null) return null
        return when {
            mime.contains("avc", ignoreCase = true) || mime.contains("h264", ignoreCase = true) -> when (profile) {
                CodecProfileLevel.AVCProfileBaseline -> "Baseline"
                CodecProfileLevel.AVCProfileMain -> "Main"
                CodecProfileLevel.AVCProfileExtended -> "Extended"
                CodecProfileLevel.AVCProfileHigh -> "High"
                CodecProfileLevel.AVCProfileHigh10 -> "High 10"
                CodecProfileLevel.AVCProfileHigh422 -> "High 4:2:2"
                CodecProfileLevel.AVCProfileHigh444 -> "High 4:4:4"
                else -> null
            }
            mime.contains("hevc", ignoreCase = true) || mime.contains("h265", ignoreCase = true) -> when (profile) {
                CodecProfileLevel.HEVCProfileMain -> "Main"
                CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
                CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
                else -> null
            }
            mime.contains("av01", ignoreCase = true) -> when (profile) {
                CodecProfileLevel.AV1ProfileMain8 -> "Main 8-bit"
                CodecProfileLevel.AV1ProfileMain10 -> "Main 10-bit"
                else -> null
            }
            mime.contains("vp9", ignoreCase = true) -> when (profile) {
                CodecProfileLevel.VP9Profile0 -> "Profile 0"
                CodecProfileLevel.VP9Profile2HDR -> "Profile 2 HDR"
                else -> null
            }
            mime.startsWith("audio/", ignoreCase = true) -> when (profile) {
                CodecProfileLevel.AACObjectLC -> "LC"
                CodecProfileLevel.AACObjectHE -> "HE-AAC"
                CodecProfileLevel.AACObjectHE_PS -> "HE-AAC v2"
                CodecProfileLevel.AACObjectELD -> "ELD"
                CodecProfileLevel.AACObjectXHE -> "xHE-AAC"
                else -> null
            }
            else -> null
        }
    }

    /** 将 Android MediaCodecLevel 位掩码转换为人类可读的 Lx.y 级别 */
    private fun levelName(mime: String, level: Int?): String? {
        if (level == null || level <= 0) return null
        return when {
            mime.contains("avc", ignoreCase = true) || mime.contains("h264", ignoreCase = true) -> when (level) {
                CodecProfileLevel.AVCLevel1 -> "L1"
                CodecProfileLevel.AVCLevel1b -> "L1b"
                CodecProfileLevel.AVCLevel11 -> "L1.1"
                CodecProfileLevel.AVCLevel12 -> "L1.2"
                CodecProfileLevel.AVCLevel13 -> "L1.3"
                CodecProfileLevel.AVCLevel2 -> "L2"
                CodecProfileLevel.AVCLevel21 -> "L2.1"
                CodecProfileLevel.AVCLevel22 -> "L2.2"
                CodecProfileLevel.AVCLevel3 -> "L3"
                CodecProfileLevel.AVCLevel31 -> "L3.1"
                CodecProfileLevel.AVCLevel32 -> "L3.2"
                CodecProfileLevel.AVCLevel4 -> "L4"
                CodecProfileLevel.AVCLevel41 -> "L4.1"
                CodecProfileLevel.AVCLevel42 -> "L4.2"
                CodecProfileLevel.AVCLevel5 -> "L5"
                CodecProfileLevel.AVCLevel51 -> "L5.1"
                CodecProfileLevel.AVCLevel52 -> "L5.2"
                CodecProfileLevel.AVCLevel6 -> "L6"
                CodecProfileLevel.AVCLevel61 -> "L6.1"
                CodecProfileLevel.AVCLevel62 -> "L6.2"
                else -> null
            }
            mime.contains("hevc", ignoreCase = true) || mime.contains("h265", ignoreCase = true) -> when (level) {
                CodecProfileLevel.HEVCMainTierLevel1, CodecProfileLevel.HEVCHighTierLevel1 -> "L1"
                CodecProfileLevel.HEVCMainTierLevel2, CodecProfileLevel.HEVCHighTierLevel2 -> "L2"
                CodecProfileLevel.HEVCMainTierLevel21, CodecProfileLevel.HEVCHighTierLevel21 -> "L2.1"
                CodecProfileLevel.HEVCMainTierLevel3, CodecProfileLevel.HEVCHighTierLevel3 -> "L3"
                CodecProfileLevel.HEVCMainTierLevel31, CodecProfileLevel.HEVCHighTierLevel31 -> "L3.1"
                CodecProfileLevel.HEVCMainTierLevel4, CodecProfileLevel.HEVCHighTierLevel4 -> "L4"
                CodecProfileLevel.HEVCMainTierLevel41, CodecProfileLevel.HEVCHighTierLevel41 -> "L4.1"
                CodecProfileLevel.HEVCMainTierLevel5, CodecProfileLevel.HEVCHighTierLevel5 -> "L5"
                CodecProfileLevel.HEVCMainTierLevel51, CodecProfileLevel.HEVCHighTierLevel51 -> "L5.1"
                CodecProfileLevel.HEVCMainTierLevel52, CodecProfileLevel.HEVCHighTierLevel52 -> "L5.2"
                CodecProfileLevel.HEVCMainTierLevel6, CodecProfileLevel.HEVCHighTierLevel6 -> "L6"
                CodecProfileLevel.HEVCMainTierLevel61, CodecProfileLevel.HEVCHighTierLevel61 -> "L6.1"
                CodecProfileLevel.HEVCMainTierLevel62, CodecProfileLevel.HEVCHighTierLevel62 -> "L6.2"
                else -> null
            }
            else -> null
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

    private val KNOWN_CONTAINER_EXTS = setOf(
        "mp4", "mkv", "mov", "avi", "webm", "flv", "ts", "m2ts", "mts",
        "m4v", "wmv", "3gp", "mpg", "mpeg", "rmvb", "rm", "vob", "ogv",
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "ape", "mka",
    )
}
