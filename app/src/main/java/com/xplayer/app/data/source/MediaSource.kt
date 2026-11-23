package com.xplayer.app.data.source

/** 外挂字幕（同目录自动发现或手动导入） */
data class SidecarSubtitle(
    val path: String,
    val name: String,
    val mimeType: String,
)

/**
 * 播放源抽象层：播放页与引擎只依赖该接口，
 * V2 的 WebDAV / SMB / Emby 只需新增实现即可复用整套播控链路。
 */
sealed interface MediaSource {
    /** 用于播放记录的稳定主键 */
    val key: String
    val displayName: String
    val isLocal: Boolean
    val folderPath: String?
}

data class LocalFileSource(
    val path: String,
    override val displayName: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val subtitles: List<SidecarSubtitle> = emptyList(),
    override val folderPath: String = "",
) : MediaSource {
    override val key: String get() = path
    override val isLocal: Boolean get() = true
}

data class NetworkUrlSource(
    val url: String,
    override val displayName: String,
    val headers: Map<String, String> = emptyMap(),
) : MediaSource {
    override val key: String get() = url
    override val isLocal: Boolean get() = false
    override val folderPath: String? get() = null
}
