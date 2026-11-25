package com.xplayer.app.data.scanner

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.xplayer.app.data.source.SidecarSubtitle
import java.io.File
import java.util.Locale

/**
 * 外挂字幕发现与管理：支持同目录及 Subs/Subtitles 子目录自动发现（同名或带语言/分隔后缀），
 * 以及从系统文件选择器导入至应用缓存并兼容重命名同步。
 */
object SubtitleFinder {

    private const val MAX_IMPORT_BYTES = 10L * 1024 * 1024

    private val MIME_BY_EXT = mapOf(
        "srt" to "application/x-subrip",
        "ass" to "text/x-ssa",
        "ssa" to "text/x-ssa",
        "vtt" to "text/vtt",
        "sub" to "text/x-microdvd",
        "ttml" to "application/ttml+xml",
        "dfxp" to "application/ttml+xml",
    )

    private val SUBTITLE_SUBDIRS = setOf("subs", "subtitles")

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
        val subFiles = collectSubtitleEntries(dir)
        return matchForVideo(video, subFiles)
    }

    /**
     * 对同一目录下的多个视频只列举一次磁盘目录，避免播放列表加载时 N 次 listFiles 阻塞线程/FUSE。
     */
    fun findForFolder(dirPath: String, videoPaths: List<String>): Map<String, List<SidecarSubtitle>> {
        if (videoPaths.isEmpty()) return emptyMap()
        val dir = File(dirPath)
        val subFiles = collectSubtitleEntries(dir)
        if (subFiles.isEmpty()) return emptyMap()
        return videoPaths.associateWith { path ->
            matchForVideo(File(path), subFiles)
        }
    }

    /** 读取同目录及 Subs/Subtitles 子目录下全部 ASS/SSA 文件的小写主名，供扫描器按目录缓存复用 */
    fun listAssNamesInFolder(dirPath: String): List<String> =
        collectSubtitleEntries(File(dirPath))
            .asSequence()
            .filter { isAss(it.name) }
            .map { it.lowerBase }
            .toList()

    fun hasMatchingAss(videoFile: File, folderAssNames: List<String>): Boolean {
        if (folderAssNames.isEmpty()) return false
        val base = videoFile.nameWithoutExtension.lowercase(Locale.US)
        if (base.isEmpty()) return false
        return folderAssNames.any { matchesBase(it, base) }
    }

    /** 视频重命名时同步重命名同目录下的外挂字幕，避免丢失匹配关系 */
    fun renameSidecars(oldVideoPath: String, newVideoTitle: String) {
        val oldVideo = File(oldVideoPath)
        val dir = oldVideo.parentFile ?: return
        val oldBase = oldVideo.nameWithoutExtension
        val oldLower = oldBase.lowercase(Locale.US)
        if (oldLower.isEmpty() || newVideoTitle.isBlank()) return
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (file in files) {
            if (!file.isFile || !isSubtitle(file.name)) continue
            val subBase = file.nameWithoutExtension
            val subLower = subBase.lowercase(Locale.US)
            if (!matchesBase(subLower, oldLower) || subBase.length < oldBase.length) continue
            val suffix = subBase.substring(oldBase.length)
            val ext = file.extension
            val targetName = if (ext.isNotEmpty()) "$newVideoTitle$suffix.$ext" else "$newVideoTitle$suffix"
            val target = File(dir, targetName)
            if (!target.exists() || runCatching { target.canonicalPath == file.canonicalPath }.getOrDefault(false)) {
                runCatching { file.renameTo(target) }
            }
        }
    }

    private class SubEntry(
        val file: File,
        val name: String,
        val lowerName: String,
        val lowerBase: String,
    )

    private fun collectSubtitleEntries(dir: File): List<SubEntry> {
        val top = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        val entries = ArrayList<SubEntry>()
        fun addSubFiles(files: Array<File>) {
            for (item in files) {
                if (item.isFile && isSubtitle(item.name)) {
                    entries += SubEntry(
                        file = item,
                        name = item.name,
                        lowerName = item.name.lowercase(Locale.US),
                        lowerBase = item.nameWithoutExtension.lowercase(Locale.US),
                    )
                }
            }
        }
        addSubFiles(top)
        for (item in top) {
            if (item.isDirectory && item.name.lowercase(Locale.US) in SUBTITLE_SUBDIRS) {
                runCatching { item.listFiles() }.getOrNull()?.let(::addSubFiles)
            }
        }
        return entries
    }

    private fun matchForVideo(video: File, subFiles: List<SubEntry>): List<SidecarSubtitle> {
        val base = video.nameWithoutExtension.lowercase(Locale.US)
        if (base.isEmpty()) return emptyList()
        return subFiles.asSequence()
            .filter { matchesBase(it.lowerBase, base) }
            .sortedBy { it.lowerName }
            .distinctBy { it.file.absolutePath }
            .map {
                SidecarSubtitle(
                    path = it.file.absolutePath,
                    name = it.name,
                    mimeType = mimeFor(it.name) ?: "text/x-ssa",
                )
            }
            .toList()
    }

    private fun matchesBase(subBase: String, videoBase: String): Boolean =
        subBase == videoBase ||
            subBase.startsWith("$videoBase.") ||
            subBase.startsWith("${videoBase}_") ||
            subBase.startsWith("$videoBase-") ||
            subBase.startsWith("$videoBase ")

    /**
     * 将系统文件选择器返回的 content:// 字幕拷贝至应用缓存目录，
     * 保证 Media3 与 libmpv 均可通过真实文件路径读取且授权不会随页面退出失效。
     */
    fun importFromUri(context: Context, uri: Uri): SidecarSubtitle? {
        var target: File? = null
        return runCatching {
            val resolver = context.contentResolver
            var displayName: String? = null
            var declaredSize = -1L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) declaredSize = cursor.getLong(sizeIdx)
                        }
                    }
            }
            if (declaredSize > MAX_IMPORT_BYTES) return null

            val resolvedName = displayName?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "external.ass"
            val safeName = resolvedName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "external.ass" }
            val subDir = File(context.cacheDir, "subtitles").apply { mkdirs() }
            val outFile = File(subDir, "${System.currentTimeMillis()}_$safeName")
            target = outFile

            resolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_IMPORT_BYTES) error("subtitle too large")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return null

            if (outFile.length() <= 0L) {
                runCatching { outFile.delete() }
                return null
            }

            SidecarSubtitle(
                path = outFile.absolutePath,
                name = resolvedName,
                mimeType = mimeFor(resolvedName) ?: "text/x-ssa",
            )
        }.getOrElse {
            target?.let { f -> runCatching { f.delete() } }
            null
        }
    }
}
