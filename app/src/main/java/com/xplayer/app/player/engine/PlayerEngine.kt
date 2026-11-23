package com.xplayer.app.player.engine

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import com.xplayer.app.data.datastore.AspectMode
import com.xplayer.app.data.datastore.SubtitlePrefs
import com.xplayer.app.data.source.MediaSource
import com.xplayer.app.data.source.SidecarSubtitle
import kotlinx.coroutines.flow.StateFlow

enum class EngineType { MEDIA3, MPV }

data class TrackInfo(
    val id: String,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
    val channels: Int? = null,
    val isExternal: Boolean = false,
    /** Media3: Tracks.Group 索引；mpv: 轨道 id */
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
)

data class PlayerUiState(
    val engine: EngineType = EngineType.MEDIA3,
    val ready: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val speed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val selectedAudioId: String? = null,
    val selectedSubtitleId: String? = null,
    val subtitleDelayMs: Long = 0L,
    val supportsSubtitleDelay: Boolean = false,
    val ended: Boolean = false,
    val error: String? = null,
)

/**
 * 双内核统一接口：Media3 与 libmpv 共用同一套播控界面、手势层与弹窗菜单。
 * 上层只依赖状态流与这些方法，不感知具体实现。
 */
interface PlayerEngine {

    val type: EngineType

    val state: StateFlow<PlayerUiState>

    /** 渲染视图：Media3 返回 PlayerView 容器，libmpv 返回 BaseMPVView */
    fun createVideoView(context: Context): View

    fun prepare(source: MediaSource, startPositionMs: Long, subtitleStyle: SubtitlePrefs, speed: Float)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float)

    fun selectAudioTrack(track: TrackInfo)

    fun selectSubtitleTrack(track: TrackInfo?)

    fun addExternalSubtitle(subtitle: SidecarSubtitle)

    fun setSubtitleDelay(delayMs: Long)

    fun applySubtitleStyle(style: SubtitlePrefs)

    fun setAspectRatio(mode: AspectMode)

    fun setPreciseSeek(enabled: Boolean)

    fun setVolume(volume: Float)

    suspend fun captureFrame(): Bitmap?

    fun release()
}

/** 供 UI 判断某内核是否可用（例如 libmpv 加载失败时应隐藏热切换胶囊） */
object EngineAvailability {
    @Volatile
    var mpvAvailable: Boolean = true
        internal set
}
