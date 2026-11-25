package com.xplayer.app.data.source

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.xplayer.app.data.scanner.SubtitleFinder
import java.io.File
import java.util.Locale

data class ContentFileMeta(
    val displayName: String,
    val sizeBytes: Long = -1L,
    val modifiedAt: Long = 0L,
)

/**
 * 把外部应用传来的 Uri 归一化成播放源。
 *
 * 策略：能反解成真实文件路径的（同盘、Downloads、MediaStore）一律走 [LocalFileSource]，
 * 从而获得 mpv 内核、同目录连播、外挂字幕与播放记录等全部能力；
 * 反解不出来的（第三方网盘、SAF 文档等）退化为 [ContentUriSource]，仅由 Media3 播。
 */
object MediaUriResolver {

    private const val TAG = "MediaUriResolver"
    private const val SEC_TO_MS_THRESHOLD = 100_000_000_000L

    fun resolve(context: Context, uri: Uri): MediaSource? {
        return runCatching { resolveInternal(context, uri) }
            .onFailure { Log.w(TAG, "resolve failed for $uri: ${it.message}") }
            .getOrNull()
    }

    private fun resolveInternal(context: Context, uri: Uri): MediaSource? {
        return when (uri.scheme?.lowercase(Locale.US)) {
            "file" -> {
                val path = uri.path ?: return null
                localSource(path) ?: ContentUriSource(uri.toString(), fallbackName(uri))
            }

            "content" -> {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val path = contentToPath(context, uri)
                val local = path?.let { localSource(it) }
                local ?: ContentUriSource(uri.toString(), displayName(context, uri))
            }

            "http", "https", "rtsp", "rtmp" -> NetworkUrlSource(
                url = uri.toString(),
                displayName = fallbackName(uri),
            )

            else -> null
        }
    }

    private fun localSource(path: String): LocalFileSource? {
        val raw = File(path)
        if (!raw.isFile || !raw.canRead()) return null
        val canonicalPath = runCatching { raw.canonicalPath }.getOrDefault(raw.absolutePath)
        val canonicalFolder = runCatching { raw.parentFile?.canonicalPath }.getOrNull() ?: raw.parent.orEmpty()
        return LocalFileSource(
            path = canonicalPath,
            displayName = raw.nameWithoutExtension.ifBlank { raw.name },
            subtitles = SubtitleFinder.find(canonicalPath),
            folderPath = canonicalFolder,
        )
    }

    fun queryContentMeta(context: Context, uri: Uri): ContentFileMeta {
        var displayName: String? = null
        var size = -1L
        var modified = 0L
        runCatching {
            val projection = arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                "last_modified",
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) displayName = cursor.getString(nameIdx)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                val dateModIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val lastModIdx = cursor.getColumnIndex("last_modified")
                val rawTime = when {
                    dateModIdx >= 0 && !cursor.isNull(dateModIdx) -> cursor.getLong(dateModIdx)
                    lastModIdx >= 0 && !cursor.isNull(lastModIdx) -> cursor.getLong(lastModIdx)
                    else -> 0L
                }
                if (rawTime > 0L) {
                    modified = if (rawTime < SEC_TO_MS_THRESHOLD) rawTime * 1000L else rawTime
                }
            }
        }
        return ContentFileMeta(
            displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName(uri),
            sizeBytes = size,
            modifiedAt = modified,
        )
    }

    fun displayName(context: Context, uri: Uri): String {
        if (!uri.scheme.equals("content", ignoreCase = true)) return fallbackName(uri)
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() } ?: fallbackName(uri)
    }

    private fun fallbackName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: uri.toString().substringAfterLast('/').ifBlank { "media" }

    /**
     * content:// → 真实路径。只覆盖可验证 authority 的几类来源，
     * 失败返回 null，由调用方退化成 ContentUriSource。
     */
    private fun contentToPath(context: Context, uri: Uri): String? {
        // 1) 能直接查到 _data 的（MediaStore 等）
        queryDataColumn(context, uri)?.let { return it }

        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null

        // 2) 外部存储 documents provider：primary:Movies/a.mkv / home:Movies/a.mkv / <vol>:...
        if (uri.authority == EXTERNAL_STORAGE_AUTHORITY) {
            val parts = docId.split(':', limit = 2)
            if (parts.size == 2) {
                val volume = parts[0]
                val relative = parts[1]
                @Suppress("DEPRECATION")
                val primaryRoot = Environment.getExternalStorageDirectory()
                val candidate = when {
                    volume.equals("primary", ignoreCase = true) -> File(primaryRoot, relative)
                    volume.equals("home", ignoreCase = true) -> File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        relative,
                    )
                    else -> File("/storage/$volume", relative)
                }
                if (candidate.isFile && candidate.canRead()) return candidate.canonicalPath
            }
        }

        // 3) 下载 provider：raw:/storage/... 或 msf:123
        if (uri.authority == DOWNLOADS_AUTHORITY) {
            if (docId.startsWith("raw:", ignoreCase = true)) {
                val raw = docId.substringAfter("raw:")
                val file = File(raw)
                if (file.isFile && file.canRead()) return file.canonicalPath
            } else if (docId.startsWith("msf:", ignoreCase = true) &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
            ) {
                val id = docId.substringAfter(':').toLongOrNull()
                if (id != null) {
                    val downloadUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    queryDataColumn(context, downloadUri)?.let { return it }
                }
            }
        }

        // 4) 媒体库 documents provider：仅校验过 authority 后映射 video:123 / audio:123
        if (uri.authority == MEDIA_DOCUMENTS_AUTHORITY && docId.contains(':')) {
            val type = docId.substringBefore(':')
            val id = docId.substringAfter(':').toLongOrNull()
            val collection = when (type) {
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> null
            }
            if (collection != null && id != null) {
                queryDataColumn(context, ContentUris.withAppendedId(collection, id))?.let { return it }
            }
        }
        return null
    }

    private fun queryDataColumn(context: Context, uri: Uri): String? {
        val path = runCatching {
            @Suppress("DEPRECATION")
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { cursor ->
                    @Suppress("DEPRECATION")
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        return path?.takeIf { it.isNotBlank() && File(it).canRead() }
    }

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"
    private const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
}
