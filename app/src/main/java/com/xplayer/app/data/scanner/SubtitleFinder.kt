package com.xplayer.app.data.scanner

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
}
