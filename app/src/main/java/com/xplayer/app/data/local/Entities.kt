package com.xplayer.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** 动态范围标识，用于列表徽章与路由决策 */
enum class HdrType {
    NONE,
    HDR10,
    HDR10_PLUS,
    DOLBY_VISION,
    HLG,
}

enum class SourceType { LOCAL, NETWORK }

class Converters {
    @TypeConverter
    fun hdrToString(value: HdrType?): String = (value ?: HdrType.NONE).name

    @TypeConverter
    fun stringToHdr(value: String?): HdrType = runCatching {
        HdrType.valueOf(value ?: HdrType.NONE.name)
    }.getOrDefault(HdrType.NONE)

    @TypeConverter
    fun sourceToString(value: SourceType?): String = (value ?: SourceType.LOCAL).name

    @TypeConverter
    fun stringToSource(value: String?): SourceType = runCatching {
        SourceType.valueOf(value ?: SourceType.LOCAL.name)
    }.getOrDefault(SourceType.LOCAL)
}

@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["path"], unique = true, name = "index_videos_path"),
        Index(value = ["folderPath"], name = "index_videos_folderPath"),
        Index(value = ["dateModified"], name = "index_videos_dateModified"),
    ],
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val folderPath: String,
    val title: String,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String? = null,
    val hdrType: HdrType = HdrType.NONE,
    val hasAssSubtitle: Boolean = false,
    val thumbnailPath: String? = null,
    val bitrate: Long = 0,
    val frameRate: Float = 0f,
)

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["mediaId", "sourceType"], unique = true, name = "index_playback_history_mediaId_sourceType"),
        Index(value = ["lastPlayedAt"], name = "index_playback_history_lastPlayedAt"),
    ],
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 本地为绝对路径，网络为 URL */
    val mediaId: String,
    val sourceType: SourceType = SourceType.LOCAL,
    val title: String = "",
    val lastPositionMs: Long = 0,
    val durationMs: Long = 0,
    val lastPlayedAt: Long = 0,
    /** 记忆该片源上次手动切换的内核：MEDIA3 / MPV */
    val preferredEngine: String? = null,
    val subtitleDelayMs: Long = 0,
    val audioTrackId: String? = null,
    val subtitleTrackId: String? = null,
    val thumbnailPath: String? = null,
    val folderPath: String? = null,
    val completed: Boolean = false,
    val customHeaders: String? = null,
) {
    val progress: Float
        get() = if (durationMs <= 0) 0f else (lastPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

@Entity(tableName = "folder_blacklist")
data class FolderBlacklistEntity(
    @PrimaryKey val folderPath: String,
    val addedAt: Long = 0,
)

@Entity(
    tableName = "stream_urls",
    indices = [
        Index(value = ["url"], unique = true, name = "index_stream_urls_url"),
        Index(value = ["lastPlayedAt"], name = "index_stream_urls_lastPlayedAt"),
    ],
)
data class StreamUrlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val customTitle: String? = null,
    /** JSON 形式的自定义 HTTP Headers，可为空 */
    val headersJson: String? = null,
    val lastPlayedAt: Long = 0,
)

@Entity(
    tableName = "mpv_shaders",
    indices = [
        Index(value = ["orderIndex"], name = "index_mpv_shaders_orderIndex"),
    ],
)
data class MpvShaderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val enabled: Boolean = true,
    val orderIndex: Int = 0,
)
