package com.xplayer.app.data.datastore

import androidx.annotation.StringRes
import com.xplayer.app.R

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

enum class ColorSeed(val seed: Long) {
    OCEAN(0xFF2F6BFF),
    FOREST(0xFF2E7D55),
    SUNSET(0xFFE0663A),
    VIOLET(0xFF7A5AF8),
    ROSE(0xFFE0457B),
    SLATE(0xFF5B6573),
}

enum class EnginePolicy { MEDIA3_FIRST, MEDIA3_ONLY, MPV_ONLY }

enum class MpvHwDecoder(val value: String) {
    MEDIACODEC("mediacodec"),
    MEDIACODEC_COPY("mediacodec-copy"),
    SOFT("no"),
}

enum class BackgroundBehavior { PAUSE, AUDIO, PIP }

enum class VerticalGestureAction { BRIGHTNESS, VOLUME, DISABLED }

/** 手势可绑定动作，左右双击共用一套枚举 */
enum class GestureAction {
    SEEK_BACK,
    SEEK_FORWARD,
    PLAY_PAUSE,
    PREV,
    NEXT,
    MUTE,
    SCREENSHOT,
    ASPECT_TOGGLE,
    DISABLED,
}

enum class SingleTapAction { TOGGLE_CONTROLS, PLAY_PAUSE }

enum class ZoomMode { FREE, TOGGLE_ASPECT, DISABLED }

enum class EdgeDeadZone(val dp: Int) {
    NARROW(16),
    STANDARD(24),
    WIDE(36),
}

/** 滑满一屏对应的跳转时长 */
enum class SeekSpan(val seconds: Int, val ratioOfDuration: Boolean = false) {
    S30(30),
    S60(60),
    S90(90),
    S180(180),
    RATIO(0, true),
    ;

    fun resolveMillis(durationMs: Long): Long = when {
        ratioOfDuration -> (durationMs * 0.25f).toLong().coerceAtLeast(10_000L)
        else -> seconds * 1000L
    }
}

enum class AspectMode { FIT, CROP, STRETCH, RATIO_16_9, RATIO_4_3, ORIGINAL }

enum class RotationMode { AUTO, LANDSCAPE, PORTRAIT }

enum class ScreenshotFormat { PNG, JPEG }

data class AppearancePrefs(
    val dynamicColor: Boolean = true,
    val seed: ColorSeed = ColorSeed.OCEAN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

data class EnginePrefs(
    val policy: EnginePolicy = EnginePolicy.MEDIA3_FIRST,
    val assAutoSwitch: Boolean = true,
    val mpvHwDecoder: MpvHwDecoder = MpvHwDecoder.MEDIACODEC,
    val frameRateMatching: Boolean = true,
    val customMpvConfEnabled: Boolean = false,
    val mpvConf: String = DEFAULT_MPV_CONF,
    val shaderPipelineEnabled: Boolean = false,
) {
    companion object {
        const val DEFAULT_MPV_CONF = "# XPlayer · libmpv options\n# vo=gpu-next\ntone-mapping=bt.2446a\ndeband=yes\n"
    }
}

data class GesturePrefs(
    val leftAction: VerticalGestureAction = VerticalGestureAction.BRIGHTNESS,
    val rightAction: VerticalGestureAction = VerticalGestureAction.VOLUME,
    val swipeSensitivity: Float = 1f,
    val volumeBoost: Boolean = false,
    val horizontalSeekEnabled: Boolean = true,
    val seekSpan: SeekSpan = SeekSpan.S60,
    val preciseSeek: Boolean = false,
    val singleTapAction: SingleTapAction = SingleTapAction.TOGGLE_CONTROLS,
    val doubleLeftAction: GestureAction = GestureAction.SEEK_BACK,
    val doubleCenterAction: GestureAction = GestureAction.PLAY_PAUSE,
    val doubleRightAction: GestureAction = GestureAction.SEEK_FORWARD,
    val leftStepMs: Long = 10_000L,
    val rightStepMs: Long = 10_000L,
    val longPressEnabled: Boolean = true,
    val longPressSpeed: Float = 2.0f,
    val longPressDragSpeed: Boolean = true,
    val longPressDelayMs: Long = 300L,
    val zoomMode: ZoomMode = ZoomMode.FREE,
    val edgeDeadZone: EdgeDeadZone = EdgeDeadZone.STANDARD,
)

data class SubtitlePrefs(
    val fontScalePercent: Int = 100,
    val colorArgb: Long = 0xFFFFFFFF,
    val outlinePercent: Int = 100,
    val shadow: Boolean = true,
    val backgroundBar: Boolean = true,
    val bottomMarginDp: Int = 24,
    val forceAssOverride: Boolean = false,
)

data class PlaybackPrefs(
    val defaultSpeed: Float = 1f,
    val autoPlayNext: Boolean = true,
    val backgroundBehavior: BackgroundBehavior = BackgroundBehavior.AUDIO,
    val screenshotFormat: ScreenshotFormat = ScreenshotFormat.PNG,
)

data class MediaPrefs(
    val autoScanOnLaunch: Boolean = true,
)

data class AppSettings(
    val appearance: AppearancePrefs = AppearancePrefs(),
    val engine: EnginePrefs = EnginePrefs(),
    val gesture: GesturePrefs = GesturePrefs(),
    val subtitle: SubtitlePrefs = SubtitlePrefs(),
    val playback: PlaybackPrefs = PlaybackPrefs(),
    val media: MediaPrefs = MediaPrefs(),
)

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_dark_system
    ThemeMode.LIGHT -> R.string.settings_dark_light
    ThemeMode.DARK -> R.string.settings_dark_dark
    ThemeMode.AMOLED -> R.string.settings_dark_amoled
}

@StringRes
fun ColorSeed.labelRes(): Int = when (this) {
    ColorSeed.OCEAN -> R.string.settings_seed_ocean
    ColorSeed.FOREST -> R.string.settings_seed_forest
    ColorSeed.SUNSET -> R.string.settings_seed_sunset
    ColorSeed.VIOLET -> R.string.settings_seed_violet
    ColorSeed.ROSE -> R.string.settings_seed_rose
    ColorSeed.SLATE -> R.string.settings_seed_slate
}

@StringRes
fun EnginePolicy.labelRes(): Int = when (this) {
    EnginePolicy.MEDIA3_FIRST -> R.string.settings_engine_policy_media3_full
    EnginePolicy.MEDIA3_ONLY -> R.string.settings_engine_policy_media3_only_full
    EnginePolicy.MPV_ONLY -> R.string.settings_engine_policy_mpv_full
}

@StringRes
fun EnginePolicy.summaryRes(): Int = when (this) {
    EnginePolicy.MEDIA3_FIRST -> R.string.settings_engine_policy_media3
    EnginePolicy.MEDIA3_ONLY -> R.string.settings_engine_policy_media3_only
    EnginePolicy.MPV_ONLY -> R.string.settings_engine_policy_mpv
}

@StringRes
fun MpvHwDecoder.labelRes(): Int = when (this) {
    MpvHwDecoder.MEDIACODEC -> R.string.settings_mpv_decoder_mediacodec
    MpvHwDecoder.MEDIACODEC_COPY -> R.string.settings_mpv_decoder_copy
    MpvHwDecoder.SOFT -> R.string.settings_mpv_decoder_soft
}

@StringRes
fun VerticalGestureAction.labelRes(): Int = when (this) {
    VerticalGestureAction.BRIGHTNESS -> R.string.gesture_action_brightness
    VerticalGestureAction.VOLUME -> R.string.gesture_action_volume
    VerticalGestureAction.DISABLED -> R.string.gesture_action_disabled
}

@StringRes
fun GestureAction.labelRes(): Int = when (this) {
    GestureAction.SEEK_BACK -> R.string.action_seek_back
    GestureAction.SEEK_FORWARD -> R.string.action_seek_forward
    GestureAction.PLAY_PAUSE -> R.string.action_play_pause
    GestureAction.PREV -> R.string.action_prev
    GestureAction.NEXT -> R.string.action_next
    GestureAction.MUTE -> R.string.action_mute
    GestureAction.SCREENSHOT -> R.string.action_screenshot
    GestureAction.ASPECT_TOGGLE -> R.string.action_aspect
    GestureAction.DISABLED -> R.string.gesture_action_disabled
}

@StringRes
fun ZoomMode.labelRes(): Int = when (this) {
    ZoomMode.FREE -> R.string.gesture_zoom_free
    ZoomMode.TOGGLE_ASPECT -> R.string.gesture_zoom_toggle
    ZoomMode.DISABLED -> R.string.gesture_zoom_off
}

@StringRes
fun EdgeDeadZone.labelRes(): Int = when (this) {
    EdgeDeadZone.NARROW -> R.string.gesture_edge_narrow
    EdgeDeadZone.STANDARD -> R.string.gesture_edge_standard
    EdgeDeadZone.WIDE -> R.string.gesture_edge_wide
}

@StringRes
fun BackgroundBehavior.labelRes(): Int = when (this) {
    BackgroundBehavior.PAUSE -> R.string.settings_background_pause
    BackgroundBehavior.AUDIO -> R.string.settings_background_audio
    BackgroundBehavior.PIP -> R.string.settings_background_pip
}

@StringRes
fun AspectMode.labelRes(): Int = when (this) {
    AspectMode.FIT -> R.string.player_aspect_fit
    AspectMode.CROP -> R.string.player_aspect_crop
    AspectMode.STRETCH -> R.string.player_aspect_stretch
    AspectMode.RATIO_16_9 -> R.string.player_aspect_16_9
    AspectMode.RATIO_4_3 -> R.string.player_aspect_4_3
    AspectMode.ORIGINAL -> R.string.player_aspect_original
}
