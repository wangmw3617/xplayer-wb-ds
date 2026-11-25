package com.xplayer.app.ui.player

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xplayer.app.R
import com.xplayer.app.data.datastore.AspectMode
import com.xplayer.app.data.datastore.RotationMode
import com.xplayer.app.data.datastore.SubtitlePrefs
import com.xplayer.app.data.datastore.labelRes
import com.xplayer.app.data.scanner.SubtitleFinder
import com.xplayer.app.data.source.SidecarSubtitle
import com.xplayer.app.player.engine.EngineType
import com.xplayer.app.player.engine.TrackInfo
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** 字幕样式预设颜色，与设置详情页共享 */
val SUBTITLE_COLOR_PRESETS: List<Pair<Long, Int>> = listOf(
    0xFFFFFFFF to R.string.settings_sub_color_white,
    0xFFFFEB3B to R.string.settings_sub_color_yellow,
    0xFF80DEEA to R.string.settings_sub_color_cyan,
    0xFFB9F6CA to R.string.settings_sub_color_green,
)

@Composable
private fun panelSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
)

/**
 * 播放页半透明面板容器：
 * - 横屏：靠右停靠，占约 2/5 屏宽（290dp..420dp），左侧留出 3/5 画面；
 * - 竖屏：靠底停靠，占约半屏高（48%），上方留出画面；
 */
@Composable
private fun PlayerTranslucentPanel(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp

    val panelShape = if (isLandscape) {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    }

    val panelModifier = if (isLandscape) {
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.40f)
            .widthIn(min = 290.dp, max = 420.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.48f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Box(
            modifier = panelModifier
                .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .clip(panelShape)
                .background(Color(0xCC12141B))
                .border(1.dp, Color.White.copy(alpha = 0.14f), panelShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isLandscape) {
                                Modifier
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                                    .displayCutoutPadding()
                            } else {
                                Modifier.navigationBarsPadding()
                            },
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        content = content,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TracksSheet(
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    selectedAudio: String?,
    selectedSubtitle: String?,
    subtitleDelayMs: Long,
    supportsDelay: Boolean,
    onSelectAudio: (TrackInfo) -> Unit,
    onSelectSubtitle: (TrackInfo?) -> Unit,
    onDelayChange: (Long) -> Unit,
    onImportSubtitle: (SidecarSubtitle) -> Unit,
    onImportFailed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    SubtitleFinder.importFromUri(context, uri)
                }
                if (imported != null) onImportSubtitle(imported) else onImportFailed()
            }
        }
    }

    PlayerTranslucentPanel(
        title = stringResource(R.string.player_tracks),
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.player_track_audio),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (audioTracks.isEmpty()) {
            Text(
                text = stringResource(R.string.player_no_tracks),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        val monoLabel = stringResource(R.string.media_channel_mono)
        val stereoLabel = stringResource(R.string.media_channel_stereo)
        audioTracks.forEach { track ->
            val chLabel = track.channels?.let { ch ->
                when (ch) {
                    1 -> monoLabel
                    2 -> stereoLabel
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> stringResource(R.string.media_channel_count, ch)
                }
            }
            val codec = Formatters.codecLabel(track.mimeType) ?: track.mimeType?.substringAfter('/')?.uppercase(Locale.US)
            PanelSelectableRow(
                title = track.label,
                subtitle = listOfNotNull(
                    track.language?.uppercase(Locale.US),
                    chLabel,
                    codec,
                ).joinToString(" · "),
                selected = track.id == selectedAudio,
                onClick = { onSelectAudio(track) },
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.player_track_subtitle),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        PanelSelectableRow(
            title = stringResource(R.string.player_subtitle_off),
            subtitle = null,
            selected = selectedSubtitle == null,
            onClick = { onSelectSubtitle(null) },
        )
        val extTag = stringResource(R.string.player_track_ext)
        val intTag = stringResource(R.string.player_track_internal)
        subtitleTracks.forEach { track ->
            PanelSelectableRow(
                title = track.label,
                subtitle = listOfNotNull(
                    track.language?.uppercase(Locale.US),
                    if (track.isExternal) extTag else intTag,
                ).joinToString(" · "),
                selected = track.id == selectedSubtitle,
                onClick = { onSelectSubtitle(track) },
            )
        }
        TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
            Icon(
                imageVector = XPlayerIcons.Subtitles,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.player_import_subtitle),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.player_subtitle_delay),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = if (supportsDelay) 1f else 0.55f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.player_delay_value, subtitleDelayMs),
                style = MaterialTheme.typography.labelLarge,
                color = if (supportsDelay) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.4f)
                },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(-500L, -100L, 100L, 500L).forEach { step ->
                TextButton(
                    enabled = supportsDelay,
                    onClick = { onDelayChange(subtitleDelayMs + step) },
                ) {
                    Text(
                        text = if (step > 0) "+$step" else step.toString(),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = if (supportsDelay) 0.88f else 0.35f),
                    )
                }
            }
            TextButton(
                enabled = supportsDelay && subtitleDelayMs != 0L,
                onClick = { onDelayChange(0L) },
            ) {
                Text(
                    text = stringResource(R.string.common_zero),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (supportsDelay && subtitleDelayMs != 0L) 1f else 0.35f,
                    ),
                )
            }
        }
        if (!supportsDelay) {
            Text(
                text = stringResource(R.string.player_delay_unsupported),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PanelSelectableRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
        if (selected) {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SpeedSheet(current: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(current) { mutableFloatStateOf(current) }
    PlayerTranslucentPanel(
        title = "${stringResource(R.string.player_speed)} · ${stringResource(R.string.settings_speed_x, Formatters.speed(draft))}",
        onDismiss = onDismiss,
    ) {
        Slider(
            value = draft,
            onValueChange = { raw ->
                val quantized = (raw * 20f).roundToInt() / 20f
                if (quantized != draft) {
                    draft = quantized
                }
            },
            onValueChangeFinished = { onSpeed(draft) },
            valueRange = 0.25f..4f,
            colors = panelSliderColors(),
            modifier = Modifier.padding(vertical = 8.dp),
        )
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f).chunked(4).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { preset ->
                    val selected = preset == draft
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                },
                            )
                            .clickable {
                                draft = preset
                                onSpeed(preset)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_speed_x, Formatters.speed(preset)),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AspectSheet(current: AspectMode, onSelect: (AspectMode) -> Unit, onDismiss: () -> Unit) {
    PlayerTranslucentPanel(
        title = stringResource(R.string.player_aspect),
        onDismiss = onDismiss,
    ) {
        AspectMode.entries.forEach { mode ->
            PanelSelectableRow(
                title = stringResource(mode.labelRes()),
                icon = XPlayerIcons.AspectRatio,
                selected = mode == current,
                onClick = {
                    onSelect(mode)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
fun SubtitleStyleSheet(
    style: SubtitlePrefs,
    onChange: (SubtitlePrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(style) { mutableStateOf(style) }
    val sliderColors = panelSliderColors()

    PlayerTranslucentPanel(
        title = stringResource(R.string.player_subtitle_style),
        onDismiss = onDismiss,
    ) {
        Text(
            text = "${stringResource(R.string.settings_sub_font_size)} · ${draft.fontScalePercent}%",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Slider(
            value = draft.fontScalePercent.toFloat(),
            onValueChange = { draft = draft.copy(fontScalePercent = it.roundToInt()) },
            onValueChangeFinished = { onChange(draft) },
            valueRange = 50f..220f,
            colors = sliderColors,
        )

        Text(
            text = "${stringResource(R.string.settings_sub_outline)} · ${draft.outlinePercent}%",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Slider(
            value = draft.outlinePercent.toFloat(),
            onValueChange = { draft = draft.copy(outlinePercent = it.roundToInt()) },
            onValueChangeFinished = { onChange(draft) },
            valueRange = 0f..200f,
            colors = sliderColors,
        )

        Text(
            text = stringResource(R.string.settings_sub_color),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SUBTITLE_COLOR_PRESETS.forEach { (argb, _) ->
                val selected = draft.colorArgb == argb
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(argb.toInt()))
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable {
                            draft = draft.copy(colorArgb = argb)
                            onChange(draft)
                        },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PanelSwitchRow(
            label = stringResource(R.string.settings_sub_shadow),
            checked = draft.shadow,
            onCheckedChange = {
                draft = draft.copy(shadow = it)
                onChange(draft)
            },
        )
        PanelSwitchRow(
            label = stringResource(R.string.settings_sub_bg),
            checked = draft.backgroundBar,
            onCheckedChange = {
                draft = draft.copy(backgroundBar = it)
                onChange(draft)
            },
        )
        PanelSwitchRow(
            label = stringResource(R.string.settings_sub_force_ass),
            checked = draft.forceAssOverride,
            onCheckedChange = {
                draft = draft.copy(forceAssOverride = it)
                onChange(draft)
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${stringResource(R.string.settings_sub_margin)} · ${draft.bottomMarginDp} dp",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Slider(
            value = draft.bottomMarginDp.toFloat(),
            onValueChange = { draft = draft.copy(bottomMarginDp = it.roundToInt()) },
            onValueChangeFinished = { onChange(draft) },
            valueRange = 0f..120f,
            colors = sliderColors,
        )
    }
}

@Composable
private fun PanelSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.22f),
            ),
        )
    }
}

/**
 * 播放页设置：横屏占右侧约 2/5 屏宽、竖屏占底部约半屏的半透明面板。
 */
@Composable
fun PlayerSettingsSheet(
    engine: EngineType,
    mpvAvailable: Boolean,
    mpvBlockedByUri: Boolean = false,
    aspectLabel: String,
    rotationMode: RotationMode,
    onSwitchEngine: (EngineType) -> Unit,
    onAspect: () -> Unit,
    onSubtitleStyle: () -> Unit,
    onScreenshot: () -> Unit,
    onBackgroundAudio: () -> Unit,
    onPip: () -> Unit,
    onRotate: (RotationMode) -> Unit,
    onLock: () -> Unit,
    onMediaInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerTranslucentPanel(
        title = stringResource(R.string.player_settings),
        onDismiss = onDismiss,
    ) {
        PlayerSheetSectionTitle(stringResource(R.string.player_settings_engine))
        PlayerEngineRow(
            title = "Media3",
            subtitle = stringResource(R.string.player_engine_media3_desc),
            icon = XPlayerIcons.Video,
            selected = engine == EngineType.MEDIA3,
            enabled = true,
            onClick = { onSwitchEngine(EngineType.MEDIA3) },
        )
        PlayerEngineRow(
            title = "libmpv",
            subtitle = when {
                !mpvAvailable -> stringResource(R.string.player_engine_unavailable)
                mpvBlockedByUri -> stringResource(R.string.player_engine_uri_media3_only)
                else -> stringResource(R.string.player_engine_mpv_desc)
            },
            icon = XPlayerIcons.Memory,
            selected = engine == EngineType.MPV,
            enabled = mpvAvailable && !mpvBlockedByUri,
            onClick = { onSwitchEngine(EngineType.MPV) },
        )
        Text(
            text = stringResource(R.string.player_engine_switch_hint),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.56f),
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )

        PlayerSheetSectionTitle(stringResource(R.string.player_section_picture))
        PlayerSheetRow(
            icon = XPlayerIcons.AspectRatio,
            title = stringResource(R.string.player_aspect),
            subtitle = aspectLabel,
            onClick = onAspect,
        )
        PlayerSheetRow(
            icon = XPlayerIcons.Subtitles,
            title = stringResource(R.string.player_subtitle_style),
            onClick = onSubtitleStyle,
        )
        PlayerSheetRow(
            icon = Icons.Default.Info,
            title = stringResource(R.string.action_media_info),
            onClick = onMediaInfo,
        )
        PlayerSheetRow(
            icon = XPlayerIcons.RotateScreen,
            title = stringResource(R.string.player_orientation),
            subtitle = stringResource(rotationMode.labelRes()),
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    RotationMode.entries.forEach { mode ->
                        val selected = rotationMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White.copy(alpha = 0.14f)
                                    },
                                )
                                .clickable { onRotate(mode) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(mode.labelRes()),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    Color.White.copy(alpha = 0.85f)
                                },
                            )
                        }
                    }
                }
            },
            onClick = null,
        )

        PlayerSheetSectionTitle(stringResource(R.string.player_section_actions))
        PlayerSheetRow(
            icon = XPlayerIcons.Camera,
            title = stringResource(R.string.player_screenshot),
            onClick = onScreenshot,
        )
        PlayerSheetRow(
            icon = XPlayerIcons.Headphones,
            title = stringResource(R.string.player_background_audio),
            onClick = onBackgroundAudio,
        )
        PlayerSheetRow(
            icon = XPlayerIcons.PictureInPicture,
            title = stringResource(R.string.player_pip),
            onClick = onPip,
        )
        PlayerSheetRow(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.player_lock),
            onClick = onLock,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PlayerSheetSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PlayerEngineRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White.copy(alpha = if (enabled) 0.78f else 0.35f)
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    enabled -> Color.White
                    else -> Color.White.copy(alpha = 0.4f)
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = if (enabled) 0.65f else 0.35f),
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary),
                )
            }
        }
    }
}

@Composable
private fun PlayerSheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.52f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
