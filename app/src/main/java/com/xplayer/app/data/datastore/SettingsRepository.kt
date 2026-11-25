package com.xplayer.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "xplayer_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs.toSettings()
            val next = transform(current)
            prefs.writeDiff(current, next)
        }
    }

    suspend fun resetGestures() {
        update { it.copy(gesture = GesturePrefs()) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val rawTheme = this[Keys.THEME_MODE]
        val legacyAmoled = rawTheme == "AMOLED"
        val resolvedTheme = if (legacyAmoled) ThemeMode.DARK else enumOf(rawTheme, ThemeMode.SYSTEM)
        val resolvedDarkSurface = if (legacyAmoled && this[Keys.DARK_SURFACE_MODE] == null) {
            DarkSurfaceMode.PURE_BLACK
        } else {
            enumOf(this[Keys.DARK_SURFACE_MODE], DarkSurfaceMode.STANDARD)
        }
        return AppSettings(
            appearance = AppearancePrefs(
                dynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
                seed = enumOf(this[Keys.SEED], ColorSeed.OCEAN),
                themeMode = resolvedTheme,
                darkSurfaceMode = resolvedDarkSurface,
                bottomBarStyle = enumOf(this[Keys.BOTTOM_BAR_STYLE], BottomBarStyle.LIQUID_GLASS),
                animationLevel = enumOf(this[Keys.ANIMATION_LEVEL], AnimationLevel.STANDARD),
            ),
        engine = EnginePrefs(
            policy = enumOf(this[Keys.ENGINE_POLICY], EnginePolicy.MEDIA3_FIRST),
            assAutoSwitch = this[Keys.ASS_AUTO_SWITCH] ?: true,
            mpvHwDecoder = enumOf(this[Keys.MPV_HWDEC], MpvHwDecoder.MEDIACODEC),
            frameRateMatching = this[Keys.FRAME_RATE] ?: true,
            customMpvConfEnabled = this[Keys.MPV_CONF_ENABLED] ?: false,
            mpvConf = this[Keys.MPV_CONF] ?: EnginePrefs.DEFAULT_MPV_CONF,
            shaderPipelineEnabled = this[Keys.SHADER_ENABLED] ?: false,
        ),
        gesture = GesturePrefs(
            leftAction = enumOf(this[Keys.LEFT_ACTION], VerticalGestureAction.BRIGHTNESS),
            rightAction = enumOf(this[Keys.RIGHT_ACTION], VerticalGestureAction.VOLUME),
            swipeSensitivity = (this[Keys.SWIPE_SENSITIVITY] ?: 1f).takeIf { it.isFinite() }?.coerceIn(0.4f, 2.5f) ?: 1f,
            volumeBoost = this[Keys.VOLUME_BOOST] ?: false,
            horizontalSeekEnabled = this[Keys.HORIZONTAL_SEEK] ?: true,
            seekSpan = enumOf(this[Keys.SEEK_SPAN], SeekSpan.S60),
            preciseSeek = this[Keys.PRECISE_SEEK] ?: false,
            singleTapAction = enumOf(this[Keys.SINGLE_TAP], SingleTapAction.TOGGLE_CONTROLS),
            doubleLeftAction = enumOf(this[Keys.DOUBLE_LEFT], GestureAction.SEEK_BACK),
            doubleCenterAction = enumOf(this[Keys.DOUBLE_CENTER], GestureAction.PLAY_PAUSE),
            doubleRightAction = enumOf(this[Keys.DOUBLE_RIGHT], GestureAction.SEEK_FORWARD),
            leftStepMs = (this[Keys.LEFT_STEP] ?: 10_000L).coerceIn(1_000L, 120_000L),
            rightStepMs = (this[Keys.RIGHT_STEP] ?: 10_000L).coerceIn(1_000L, 120_000L),
            longPressEnabled = this[Keys.LONG_PRESS] ?: true,
            longPressSpeed = (this[Keys.LONG_PRESS_SPEED] ?: 2.0f).takeIf { it.isFinite() }?.coerceIn(1.25f, 4.0f) ?: 2.0f,
            longPressDragSpeed = this[Keys.LONG_PRESS_DRAG] ?: true,
            longPressDelayMs = (this[Keys.LONG_PRESS_DELAY] ?: 300L).coerceIn(150L, 1_500L),
            zoomMode = enumOf(this[Keys.ZOOM_MODE], ZoomMode.FREE),
            edgeDeadZone = enumOf(this[Keys.EDGE_ZONE], EdgeDeadZone.STANDARD),
        ),
        subtitle = SubtitlePrefs(
            fontScalePercent = (this[Keys.SUB_FONT] ?: 100).coerceIn(50, 220),
            colorArgb = this[Keys.SUB_COLOR] ?: 0xFFFFFFFF,
            outlinePercent = (this[Keys.SUB_OUTLINE] ?: 100).coerceIn(0, 200),
            shadow = this[Keys.SUB_SHADOW] ?: true,
            backgroundBar = this[Keys.SUB_BAR] ?: true,
            bottomMarginDp = (this[Keys.SUB_MARGIN] ?: 24).coerceIn(0, 120),
            forceAssOverride = this[Keys.SUB_FORCE_ASS] ?: false,
        ),
        playback = PlaybackPrefs(
            defaultSpeed = (this[Keys.DEFAULT_SPEED] ?: 1f).takeIf { it.isFinite() }?.coerceIn(0.25f, 4.0f) ?: 1f,
            autoPlayNext = this[Keys.AUTO_NEXT] ?: true,
            backgroundBehavior = enumOf(this[Keys.BACKGROUND_BEHAVIOR], BackgroundBehavior.AUDIO),
            screenshotFormat = enumOf(this[Keys.SCREENSHOT_FORMAT], ScreenshotFormat.PNG),
        ),
        media = MediaPrefs(
            autoScanOnLaunch = this[Keys.AUTO_SCAN] ?: true,
        ),
        )
    }

    private fun MutablePreferences.writeDiff(current: AppSettings, next: AppSettings) {
        if (next.appearance != current.appearance) {
            val a = next.appearance
            this[Keys.DYNAMIC_COLOR] = a.dynamicColor
            this[Keys.SEED] = a.seed.name
            this[Keys.THEME_MODE] = a.themeMode.name
            this[Keys.DARK_SURFACE_MODE] = a.darkSurfaceMode.name
            this[Keys.BOTTOM_BAR_STYLE] = a.bottomBarStyle.name
            this[Keys.ANIMATION_LEVEL] = a.animationLevel.name
        }
        if (next.engine != current.engine) {
            val e = next.engine
            this[Keys.ENGINE_POLICY] = e.policy.name
            this[Keys.ASS_AUTO_SWITCH] = e.assAutoSwitch
            this[Keys.MPV_HWDEC] = e.mpvHwDecoder.name
            this[Keys.FRAME_RATE] = e.frameRateMatching
            this[Keys.MPV_CONF_ENABLED] = e.customMpvConfEnabled
            this[Keys.MPV_CONF] = e.mpvConf
            this[Keys.SHADER_ENABLED] = e.shaderPipelineEnabled
        }
        if (next.gesture != current.gesture) {
            val g = next.gesture
            this[Keys.LEFT_ACTION] = g.leftAction.name
            this[Keys.RIGHT_ACTION] = g.rightAction.name
            this[Keys.SWIPE_SENSITIVITY] = g.swipeSensitivity
            this[Keys.VOLUME_BOOST] = g.volumeBoost
            this[Keys.HORIZONTAL_SEEK] = g.horizontalSeekEnabled
            this[Keys.SEEK_SPAN] = g.seekSpan.name
            this[Keys.PRECISE_SEEK] = g.preciseSeek
            this[Keys.SINGLE_TAP] = g.singleTapAction.name
            this[Keys.DOUBLE_LEFT] = g.doubleLeftAction.name
            this[Keys.DOUBLE_CENTER] = g.doubleCenterAction.name
            this[Keys.DOUBLE_RIGHT] = g.doubleRightAction.name
            this[Keys.LEFT_STEP] = g.leftStepMs
            this[Keys.RIGHT_STEP] = g.rightStepMs
            this[Keys.LONG_PRESS] = g.longPressEnabled
            this[Keys.LONG_PRESS_SPEED] = g.longPressSpeed
            this[Keys.LONG_PRESS_DRAG] = g.longPressDragSpeed
            this[Keys.LONG_PRESS_DELAY] = g.longPressDelayMs
            this[Keys.ZOOM_MODE] = g.zoomMode.name
            this[Keys.EDGE_ZONE] = g.edgeDeadZone.name
        }
        if (next.subtitle != current.subtitle) {
            val s = next.subtitle
            this[Keys.SUB_FONT] = s.fontScalePercent
            this[Keys.SUB_COLOR] = s.colorArgb
            this[Keys.SUB_OUTLINE] = s.outlinePercent
            this[Keys.SUB_SHADOW] = s.shadow
            this[Keys.SUB_BAR] = s.backgroundBar
            this[Keys.SUB_MARGIN] = s.bottomMarginDp
            this[Keys.SUB_FORCE_ASS] = s.forceAssOverride
        }
        if (next.playback != current.playback) {
            val p = next.playback
            this[Keys.DEFAULT_SPEED] = p.defaultSpeed
            this[Keys.AUTO_NEXT] = p.autoPlayNext
            this[Keys.BACKGROUND_BEHAVIOR] = p.backgroundBehavior.name
            this[Keys.SCREENSHOT_FORMAT] = p.screenshotFormat.name
        }
        if (next.media != current.media) {
            this[Keys.AUTO_SCAN] = next.media.autoScanOnLaunch
        }
    }

    private inline fun <reified T : Enum<T>> enumOf(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private object Keys {
        val DYNAMIC_COLOR = booleanPreferencesKey("appearance.dynamic_color")
        val SEED = stringPreferencesKey("appearance.seed")
        val THEME_MODE = stringPreferencesKey("appearance.theme_mode")
        val DARK_SURFACE_MODE = stringPreferencesKey("appearance.dark_surface_mode")
        val BOTTOM_BAR_STYLE = stringPreferencesKey("appearance.bottom_bar_style")
        val ANIMATION_LEVEL = stringPreferencesKey("appearance.animation_level")

        val ENGINE_POLICY = stringPreferencesKey("engine.policy")
        val ASS_AUTO_SWITCH = booleanPreferencesKey("engine.ass_auto_switch")
        val MPV_HWDEC = stringPreferencesKey("engine.mpv_hwdec")
        val FRAME_RATE = booleanPreferencesKey("engine.frame_rate_matching")
        val MPV_CONF_ENABLED = booleanPreferencesKey("engine.mpv_conf_enabled")
        val MPV_CONF = stringPreferencesKey("engine.mpv_conf")
        val SHADER_ENABLED = booleanPreferencesKey("engine.shaders_enabled")

        val LEFT_ACTION = stringPreferencesKey("gesture.left_action")
        val RIGHT_ACTION = stringPreferencesKey("gesture.right_action")
        val SWIPE_SENSITIVITY = floatPreferencesKey("gesture.swipe_sensitivity")
        val VOLUME_BOOST = booleanPreferencesKey("gesture.volume_boost")
        val HORIZONTAL_SEEK = booleanPreferencesKey("gesture.horizontal_seek")
        val SEEK_SPAN = stringPreferencesKey("gesture.seek_span")
        val PRECISE_SEEK = booleanPreferencesKey("gesture.precise_seek")
        val SINGLE_TAP = stringPreferencesKey("gesture.single_tap")
        val DOUBLE_LEFT = stringPreferencesKey("gesture.double_left")
        val DOUBLE_CENTER = stringPreferencesKey("gesture.double_center")
        val DOUBLE_RIGHT = stringPreferencesKey("gesture.double_right")
        val LEFT_STEP = longPreferencesKey("gesture.left_step")
        val RIGHT_STEP = longPreferencesKey("gesture.right_step")
        val LONG_PRESS = booleanPreferencesKey("gesture.long_press")
        val LONG_PRESS_SPEED = floatPreferencesKey("gesture.long_press_speed")
        val LONG_PRESS_DRAG = booleanPreferencesKey("gesture.long_press_drag")
        val LONG_PRESS_DELAY = longPreferencesKey("gesture.long_press_delay")
        val ZOOM_MODE = stringPreferencesKey("gesture.zoom_mode")
        val EDGE_ZONE = stringPreferencesKey("gesture.edge_zone")

        val SUB_FONT = intPreferencesKey("subtitle.font_scale")
        val SUB_COLOR = longPreferencesKey("subtitle.color")
        val SUB_OUTLINE = intPreferencesKey("subtitle.outline")
        val SUB_SHADOW = booleanPreferencesKey("subtitle.shadow")
        val SUB_BAR = booleanPreferencesKey("subtitle.background_bar")
        val SUB_MARGIN = intPreferencesKey("subtitle.bottom_margin")
        val SUB_FORCE_ASS = booleanPreferencesKey("subtitle.force_ass_override")

        val DEFAULT_SPEED = floatPreferencesKey("playback.default_speed")
        val AUTO_NEXT = booleanPreferencesKey("playback.auto_next")
        val BACKGROUND_BEHAVIOR = stringPreferencesKey("playback.background_behavior")
        val SCREENSHOT_FORMAT = stringPreferencesKey("playback.screenshot_format")

        val AUTO_SCAN = booleanPreferencesKey("media.auto_scan")
    }
}
