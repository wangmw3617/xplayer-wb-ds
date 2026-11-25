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
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        val subFiles = files.filter { it.isFile && isSubtitle(it.name) }
        return matchForVideo(video, subFiles)
    }

    /**
     * 对同一目录下的多个视频只列举一次磁盘目录，避免播放列表加载时 N 次 listFiles 阻塞主线程/FUSE。
     */
    fun findForFolder(dirPath: String, videoPaths: List<String>): Map<String, List<SidecarSubtitle>> {
        if (videoPaths.isEmpty()) return emptyMap()
        val dir = File(dirPath)
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return emptyMap()
        val subFiles = files.filter { it.isFile && isSubtitle(it.name) }
        if (subFiles.isEmpty()) return emptyMap()
        return videoPaths.associateWith { path ->
            matchForVideo(File(path), subFiles)
        }
    }

    private fun matchForVideo(video: File, subFiles: List<File>): List<SidecarSubtitle> {
        val base = video.nameWithoutExtension.lowercase(Locale.US)
        if (base.isEmpty()) return emptyList()
        return subFiles.asSequence()
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
