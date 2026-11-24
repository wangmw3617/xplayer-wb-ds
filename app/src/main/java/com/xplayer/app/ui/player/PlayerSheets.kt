package com.xplayer.app.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.xplayer.app.player.engine.EngineType
import com.xplayer.app.R
import com.xplayer.app.data.datastore.AspectMode
import com.xplayer.app.data.datastore.SubtitlePrefs
import com.xplayer.app.data.datastore.labelRes
import com.xplayer.app.data.source.SidecarSubtitle
import com.xplayer.app.player.engine.TrackInfo
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.util.Formatters
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
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
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "external.ass"
            onImportSubtitle(
                SidecarSubtitle(
                    path = uri.toString(),
                    name = name,
                    mimeType = com.xplayer.app.data.scanner.SubtitleFinder.mimeFor(name) ?: "text/x-ssa",
                ),
            )
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.player_tracks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.player_track_audio),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (audioTracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.player_no_tracks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            audioTracks.forEach { track ->
                TrackRow(
                    title = track.label,
                    subtitle = listOfNotNull(
                        track.language?.uppercase(),
                        track.channels?.let { if (it >= 6) "${if (it == 8) "7.1" else "5.1"}" else null },
                        track.mimeType?.substringAfter('/')?.uppercase(),
                    ).joinToString(" · "),
                    selected = track.id == selectedAudio,
                    onClick = { onSelectAudio(track) },
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.player_track_subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TrackRow(
                title = stringResource(R.string.player_subtitle_off),
                subtitle = null,
                selected = selectedSubtitle == null,
                onClick = { onSelectSubtitle(null) },
            )
            subtitleTracks.forEach { track ->
                TrackRow(
                    title = track.label,
                    subtitle = listOfNotNull(
                        track.language?.uppercase(),
                        if (track.isExternal) "EXT" else "INTERNAL",
                    ).joinToString(" · "),
                    selected = track.id == selectedSubtitle,
                    onClick = { onSelectSubtitle(track) },
                )
            }
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Icon(
                    imageVector = XPlayerIcons.Subtitles,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.player_import_subtitle))
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.player_subtitle_delay),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${if (subtitleDelayMs >= 0) "+" else ""}${subtitleDelayMs} ms",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (supportsDelay) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-500L, -100L, 100L, 500L).forEach { step ->
                    TextButton(onClick = { onDelayChange(subtitleDelayMs + step) }) {
                        Text(if (step > 0) "+$step" else step.toString(), fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { onDelayChange(0L) }) {
                    Text(stringResource(R.string.common_zero), fontSize = 12.sp)
                }
            }
            if (!supportsDelay) {
                Text(
                    text = stringResource(R.string.player_switch_engine) + " → libmpv",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrackRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(current: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableFloatStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.player_speed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${Formatters.speed(draft)}×",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = draft,
                onValueChange = {
                    draft = (Math.round(it * 20f) / 20f)
                    onSpeed(draft)
                },
                valueRange = 0.25f..4f,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f).forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (preset == draft) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            )
                            .clickable {
                                draft = preset
                                onSpeed(preset)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = Formatters.speed(preset),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (preset == draft) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectSheet(current: AspectMode, onSelect: (AspectMode) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.player_aspect),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            AspectMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSelect(mode)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = XPlayerIcons.AspectRatio,
                        contentDescription = null,
                        tint = if (mode == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(mode.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mode == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleSheet(
    style: SubtitlePrefs,
    onChange: (SubtitlePrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.player_subtitle_style),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))

            Text(
                text = "${stringResource(R.string.settings_sub_font_size)} · ${style.fontScalePercent}%",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = style.fontScalePercent.toFloat(),
                onValueChange = { onChange(style.copy(fontScalePercent = it.toInt())) },
                valueRange = 50f..200f,
            )

            Text(
                text = stringResource(R.string.settings_sub_color),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFF80DEEA, 0xFFB9F6CA).forEach { argb ->
                    val selected = style.colorArgb == argb
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(argb.toInt()))
                            .clickable { onChange(style.copy(colorArgb = argb)) }
                            .padding(2.dp),
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_sub_shadow),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = style.shadow,
                    onCheckedChange = { onChange(style.copy(shadow = it)) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_sub_bg),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = style.backgroundBar,
                    onCheckedChange = { onChange(style.copy(backgroundBar = it)) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_sub_force_ass),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = style.forceAssOverride,
                    onCheckedChange = { onChange(style.copy(forceAssOverride = it)) },
                )
            }
            Text(
                text = "${stringResource(R.string.settings_sub_margin)} · ${style.bottomMarginDp} dp",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = style.bottomMarginDp.toFloat(),
                onValueChange = { onChange(style.copy(bottomMarginDp = it.toInt())) },
                valueRange = 0f..120f,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun fileLabel(path: String): String = File(path).name

/**
 * 播放页二级菜单：内核切换、画面与常用操作都收在这里，
 * 一级界面上只保留高频按钮，布局向 YouTube 靠，但配色/圆角跟随应用主题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    engine: EngineType,
    mpvAvailable: Boolean,
    aspectLabel: String,
    rotationMode: Int,
    onSwitchEngine: (EngineType) -> Unit,
    onAspect: () -> Unit,
    onSubtitleStyle: () -> Unit,
    onScreenshot: () -> Unit,
    onBackgroundAudio: () -> Unit,
    onPip: () -> Unit,
    onRotate: (Int) -> Unit,
    onLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.player_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )

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
                subtitle = if (mpvAvailable) {
                    stringResource(R.string.player_engine_mpv_desc)
                } else {
                    stringResource(R.string.player_engine_unavailable)
                },
                icon = XPlayerIcons.Memory,
                selected = engine == EngineType.MPV,
                enabled = mpvAvailable,
                onClick = { onSwitchEngine(EngineType.MPV) },
            )
            Text(
                text = stringResource(R.string.player_engine_switch_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
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
                icon = XPlayerIcons.RotateScreen,
                title = stringResource(R.string.player_orientation),
                subtitle = stringResource(
                    when (rotationMode) {
                        1 -> R.string.player_rotate_landscape
                        2 -> R.string.player_rotate_portrait
                        else -> R.string.player_rotate_auto
                    },
                ),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 1, 2).forEach { mode ->
                            val selected = rotationMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                    )
                                    .clickable { onRotate(mode) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        when (mode) {
                                            1 -> R.string.player_rotate_landscape
                                            2 -> R.string.player_rotate_portrait
                                            else -> R.string.player_rotate_auto
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                },
                onClick = {},
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlayerSheetSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun PlayerEngineRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onPrimary),
                )
            }
        }
    }
}

@Composable
private fun PlayerSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
