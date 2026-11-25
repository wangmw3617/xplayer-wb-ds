package com.xplayer.app.data.scanner

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.xplayer.app.data.source.SidecarSubtitle
import java.io.File
import java.util.Locale

/**
 * 同目录外挂字幕自动发现：文件名与视频主名一致（或带语言后缀）即视为该视频的字幕。
 */
object SubtitleFinder {

    private val MIME_BY_EXT = mapOf(
        "srt" to "application/x-subrip",
        "ass" to "text/x-ssa",
        "ssa" to "text/x-ssa",
        "vtt" to "text/vtt",
        "sub" to "text/x-microdvd",
    )

    val extensions: Set<String> get() = MIME_BY_EXT.keys

    fun mimeFor(path: String): String? =
        MIME_BY_EXT[path.substringAfterLast('.', "").lowercase(Locale.US)]

    fun isSubtitle(path: String): Boolean = mimeFor(path) != null

    fun isAss(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
        return ext == "ass" || ext == "ssa"
    }

    fun find(videoPath: String): List<SidecarSubtitle> {
        val video = File(videoPath)
        val dir = video.parentFile ?: return emptyList()
        val base = video.nameWithoutExtension.lowercase(Locale.US)
        if (base.isEmpty()) return emptyList()
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        return files.asSequence()
            .filter { it.isFile && isSubtitle(it.name) }
            .filter { file ->
                val name = file.nameWithoutExtension.lowercase(Locale.US)
                name == base ||
                    name.startsWith("$base.") ||
                    name.startsWith("${base}_") ||
                    name.startsWith("$base-")
            }
            .sortedBy { it.name.lowercase(Locale.US) }
            .map { SidecarSubtitle(path = it.absolutePath, name = it.name, mimeType = mimeFor(it.name) ?: "text/x-ssa") }
            .toList()
    }

    /**
     * 将系统文件选择器返回的 content:// 字幕拷贝至应用缓存目录，
     * 保证 Media3 与 libmpv 均可通过真实文件路径读取且授权不会随页面退出失效。
     */
    fun importFromUri(context: Context, uri: Uri): SidecarSubtitle? = runCatching {
        val displayName = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "external.ass"
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val subDir = File(context.cacheDir, "subtitles").apply { mkdirs() }
        val target = File(subDir, "${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        SidecarSubtitle(
            path = target.absolutePath,
            name = displayName,
            mimeType = mimeFor(displayName) ?: "text/x-ssa",
        )
    }.getOrNull()
}
