package com.xplayer.app.data.scanner

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.xplayer.app.data.local.HdrType
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.scanner.MediaFormatUtils.numberOrNull
import com.xplayer.app.data.scanner.MediaFormatUtils.stringOrNull
import com.xplayer.app.util.Formatters
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * 元数据提取：MediaMetadataRetriever 取时长/分辨率/码率并生成缩略图，
 * MediaExtractor 读取真实视频编码、帧率、HDR 传输特性与内封 ASS 字幕。
 */
class MetadataExtractor(private val thumbnailDir: File) {

    fun extract(
        file: File,
        externalAssHint: Boolean? = null,
        existingId: Long = 0L,
        existingDateAdded: Long = 0L,
    ): VideoEntity {
        val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val folderPath = runCatching { file.parentFile?.canonicalPath }.getOrNull() ?: file.parent.orEmpty()
        var duration = 0L
        var width = 0
        var height = 0
        var bitrate = 0L
        var captureFps = 0f
        var containerCodec: String? = null
        var thumbnail: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull() ?: 0f
            containerCodec = Formatters.videoCodecLabel(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val swap = width
                width = height
                height = swap
            }
            thumbnail = createThumbnail(retriever, path, duration)
        } catch (error: Throwable) {
            Log.w(TAG, "metadata failed for $path: ${error.message}")
        } finally {
            runCatching { retriever.release() }
        }

        val tracks = inspectTracks(path)
        val hdr = when {
            tracks.hdr != HdrType.NONE -> tracks.hdr
            else -> hdrFromName(file.nameWithoutExtension)
        }
        val codecLabel = Formatters.videoCodecLabel(tracks.codecMime) ?: tracks.codecMime ?: containerCodec
        val frameRate = if (tracks.frameRate > 0f) tracks.frameRate else captureFps
        val hasExternalAss = externalAssHint ?: SubtitleFinder.find(path).any { SubtitleFinder.isAss(it.path) }

        return VideoEntity(
            id = existingId,
            path = path,
            folderPath = folderPath,
            title = file.nameWithoutExtension.ifBlank { file.name },
            durationMs = duration,
            sizeBytes = file.length(),
            dateAdded = if (existingDateAdded > 0L) existingDateAdded else System.currentTimeMillis(),
            dateModified = file.lastModified(),
            width = width,
            height = height,
            videoCodec = codecLabel,
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
        val frameRate: Float = 0f,
    )

    private fun inspectTracks(path: String): TrackInspection {
        val extractor = MediaExtractor()
        var hdr = HdrType.NONE
        var hasAss = false
        var codecMime: String? = null
        var frameRate = 0f
        try {
            extractor.setDataSource(path)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.stringOrNull(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/", ignoreCase = true)) {
                    if (codecMime == null) codecMime = mime
                    if (frameRate <= 0f) {
                        frameRate = format.numberOrNull(MediaFormat.KEY_FRAME_RATE)?.toFloat()?.takeIf { it > 0f } ?: 0f
                    }
                    val trackHdr = MediaFormatUtils.detectHdrType(format, mime)
                    if (trackHdr == HdrType.DOLBY_VISION) {
                        hdr = HdrType.DOLBY_VISION
                    } else if (hdr == HdrType.NONE && trackHdr != HdrType.NONE) {
                        hdr = trackHdr
                    } else if (hdr == HdrType.HDR10 && trackHdr == HdrType.HDR10_PLUS) {
                        hdr = HdrType.HDR10_PLUS
                    }
                } else if (MediaFormatUtils.isAssMime(mime)) {
                    hasAss = true
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "track inspection failed for $path: ${error.message}")
        } finally {
            runCatching { extractor.release() }
        }
        return TrackInspection(hdr = hdr, hasAss = hasAss, codecMime = codecMime, frameRate = frameRate)
    }

    /** 部分封装（如 MKV Dolby Vision）无法从 MediaFormat 读出，退化为文件名特征判断 */
    private fun hdrFromName(name: String): HdrType {
        val lower = name.lowercase(Locale.US)
        return when {
            DOVI_REGEX.containsMatchIn(lower) -> HdrType.DOLBY_VISION
            HDR10_PLUS_REGEX.containsMatchIn(lower) -> HdrType.HDR10_PLUS
            HDR_REGEX.containsMatchIn(lower) -> HdrType.HDR10
            HLG_REGEX.containsMatchIn(lower) -> HdrType.HLG
            else -> HdrType.NONE
        }
    }

    private fun createThumbnail(retriever: MediaMetadataRetriever, canonicalPath: String, durationMs: Long): String? {
        var frame: Bitmap? = null
        var target: Bitmap? = null
        val output = File(thumbnailDir, thumbnailFileName(canonicalPath))
        return try {
            val sampleUs = if (durationMs > 10_000L) {
                (durationMs * 150L).coerceAtMost(15_000_000L)
            } else {
                1_000_000L
            }
            frame = retriever.getFrameAtTime(sampleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null
            val src = frame
            target = if (src.width > THUMBNAIL_WIDTH) {
                val ratio = THUMBNAIL_WIDTH.toFloat() / src.width
                Bitmap.createScaledBitmap(
                    src,
                    THUMBNAIL_WIDTH,
                    (src.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                src
            }
            thumbnailDir.mkdirs()
            val saved = output.outputStream().use { stream ->
                target.compress(Bitmap.CompressFormat.JPEG, 86, stream)
            }
            if (!saved || output.length() <= 0L) {
                runCatching { output.delete() }
                null
            } else {
                output.absolutePath
            }
        } catch (error: Throwable) {
            runCatching { output.delete() }
            Log.w(TAG, "thumbnail failed for $canonicalPath: ${error.message}")
            null
        } finally {
            if (target != null && target !== frame && !target.isRecycled) target.recycle()
            if (frame != null && !frame.isRecycled) frame.recycle()
        }
    }

    private fun thumbnailFileName(canonicalPath: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalPath.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(16)
        for (i in 0 until 8) {
            hex.append(String.format(Locale.US, "%02x", digest[i].toInt() and 0xFF))
        }
        return "$hex.jpg"
    }

    companion object {
        private const val TAG = "MetadataExtractor"
        private const val THUMBNAIL_WIDTH = 512

        private val DOVI_REGEX = Regex("""(^|[\s.\[\(_-])(dovi|dv|dolby\.?vision)([\s.\]\)\_-]|$)""")
        private val HDR10_PLUS_REGEX = Regex("""(^|[\s.\[\(_-])hdr10\s*(\+|plus)([\s.\]\)\_-]|$)""")
        private val HDR_REGEX = Regex("""(^|[\s.\[\(_-])hdr1?0?([\s.\]\)\_-]|$)""")
        private val HLG_REGEX = Regex("""(^|[\s.\[\(_-])hlg([\s.\]\)\_-]|$)""")
    }
}
