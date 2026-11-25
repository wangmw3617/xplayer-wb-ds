package com.xplayer.app.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.datastore.AnimationLevel
import com.xplayer.app.data.datastore.BackgroundBehavior
import com.xplayer.app.data.datastore.BottomBarStyle
import com.xplayer.app.data.datastore.ColorSeed
import com.xplayer.app.data.datastore.DarkSurfaceMode
import com.xplayer.app.data.datastore.EdgeDeadZone
import com.xplayer.app.data.datastore.EnginePolicy
import com.xplayer.app.data.datastore.GestureAction
import com.xplayer.app.data.datastore.MpvHwDecoder
import com.xplayer.app.data.datastore.ScreenshotFormat
import com.xplayer.app.data.datastore.SeekSpan
import com.xplayer.app.data.datastore.SingleTapAction
import com.xplayer.app.data.datastore.ThemeMode
import com.xplayer.app.data.datastore.VerticalGestureAction
import com.xplayer.app.data.datastore.ZoomMode
import com.xplayer.app.data.datastore.labelRes
import com.xplayer.app.data.datastore.summaryRes
import com.xplayer.app.ui.components.ChevronTrailing
import com.xplayer.app.ui.components.EmptyState
import com.xplayer.app.ui.components.FrostedGlassSurface
import com.xplayer.app.ui.components.FrostedHeaderScrim
import com.xplayer.app.ui.components.InlinePickerRow
import com.xplayer.app.ui.components.PickerSettingRow
import com.xplayer.app.ui.components.SettingGroup
import com.xplayer.app.ui.components.SettingRow
import com.xplayer.app.ui.components.SettingSwitchRow
import com.xplayer.app.ui.components.SheetOption
import com.xplayer.app.ui.components.SliderSettingRow
import com.xplayer.app.ui.components.StandaloneSwitchCard
import com.xplayer.app.ui.components.frostedBackdropSource
import com.xplayer.app.ui.components.rememberFrostedBackdropState
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.util.Formatters

private const val REPO_URL = "https://github.com/wangmw3617/xplayer-wb-ds"

@Composable
fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val backdropState = rememberFrostedBackdropState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .frostedBackdropSource(backdropState)
                .background(MaterialTheme.colorScheme.background),
        ) {
            content(PaddingValues(top = statusBarTop + 68.dp, bottom = navBarBottom))
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
        ) {
            FrostedHeaderScrim(modifier = Modifier.matchParentSize())
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrostedGlassSurface(
                        backdropState = backdropState,
                        shape = CircleShape,
                        onClick = onBack,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (actionIcon != null && onAction != null) {
                        Spacer(Modifier.width(12.dp))
                        FrostedGlassSurface(
                            backdropState = backdropState,
                            shape = CircleShape,
                            onClick = onAction,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = actionDescription,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        snackbar?.let {
            SnackbarHost(
                hostState = it,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun ToggleSettingRow(
    titleRes: Int,
    descOnRes: Int,
    descOffRes: Int,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingSwitchRow(
        title = stringResource(titleRes),
        subtitle = stringResource(if (checked) descOnRes else descOffRes),
        icon = icon,
        checked = checked,
        enabled = enabled,
        showDivider = showDivider,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun <T> EnumPickerRow(
    titleRes: Int,
    icon: ImageVector,
    options: List<T>,
    selected: T,
    labelResOf: (T) -> Int,
    descResOf: ((T) -> Int)? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onSelect: (T) -> Unit,
) {
    val sheetOptions = options.map { option ->
        SheetOption(
            label = stringResource(labelResOf(option)),
            value = option,
            description = descResOf?.let { stringResource(it(option)) },
        )
    }
    PickerSettingRow(
        title = stringResource(titleRes),
        subtitle = stringResource(descResOf?.invoke(selected) ?: labelResOf(selected)),
        icon = icon,
        options = sheetOptions,
        selected = selected,
        enabled = enabled,
        showDivider = showDivider,
        onSelect = onSelect,
    )
}

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val appearance = settings.appearance
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isDarkTheme = when (appearance.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_group_appearance),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "dynamic_color", contentType = "switch") {
                StandaloneSwitchCard(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(
                        if (appearance.dynamicColor && dynamicSupported) {
                            R.string.settings_dynamic_color_on
                        } else {
                            R.string.settings_dynamic_color_off
                        },
                    ),
                    icon = XPlayerIcons.Storage,
                    checked = appearance.dynamicColor && dynamicSupported,
                    enabled = dynamicSupported,
                    onCheckedChange = { enabled ->
                        viewModel.update {
                            it.copy(appearance = it.appearance.copy(dynamicColor = enabled))
                        }
                    },
                )
            }

            item(key = "theme_group", contentType = "group") {
                SettingGroup(title = stringResource(R.string.settings_dark)) {
                    InlinePickerRow(
                        title = stringResource(R.string.settings_dark),
                        selectedLabel = stringResource(appearance.themeMode.labelRes()),
                        icon = XPlayerIcons.Brightness,
                        options = ThemeMode.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = appearance.themeMode,
                        onSelect = { mode ->
                            viewModel.update {
                                it.copy(appearance = it.appearance.copy(themeMode = mode))
                            }
                        },
                    )
                    InlinePickerRow(
                        title = stringResource(R.string.settings_dark_surface),
                        selectedLabel = stringResource(appearance.darkSurfaceMode.labelRes()),
                        icon = XPlayerIcons.Palette,
                        options = DarkSurfaceMode.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = appearance.darkSurfaceMode,
                        enabled = isDarkTheme,
                        showDivider = false,
                        onSelect = { darkMode ->
                            viewModel.update {
                                it.copy(appearance = it.appearance.copy(darkSurfaceMode = darkMode))
                            }
                        },
                    )
                }
            }

            item(key = "color_seed", contentType = "palette") {
                SettingGroup(title = stringResource(R.string.settings_group_color)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        ColorSeed.entries.chunked(5).forEachIndexed { rowIdx, rowSeeds ->
                            if (rowIdx > 0) Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowSeeds.forEach { seed ->
                                    val selected = !appearance.dynamicColor && appearance.seed == seed
                                    ColorSeedSwatch(
                                        seed = seed,
                                        selected = selected,
                                        onClick = {
                                            viewModel.update {
                                                it.copy(
                                                    appearance = it.appearance.copy(
                                                        seed = seed,
                                                        dynamicColor = false,
                                                    ),
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(5 - rowSeeds.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bar_anim", contentType = "group") {
                SettingGroup(title = stringResource(R.string.settings_group_bar_anim)) {
                    InlinePickerRow(
                        title = stringResource(R.string.settings_bottom_bar),
                        selectedLabel = stringResource(appearance.bottomBarStyle.labelRes()),
                        icon = XPlayerIcons.Grid,
                        options = BottomBarStyle.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = appearance.bottomBarStyle,
                        onSelect = { style ->
                            viewModel.update {
                                it.copy(appearance = it.appearance.copy(bottomBarStyle = style))
                            }
                        },
                    )
                    InlinePickerRow(
                        title = stringResource(R.string.settings_anim),
                        selectedLabel = stringResource(appearance.animationLevel.labelRes()),
                        icon = XPlayerIcons.Bolt,
                        options = AnimationLevel.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = appearance.animationLevel,
                        showDivider = false,
                        onSelect = { level ->
                            viewModel.update {
                                it.copy(appearance = it.appearance.copy(animationLevel = level))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSeedSwatch(
    seed: ColorSeed,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatchColor = Color(seed.seed)
    val checkTint = if (swatchColor.luminance() > 0.34f) Color(0xFF181A1F) else Color.White
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .then(
                    if (selected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = checkTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(seed.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EngineSettingsScreen(
    onBack: () -> Unit,
    onOpenCodec: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsState()
    val engine = settings.engine

    SettingsPageScaffold(
        title = stringResource(R.string.settings_group_engine),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "ass_auto", contentType = "switch") {
                StandaloneSwitchCard(
                    title = stringResource(R.string.settings_ass_auto),
                    subtitle = stringResource(
                        if (engine.assAutoSwitch) {
                            R.string.settings_ass_auto_desc_on
                        } else {
                            R.string.settings_ass_auto_desc_off
                        },
                    ),
                    icon = XPlayerIcons.Subtitles,
                    checked = engine.assAutoSwitch,
                    enabled = engine.policy == EnginePolicy.MEDIA3_FIRST,
                    onCheckedChange = { enabled ->
                        viewModel.update { it.copy(engine = it.engine.copy(assAutoSwitch = enabled)) }
                    },
                )
            }

            item(key = "engine_group", contentType = "group") {
                SettingGroup(title = stringResource(R.string.settings_group_engine)) {
                    EnumPickerRow(
                        titleRes = R.string.settings_engine_policy,
                        icon = XPlayerIcons.Memory,
                        options = EnginePolicy.entries,
                        selected = engine.policy,
                        labelResOf = EnginePolicy::labelRes,
                        descResOf = EnginePolicy::summaryRes,
                        onSelect = { policy ->
                            viewModel.update { it.copy(engine = it.engine.copy(policy = policy)) }
                        },
                    )
                    EnumPickerRow(
                        titleRes = R.string.settings_mpv_decoder,
                        icon = XPlayerIcons.Tune,
                        options = MpvHwDecoder.entries,
                        selected = engine.mpvHwDecoder,
                        labelResOf = MpvHwDecoder::labelRes,
                        enabled = engine.policy != EnginePolicy.MEDIA3_ONLY,
                        onSelect = { decoder ->
                            viewModel.update { it.copy(engine = it.engine.copy(mpvHwDecoder = decoder)) }
                        },
                    )
                    ToggleSettingRow(
                        titleRes = R.string.settings_frame_rate,
                        descOnRes = R.string.settings_frame_rate_desc_on,
                        descOffRes = R.string.settings_frame_rate_desc_off,
                        icon = XPlayerIcons.Speed,
                        checked = engine.frameRateMatching,
                        enabled = engine.policy != EnginePolicy.MPV_ONLY,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(engine = it.engine.copy(frameRateMatching = enabled)) }
                        },
                    )
                    SettingRow(
                        title = stringResource(R.string.codec_title),
                        subtitle = stringResource(R.string.home_codec_desc),
                        icon = XPlayerIcons.Memory,
                        onClick = onOpenCodec,
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }
        }
    }
}

@Composable
fun GestureSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsState()
    val gesture = settings.gesture
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var resetConfirmVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_group_gesture),
        onBack = onBack,
        snackbar = snackbar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "vertical_group", contentType = "group") {
                SettingGroup(title = stringResource(R.string.gesture_section_vertical)) {
                    EnumPickerRow(
                        titleRes = R.string.gesture_left_action,
                        icon = XPlayerIcons.Brightness,
                        options = VerticalGestureAction.entries,
                        selected = gesture.leftAction,
                        labelResOf = VerticalGestureAction::labelRes,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(leftAction = action)) }
                        },
                    )
                    EnumPickerRow(
                        titleRes = R.string.gesture_right_action,
                        icon = XPlayerIcons.Volume,
                        options = VerticalGestureAction.entries,
                        selected = gesture.rightAction,
                        labelResOf = VerticalGestureAction::labelRes,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(rightAction = action)) }
                        },
                    )
                    SliderSettingRow(
                        title = stringResource(R.string.gesture_sensitivity),
                        subtitle = stringResource(R.string.gesture_sensitivity_desc, Formatters.speed(gesture.swipeSensitivity)),
                        icon = XPlayerIcons.Tune,
                        value = gesture.swipeSensitivity,
                        range = 0.5f..2f,
                        steps = 5,
                        format = { context.getString(R.string.settings_speed_x, Formatters.speed(it)) },
                        onChange = { value ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(swipeSensitivity = value)) }
                        },
                    )
                    ToggleSettingRow(
                        titleRes = R.string.settings_volume_boost,
                        descOnRes = R.string.settings_volume_boost_desc_on,
                        descOffRes = R.string.settings_volume_boost_desc_off,
                        icon = XPlayerIcons.Volume,
                        checked = gesture.volumeBoost,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(volumeBoost = enabled)) }
                        },
                    )
                    EnumPickerRow(
                        titleRes = R.string.gesture_edge,
                        icon = XPlayerIcons.AspectRatio,
                        options = EdgeDeadZone.entries,
                        selected = gesture.edgeDeadZone,
                        labelResOf = EdgeDeadZone::labelRes,
                        showDivider = false,
                        onSelect = { zone ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(edgeDeadZone = zone)) }
                        },
                    )
                }
            }

            item(key = "seek_group", contentType = "group") {
                SettingGroup(title = stringResource(R.string.gesture_section_seek)) {
                    ToggleSettingRow(
                        titleRes = R.string.gesture_horizontal,
                        descOnRes = R.string.gesture_horizontal_desc_on,
                        descOffRes = R.string.gesture_horizontal_desc_off,
                        icon = XPlayerIcons.Speed,
                        checked = gesture.horizontalSeekEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(horizontalSeekEnabled = enabled)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_seek_span),
                        subtitle = seekSpanLabel(gesture.seekSpan),
                        icon = XPlayerIcons.Tune,
                        options = SeekSpan.entries.map {
                            SheetOption(seekSpanLabel(it), it)
                        },
                        selected = gesture.seekSpan,
                        enabled = gesture.horizontalSeekEnabled,
                        onSelect = { span ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(seekSpan = span)) }
                        },
                    )
                    ToggleSettingRow(
                        titleRes = R.string.gesture_seek_precise,
                        descOnRes = R.string.gesture_seek_precise_on,
                        descOffRes = R.string.gesture_seek_precise_off,
                        icon = XPlayerIcons.Speed,
                        checked = gesture.preciseSeek,
                        enabled = gesture.horizontalSeekEnabled,
                        showDivider = false,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(preciseSeek = enabled)) }
                        },
                    )
                }
            }

            item(key = "taps_group", contentType = "group") {
                SettingGroup(title = stringResource(R.string.gesture_section_taps)) {
                    EnumPickerRow(
                        titleRes = R.string.gesture_single_tap,
                        icon = XPlayerIcons.Zoom,
                        options = SingleTapAction.entries,
                        selected = gesture.singleTapAction,
                        labelResOf = SingleTapAction::labelRes,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(singleTapAction = action)) }
                        },
                    )
                    EnumPickerRow(
                        titleRes = R.string.gesture_double_left,
                        icon = XPlayerIcons.SkipPrevious,
                        options = GestureAction.entries,
                        selected = gesture.doubleLeftAction,
                        labelResOf = GestureAction::labelRes,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(doubleLeftAction = action)) }
                        },
                    )
                    EnumPickerRow(
                        titleRes = R.string.gesture_double_center,
                        icon = XPlayerIcons.Pause,
                        options = GestureAction.entries,
                        selected = gesture.doubleCenterAction,
                        labelResOf = GestureAction::labelRes,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(doubleCenterAction = action)) }
                        },
                    )
                    EnumPickerRow(
                        titleRes = R.string.gesture_double_right,
                        icon = XPlayerIcons.SkipNext,
                        options = GestureAction.entries,
                        selected = gesture.doubleRightAction,
                        labelResOf = GestureAction::labelRes,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(doubleRightAction = action)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_step),
                        subtitle = stringResource(R.string.settings_seconds_short, (gesture.leftStepMs / 1000L).toInt()),
                        icon = XPlayerIcons.Tune,
                        options = listOf(5_000L, 10_000L, 15_000L, 30_000L).map { ms ->
                            SheetOption(stringResource(R.string.settings_seconds_short, (ms / 1000L).toInt()), ms)
                        },
                        selected = gesture.leftStepMs,
                        showDivider = false,
                        onSelect = { step ->
                            viewModel.update {
                                it.copy(gesture = it.gesture.copy(leftStepMs = step, rightStepMs = step))
                            }
                        },
                    )
                }
            }

            item(key = "other_group", contentType = "group") {
                SettingGroup(title = stringResource(R.string.gesture_section_other)) {
                    ToggleSettingRow(
                        titleRes = R.string.gesture_long_press,
                        descOnRes = R.string.gesture_long_press_desc_on,
                        descOffRes = R.string.gesture_long_press_desc_off,
                        icon = XPlayerIcons.Bolt,
                        checked = gesture.longPressEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(longPressEnabled = enabled)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_long_speed),
                        subtitle = stringResource(R.string.settings_speed_x, Formatters.speed(gesture.longPressSpeed)),
                        icon = XPlayerIcons.Speed,
                        options = listOf(1.5f, 2f, 2.5f, 3f, 4f).map { spd ->
                            SheetOption(stringResource(R.string.settings_speed_x, Formatters.speed(spd)), spd)
                        },
                        selected = gesture.longPressSpeed,
                        enabled = gesture.longPressEnabled,
                        onSelect = { speed ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(longPressSpeed = speed)) }
                        },
                    )
                    ToggleSettingRow(
                        titleRes = R.string.gesture_long_drag,
                        descOnRes = R.string.gesture_long_drag_desc_on,
                        descOffRes = R.string.gesture_long_drag_desc_off,
                        icon = XPlayerIcons.DragHandle,
                        checked = gesture.longPressDragSpeed,
                        enabled = gesture.longPressEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(longPressDragSpeed = enabled)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_long_delay),
                        subtitle = stringResource(R.string.settings_ms_short, gesture.longPressDelayMs.toInt()),
                        icon = XPlayerIcons.Tune,
                        options = listOf(300L, 500L).map { delay ->
                            SheetOption(stringResource(R.string.settings_ms_short, delay.toInt()), delay)
                        },
                        selected = gesture.longPressDelayMs,
                        enabled = gesture.longPressEnabled,
                        onSelect = { delay ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(longPressDelayMs = delay)) }
                        },
                    )
                    EnumPickerRow(
                        titleRes = R.string.gesture_zoom,
                        icon = XPlayerIcons.Zoom,
                        options = ZoomMode.entries,
                        selected = gesture.zoomMode,
                        labelResOf = ZoomMode::labelRes,
                        onSelect = { mode ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(zoomMode = mode)) }
                        },
                    )
                    SettingRow(
                        title = stringResource(R.string.gesture_reset),
                        subtitle = null,
                        icon = Icons.Default.Delete,
                        onClick = { resetConfirmVisible = true },
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }
        }
    }

    if (resetConfirmVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmVisible = false },
            title = { Text(stringResource(R.string.gesture_reset)) },
            text = { Text(stringResource(R.string.gesture_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfirmVisible = false
                        viewModel.resetGestures()
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmVisible = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun seekSpanLabel(span: SeekSpan): String = if (span.ratioOfDuration) {
    stringResource(R.string.settings_ratio_25_short)
} else {
    stringResource(R.string.settings_seconds_short, span.seconds)
}

@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val playback = settings.playback

    SettingsPageScaffold(title = stringResource(R.string.settings_group_playback), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "playback_group", contentType = "group") {
                SettingGroup(title = stringResource(R.string.settings_group_playback)) {
                    PickerSettingRow(
                        title = stringResource(R.string.settings_default_speed),
                        subtitle = stringResource(R.string.settings_speed_x, Formatters.speed(playback.defaultSpeed)),
                        icon = XPlayerIcons.Speed,
                        options = listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { spd ->
                            SheetOption(stringResource(R.string.settings_speed_x, Formatters.speed(spd)), spd)
                        },
                        selected = playback.defaultSpeed,
                        onSelect = { speed ->
                            viewModel.update { it.copy(playback = it.playback.copy(defaultSpeed = speed)) }
                        },
                    )
                    ToggleSettingRow(
                        titleRes = R.string.settings_auto_next,
                        descOnRes = R.string.settings_auto_next_desc_on,
                        descOffRes = R.string.settings_auto_next_desc_off,
                        icon = XPlayerIcons.SkipNext,
                        checked = playback.autoPlayNext,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(playback = it.playback.copy(autoPlayNext = enabled)) }
                        },
                    )
                    EnumPickerRow(
                        titleRes = R.string.settings_background,
                        icon = XPlayerIcons.Headphones,
                        options = BackgroundBehavior.entries,
                        selected = playback.backgroundBehavior,
                        labelResOf = BackgroundBehavior::labelRes,
                        onSelect = { behavior ->
                            viewModel.update { it.copy(playback = it.playback.copy(backgroundBehavior = behavior)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.settings_screenshot_format),
                        subtitle = playback.screenshotFormat.name,
                        icon = XPlayerIcons.Camera,
                        options = ScreenshotFormat.entries.map { fmt ->
                            SheetOption(fmt.name, fmt)
                        },
                        selected = playback.screenshotFormat,
                        showDivider = false,
                        onSelect = { fmt ->
                            viewModel.update { it.copy(playback = it.playback.copy(screenshotFormat = fmt)) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SubtitleSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val subtitle = settings.subtitle
    val colorOptions = listOf(
        0xFFFFFFFFL to R.string.settings_sub_color_white,
        0xFFFFEB3BL to R.string.settings_sub_color_yellow,
        0xFF80DEEAL to R.string.settings_sub_color_cyan,
        0xFFB9F6CAL to R.string.settings_sub_color_green,
    )
    val selectedColorRes = colorOptions.firstOrNull { it.first == subtitle.colorArgb }?.second
        ?: R.string.settings_sub_color_white

    SettingsPageScaffold(title = stringResource(R.string.player_subtitle_style), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "subtitle_group", contentType = "group") {
                SettingGroup(title = stringResource(R.string.player_subtitle_style)) {
                    SliderSettingRow(
                        title = stringResource(R.string.settings_sub_font_size),
                        subtitle = stringResource(R.string.settings_sub_font_size_desc, subtitle.fontScalePercent),
                        icon = XPlayerIcons.Subtitles,
                        value = subtitle.fontScalePercent.toFloat(),
                        range = 50f..200f,
                        steps = 14,
                        format = { "${it.toInt()}%" },
                        onChange = { value ->
                            viewModel.update {
                                it.copy(subtitle = it.subtitle.copy(fontScalePercent = value.toInt()))
                            }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.settings_sub_color),
                        subtitle = stringResource(selectedColorRes),
                        icon = XPlayerIcons.Palette,
                        options = colorOptions.map { (argb, resId) ->
                            SheetOption(stringResource(resId), argb)
                        },
                        selected = subtitle.colorArgb,
                        onSelect = { color ->
                            viewModel.update { it.copy(subtitle = it.subtitle.copy(colorArgb = color)) }
                        },
                    )
                    SliderSettingRow(
                        title = stringResource(R.string.settings_sub_outline),
                        subtitle = stringResource(R.string.settings_sub_outline_desc, subtitle.outlinePercent),
                        icon = XPlayerIcons.Tune,
                        value = subtitle.outlinePercent.toFloat(),
                        range = 0f..200f,
                        steps = 7,
                        format = { "${it.toInt()}%" },
                        onChange = { value ->
                            viewModel.update {
                                it.copy(subtitle = it.subtitle.copy(outlinePercent = value.toInt()))
                            }
                        },
                    )
                    ToggleSettingRow(
                        titleRes = R.string.settings_sub_shadow,
                        descOnRes = R.string.settings_sub_shadow_desc_on,
                        descOffRes = R.string.settings_sub_shadow_desc_off,
                        icon = XPlayerIcons.Brightness,
                        checked = subtitle.shadow,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(subtitle = it.subtitle.copy(shadow = enabled)) }
                        },
                    )
                    ToggleSettingRow(
                        titleRes = R.string.settings_sub_bg,
                        descOnRes = R.string.settings_sub_bg_desc_on,
                        descOffRes = R.string.settings_sub_bg_desc_off,
                        icon = XPlayerIcons.AspectRatio,
                        checked = subtitle.backgroundBar,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(subtitle = it.subtitle.copy(backgroundBar = enabled)) }
                        },
                    )
                    SliderSettingRow(
                        title = stringResource(R.string.settings_sub_margin),
                        subtitle = stringResource(R.string.settings_sub_margin_desc, subtitle.bottomMarginDp),
                        icon = XPlayerIcons.AspectRatio,
                        value = subtitle.bottomMarginDp.toFloat(),
                        range = 0f..120f,
                        steps = 11,
                        format = { "${it.toInt()} dp" },
                        onChange = { value ->
                            viewModel.update {
                                it.copy(subtitle = it.subtitle.copy(bottomMarginDp = value.toInt()))
                            }
                        },
                    )
                    ToggleSettingRow(
                        titleRes = R.string.settings_sub_force_ass,
                        descOnRes = R.string.settings_sub_force_ass_desc_on,
                        descOffRes = R.string.settings_sub_force_ass_desc_off,
                        icon = XPlayerIcons.Subtitles,
                        checked = subtitle.forceAssOverride,
                        showDivider = false,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(subtitle = it.subtitle.copy(forceAssOverride = enabled)) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun HiddenFoldersScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val hidden by viewModel.hiddenFolders.collectAsState()
    SettingsPageScaffold(title = stringResource(R.string.folders_hidden), onBack = onBack) { padding ->
        if (hidden.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.folders_hidden_empty),
                icon = XPlayerIcons.Folder,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "hidden_group", contentType = "group") {
                    SettingGroup(title = stringResource(R.string.folders_hidden)) {
                        hidden.forEachIndexed { idx, entry ->
                            val folderName = entry.folderPath.trimEnd('/').substringAfterLast('/').ifBlank { "/" }
                            SettingRow(
                                title = folderName,
                                subtitle = entry.folderPath,
                                icon = Icons.Default.Lock,
                                onClick = { viewModel.unhideFolder(entry.folderPath) },
                                trailing = {
                                    TextButton(onClick = { viewModel.unhideFolder(entry.folderPath) }) {
                                        Text(stringResource(R.string.folders_unhide))
                                    }
                                },
                                showDivider = idx < hidden.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val versionName by viewModel.versionName.collectAsState()
    val uriHandler = LocalUriHandler.current

    SettingsPageScaffold(title = stringResource(R.string.settings_group_about), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "about_group", contentType = "group") {
                SettingGroup(title = "XPlayer") {
                    SettingRow(
                        title = stringResource(R.string.settings_version),
                        subtitle = stringResource(R.string.settings_version_desc, versionName),
                        icon = Icons.Default.Star,
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_engines),
                        subtitle = stringResource(R.string.settings_engines_desc),
                        icon = XPlayerIcons.Memory,
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_source),
                        subtitle = stringResource(R.string.settings_source_desc),
                        icon = Icons.Default.Info,
                        onClick = { runCatching { uriHandler.openUri(REPO_URL) } },
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }
        }
    }
}
