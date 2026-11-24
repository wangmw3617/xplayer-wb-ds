package com.xplayer.app.player.engine

import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.datastore.EnginePolicy
import com.xplayer.app.data.scanner.SubtitleFinder
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaSource

enum class EngineReason {
    POLICY_MEDIA3,
    ASS_AUTO_MPV,
    SHADER_MPV,
    FORCED_MEDIA3,
    FORCED_MPV,
    MANUAL,
    FALLBACK,
    EXTERNAL_URI,
}

data class EngineDecision(val engine: EngineType, val reason: EngineReason)

/**
 * Media3 优先智能路由：
 * 1. 强制策略优先；
 * 2. ASS/SSA 自动切 mpv（可关）；
 * 3. 启用 GLSL 着色器或自定义 mpv.conf 时走 mpv；
 * 4. 其余默认 Media3（保障杜比视界 / HDR 硬件直通）。
 */
class EngineRouter {

    fun decide(
        settings: AppSettings,
        source: MediaSource,
        hasEmbeddedAss: Boolean,
        preferred: EngineType? = null,
    ): EngineDecision {
        val mpvReady = EngineAvailability.mpvAvailable
        if (!mpvReady) {
            return EngineDecision(EngineType.MEDIA3, EngineReason.FALLBACK)
        }
        // 外部应用传入的 content:// 只能交给 Media3（mpv 读不了 SAF 文档）
        if (source is ContentUriSource) {
            return EngineDecision(EngineType.MEDIA3, EngineReason.EXTERNAL_URI)
        }
        if (preferred != null) {
            return EngineDecision(preferred, EngineReason.MANUAL)
        }
        if (settings.engine.policy == EnginePolicy.MPV_ONLY) {
            return EngineDecision(EngineType.MPV, EngineReason.FORCED_MPV)
        }
        if (settings.engine.policy == EnginePolicy.MEDIA3_ONLY) {
            return EngineDecision(EngineType.MEDIA3, EngineReason.FORCED_MEDIA3)
        }
        if (settings.engine.shaderPipelineEnabled || settings.engine.customMpvConfEnabled) {
            return EngineDecision(EngineType.MPV, EngineReason.SHADER_MPV)
        }
        val hasAss = hasEmbeddedAss ||
            (source is LocalFileSource && source.subtitles.any { SubtitleFinder.isAss(it.path) })
        if (hasAss && settings.engine.assAutoSwitch) {
            return EngineDecision(EngineType.MPV, EngineReason.ASS_AUTO_MPV)
        }
        return EngineDecision(EngineType.MEDIA3, EngineReason.POLICY_MEDIA3)
    }
}
