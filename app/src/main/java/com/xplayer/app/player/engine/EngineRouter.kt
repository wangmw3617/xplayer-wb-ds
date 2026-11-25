package com.xplayer.app.player.engine

import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.datastore.EnginePolicy
import com.xplayer.app.data.scanner.SubtitleFinder
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaSource

data class EngineDecision(
    val engine: EngineType,
    val assAutoRouted: Boolean = false,
)

/**
 * Media3 优先智能路由：
 * 1. 不可用或外部 content:// URI 统一走 Media3；
 * 2. 用户强制策略或单次记忆偏好优先；
 * 3. 已启用实际 GLSL 着色器或自定义 mpv.conf（非空）时走 libmpv；
 * 4. 检测到 ASS/SSA 特效字幕且开启自动切核时走 libmpv；
 * 5. 其余默认 Media3（硬件直通与杜比视界优先）。
 */
object EngineRouter {

    fun decide(
        settings: AppSettings,
        source: MediaSource,
        hasEmbeddedAss: Boolean,
        hasActiveShaders: Boolean,
        preferred: EngineType? = null,
    ): EngineDecision {
        if (!EngineAvailability.mpvAvailable || source is ContentUriSource) {
            return EngineDecision(EngineType.MEDIA3)
        }
        if (preferred != null) {
            return EngineDecision(preferred)
        }
        if (settings.engine.policy == EnginePolicy.MPV_ONLY) {
            return EngineDecision(EngineType.MPV)
        }
        if (settings.engine.policy == EnginePolicy.MEDIA3_ONLY) {
            return EngineDecision(EngineType.MEDIA3)
        }
        val hasCustomConf = settings.engine.customMpvConfEnabled && settings.engine.mpvConf.isNotBlank()
        if (hasActiveShaders || hasCustomConf) {
            return EngineDecision(EngineType.MPV)
        }
        val hasAss = hasEmbeddedAss ||
            (source is LocalFileSource && source.subtitles.any { SubtitleFinder.isAss(it.path) })
        if (hasAss && settings.engine.assAutoSwitch) {
            return EngineDecision(EngineType.MPV, assAutoRouted = true)
        }
        return EngineDecision(EngineType.MEDIA3)
    }
}
