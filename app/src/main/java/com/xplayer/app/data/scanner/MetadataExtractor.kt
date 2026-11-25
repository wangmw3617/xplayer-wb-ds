package com.xplayer.app.data.scanner

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.xplayer.app.data.local.HdrType
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.util.Formatters
import java.io.File
import java.util.Locale

/**
 * 元数据提取：MediaMetadataRetriever 取时长/分辨率/码率/帧率，
 * MediaExtractor 遍历轨道判断 HDR 传输特性与内封 ASS 字幕，并生成关键帧缩略图。
 */
class MetadataExtractor(private val thumbnailDir: File) {

    private val assMimes = setOf("text/x-ssa", "text/x-ass", "application/x-ass", "application/x-ssa")

    fun extract(file: File, externalAssHint: Boolean? = null): VideoEntity {
        val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        var duration = 0L
        var width = 0
        var height = 0
        var bitrate = 0L
        var frameRate = 0f
        var codec: String? = null
        var thumbnail: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull() ?: 0f
            codec = Formatters.videoCodecLabel(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val swap = width
                width = height
                height = swap
            }
            thumbnail = createThumbnail(retriever, file)
        } catch (error: Throwable) {
            Log.w(TAG, "metadata failed for $path: ${error.message}")
        } finally {
            runCatching { retriever.release() }
        }

        val tracks = inspectTracks(file)
        val hdr = when {
            tracks.hdr != HdrType.NONE -> tracks.hdr
            else -> hdrFromName(file.nameWithoutExtension)
        }

        val hasExternalAss = externalAssHint ?: SubtitleFinder.find(path).any { SubtitleFinder.isAss(it.path) }

        return VideoEntity(
            path = path,
            folderPath = file.parent ?: "",
            title = file.nameWithoutExtension,
            durationMs = duration,
            sizeBytes = file.length(),
            dateAdded = System.currentTimeMillis(),
            dateModified = file.lastModified(),
            width = width,
            height = height,
            videoCodec = codec ?: tracks.codecMime,
            hdrType = hdr,
            hasAssSubtitle = tracks.hasAss || hasExternalAss,
            thumbnailPath = thumbnail,
            bitrate = bitrate,
            frameRate = frameRate,
        )
    }

    private data class TrackInspection(
        val hdr: HdrType = HdrType.NONE,
        val hasAss: Boolean = false,
        val codecMime: String? = null,
    )

    private fun inspectTracks(file: File): TrackInspection {
        val extractor = MediaExtractor()
        var hdr = HdrType.NONE
        var hasAss = false
        var codecMime: String? = null
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    if (codecMime == null) codecMime = mime
                    if (mime.contains("dolby-vision", ignoreCase = true)) {
                        hdr = HdrType.DOLBY_VISION
                    } else if (hdr == HdrType.NONE) {
                        hdr = hdrFromFormat(format)
                    }
                } else if (mime in assMimes) {
                    hasAss = true
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "track inspection failed for ${file.name}: ${error.message}")
        } finally {
            runCatching { extractor.release() }
        }
        return TrackInspection(hdr = hdr, hasAss = hasAss, codecMime = codecMime)
    }

    private fun hdrFromFormat(format: MediaFormat): HdrType {
        val transfer = runCatching { format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) }.getOrNull()
        val standard = runCatching { format.getInteger(MediaFormat.KEY_COLOR_STANDARD) }.getOrNull()
        return when {
            transfer == MediaFormat.COLOR_TRANSFER_ST2084 -> HdrType.HDR10
            transfer == MediaFormat.COLOR_TRANSFER_HLG -> HdrType.HLG
            standard == MediaFormat.COLOR_STANDARD_BT2020 -> HdrType.HDR10
            else -> HdrType.NONE
        }
    }

    /** 部分封装（如 MKV Dolby Vision）无法从 MediaFormat 读出，退化为文件名特征判断 */
    private fun hdrFromName(name: String): HdrType {
        val lower = name.lowercase(Locale.US)
        return when {
            lower.contains("dovi") || lower.contains(".dv.") || lower.contains("[dv]") || lower.contains("dolby.vision") ->
                HdrType.DOLBY_VISION
            lower.contains("hdr10+") || lower.contains("hdr10plus") -> HdrType.HDR10_PLUS
            lower.contains("hdr") || lower.contains("hdr10") -> HdrType.HDR10
            lower.contains("hlg") -> HdrType.HLG
            else -> HdrType.NONE
        }
    }

    private fun createThumbnail(retriever: MediaMetadataRetriever, file: File): String? = runCatching {
        val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
            ?: return@runCatching null
        val target = if (frame.width > THUMBNAIL_WIDTH) {
            val ratio = THUMBNAIL_WIDTH.toFloat() / frame.width
            android.graphics.Bitmap.createScaledBitmap(
                frame,
                THUMBNAIL_WIDTH,
                (frame.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            frame
        }
        thumbnailDir.mkdirs()
        val output = File(thumbnailDir, Integer.toHexString(file.absolutePath.hashCode()) + ".jpg")
        output.outputStream().use { stream ->
            target.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, stream)
        }
        if (target !== frame) target.recycle()
        frame.recycle()
        output.absolutePath
    }.getOrNull()

    companion object {
        private const val TAG = "MetadataExtractor"
        private const val THUMBNAIL_WIDTH = 512
    }
}
