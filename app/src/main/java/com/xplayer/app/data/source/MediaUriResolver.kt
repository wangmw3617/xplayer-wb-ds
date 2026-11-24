package com.xplayer.app.data.source

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.xplayer.app.data.scanner.SubtitleFinder
import java.io.File
import java.util.Locale

/**
 * 把外部应用传来的 Uri 归一化成播放源。
 *
 * 策略：能反解成真实文件路径的（同盘、Downloads、MediaStore）一律走 [LocalFileSource]，
 * 从而获得 mpv 内核、同目录连播、外挂字幕与播放记录等全部能力；
 * 反解不出来的（第三方网盘、SAF 文档等）退化为 [ContentUriSource]，仅由 Media3 播。
 */
object MediaUriResolver {

    private const val TAG = "MediaUriResolver"

    fun resolve(context: Context, uri: Uri): MediaSource? {
        return runCatching { resolveInternal(context, uri) }
            .onFailure { Log.w(TAG, "resolve failed for $uri: ${it.message}") }
            .getOrNull()
    }

    private fun resolveInternal(context: Context, uri: Uri): MediaSource? {
        val name = displayName(context, uri)
        return when (uri.scheme?.lowercase(Locale.US)) {
            "file" -> {
                val path = uri.path ?: return null
                localSource(path) ?: ContentUriSource(uri.toString(), name, mimeOf(context, uri))
            }

            "content" -> {
                val path = contentToPath(context, uri)
                val local = path?.let { localSource(it) }
                local ?: ContentUriSource(uri.toString(), name, mimeOf(context, uri))
            }

            "http", "https" -> NetworkUrlSource(
                url = uri.toString(),
                displayName = name.ifBlank { uri.lastPathSegment ?: uri.toString() },
            )

            else -> null
        }
    }

    private fun localSource(path: String): LocalFileSource? {
        val file = File(path)
        if (!file.isFile || !file.canRead()) return null
        return LocalFileSource(
            path = file.absolutePath,
            displayName = file.nameWithoutExtension.ifBlank { file.name },
            subtitles = SubtitleFinder.find(file.absolutePath),
            folderPath = file.parent ?: "",
        )
    }

    fun displayName(context: Context, uri: Uri): String {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "media"
    }

    fun mimeOf(context: Context, uri: Uri): String? =
        runCatching { context.contentResolver.getType(uri) }.getOrNull()

    fun sizeOf(context: Context, uri: Uri): Long {
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getLong(index)
                    } else {
                        -1L
                    }
                }
        }.getOrNull()
        return size ?: -1L
    }

    /**
     * content:// → 真实路径。只覆盖最常见的三类来源，
     * 失败返回 null，由调用方退化成 ContentUriSource。
     */
    private fun contentToPath(context: Context, uri: Uri): String? {
        // 1) 能直接查到 _data 的（MediaStore 等）
        queryDataColumn(context, uri)?.let { return it }

        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()

        // 2) 外部存储 documents provider：primary:Movies/a.mkv / 1A2B-3C4D:Movies/a.mkv
        if (uri.authority == EXTERNAL_STORAGE_AUTHORITY && docId != null) {
            val parts = docId.split(':', limit = 2)
            if (parts.size == 2) {
                val volume = parts[0]
                val relative = parts[1]
                val root = if (volume.equals("primary", ignoreCase = true)) {
                    @Suppress("DEPRECATION")
                    Environment.getExternalStorageDirectory()
                } else {
                    File("/storage/$volume")
                }
                val candidate = File(root, relative)
                if (candidate.isFile && candidate.canRead()) return candidate.absolutePath
            }
        }

        // 3) 下载 provider：raw:/storage/... 或 msf:123
        if (uri.authority == DOWNLOADS_AUTHORITY && docId != null) {
            if (docId.startsWith("raw:", ignoreCase = true)) {
                val raw = docId.substringAfter("raw:")
                if (File(raw).isFile && File(raw).canRead()) return raw
            }
        }

        // 4) 媒体库 documents provider：video:123 / audio:123
        if (docId != null && docId.contains(':')) {
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
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        return path?.takeIf { it.isNotBlank() && File(it).canRead() }
    }

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"
}
