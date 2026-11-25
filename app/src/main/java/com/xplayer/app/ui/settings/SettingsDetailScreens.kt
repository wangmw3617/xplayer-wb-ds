package com.xplayer.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.xplayer.app.data.datastore.SeekSpan
import com.xplayer.app.data.datastore.SingleTapAction
import com.xplayer.app.data.datastore.ThemeMode
import com.xplayer.app.data.datastore.VerticalGestureAction
import com.xplayer.app.data.datastore.ZoomMode
import com.xplayer.app.data.datastore.labelRes
import com.xplayer.app.data.datastore.summaryRes
import com.xplayer.app.ui.components.ChevronTrailing
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
fun AppearanceSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val appearance = settings.appearance

    SettingsPageScaffold(
        title = stringResource(R.string.settings_group_appearance),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = 40.dp),
        ) {
            item {
                StandaloneSwitchCard(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(
                        if (appearance.dynamicColor) {
                            R.string.settings_dynamic_color_on
                        } else {
                            R.string.settings_dynamic_color_off
                        },
                    ),
                    icon = XPlayerIcons.Storage,
                    checked = appearance.dynamicColor,
                    onCheckedChange = { enabled ->
                        viewModel.update {
                            it.copy(appearance = it.appearance.copy(dynamicColor = enabled))
                        }
                    },
                )
            }

            item {
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
                        showDivider = false,
                        onSelect = { darkMode ->
                            viewModel.update {
                                it.copy(appearance = it.appearance.copy(darkSurfaceMode = darkMode))
                            }
                        },
                    )
                }
            }

            item {
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

            item {
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
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
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
                    tint = Color.White,
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
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = 40.dp),
        ) {
            item {
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
                    onCheckedChange = { enabled ->
                        viewModel.update { it.copy(engine = it.engine.copy(assAutoSwitch = enabled)) }
                    },
                )
            }

            item {
                SettingGroup(title = stringResource(R.string.settings_group_engine)) {
                    PickerSettingRow(
                        title = stringResource(R.string.settings_engine_policy),
                        subtitle = stringResource(engine.policy.summaryRes()),
                        icon = XPlayerIcons.Memory,
                        options = EnginePolicy.entries.map {
                            SheetOption(stringResource(it.labelRes()), it, stringResource(it.summaryRes()))
                        },
                        selected = engine.policy,
                        onSelect = { policy ->
                            viewModel.update { it.copy(engine = it.engine.copy(policy = policy)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.settings_mpv_decoder),
                        subtitle = stringResource(engine.mpvHwDecoder.labelRes()),
                        icon = XPlayerIcons.Tune,
                        options = MpvHwDecoder.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = engine.mpvHwDecoder,
                        onSelect = { decoder ->
                            viewModel.update { it.copy(engine = it.engine.copy(mpvHwDecoder = decoder)) }
                        },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_frame_rate),
                        subtitle = stringResource(
                            if (engine.frameRateMatching) {
                                R.string.settings_frame_rate_desc_on
                            } else {
                                R.string.settings_frame_rate_desc_off
                            },
                        ),
                        icon = XPlayerIcons.Speed,
                        checked = engine.frameRateMatching,
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
                bottom = 40.dp,
            ),
        ) {
            item {
                SettingGroup(title = stringResource(R.string.gesture_left_action)) {
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_left_action),
                        subtitle = stringResource(gesture.leftAction.labelRes()),
                        icon = XPlayerIcons.Brightness,
                        options = VerticalGestureAction.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = gesture.leftAction,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(leftAction = action)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_right_action),
                        subtitle = stringResource(gesture.rightAction.labelRes()),
                        icon = XPlayerIcons.Volume,
                        options = VerticalGestureAction.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = gesture.rightAction,
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
                        format = { "${Formatters.speed(it)}×" },
                        onChange = { value ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(swipeSensitivity = value)) }
                        },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_volume_boost),
                        subtitle = stringResource(
                            if (gesture.volumeBoost) {
                                R.string.settings_volume_boost_desc_on
                            } else {
                                R.string.settings_volume_boost_desc_off
                            },
                        ),
                        icon = XPlayerIcons.Volume,
                        checked = gesture.volumeBoost,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(volumeBoost = enabled)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_edge),
                        subtitle = stringResource(gesture.edgeDeadZone.labelRes()),
                        icon = XPlayerIcons.AspectRatio,
                        options = EdgeDeadZone.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = gesture.edgeDeadZone,
                        onSelect = { zone ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(edgeDeadZone = zone)) }
                        },
                    )
                    SettingRow(
                        title = stringResource(R.string.gesture_reset),
                        subtitle = null,
                        icon = Icons.Default.Delete,
                        onClick = viewModel::resetGestures,
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.gesture_horizontal)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.gesture_horizontal),
                        subtitle = stringResource(
                            if (gesture.horizontalSeekEnabled) {
                                R.string.gesture_horizontal_desc_on
                            } else {
                                R.string.gesture_horizontal_desc_off
                            },
                        ),
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
                        onSelect = { span ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(seekSpan = span)) }
                        },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.gesture_seek_precise),
                        subtitle = stringResource(
                            if (gesture.preciseSeek) {
                                R.string.gesture_seek_precise_on
                            } else {
                                R.string.gesture_seek_precise_off
                            },
                        ),
                        icon = XPlayerIcons.Speed,
                        checked = gesture.preciseSeek,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(preciseSeek = enabled)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_single_tap),
                        subtitle = stringResource(
                            if (gesture.singleTapAction == SingleTapAction.TOGGLE_CONTROLS) {
                                R.string.gesture_tap_toggle
                            } else {
                                R.string.gesture_tap_play
                            },
                        ),
                        icon = XPlayerIcons.Zoom,
                        options = SingleTapAction.entries.map {
                            SheetOption(
                                stringResource(
                                    if (it == SingleTapAction.TOGGLE_CONTROLS) {
                                        R.string.gesture_tap_toggle
                                    } else {
                                        R.string.gesture_tap_play
                                    },
                                ),
                                it,
                            )
                        },
                        selected = gesture.singleTapAction,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(singleTapAction = action)) }
                        },
                        enabled = true,
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.gesture_double_left)) {
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_double_left),
                        subtitle = stringResource(gesture.doubleLeftAction.labelRes()),
                        icon = XPlayerIcons.SkipPrevious,
                        options = GestureAction.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = gesture.doubleLeftAction,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(doubleLeftAction = action)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_double_center),
                        subtitle = stringResource(gesture.doubleCenterAction.labelRes()),
                        icon = XPlayerIcons.Pause,
                        options = GestureAction.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = gesture.doubleCenterAction,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(doubleCenterAction = action)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_double_right),
                        subtitle = stringResource(gesture.doubleRightAction.labelRes()),
                        icon = XPlayerIcons.SkipNext,
                        options = GestureAction.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = gesture.doubleRightAction,
                        onSelect = { action ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(doubleRightAction = action)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_step),
                        subtitle = "${gesture.leftStepMs / 1000}s",
                        icon = XPlayerIcons.Tune,
                        options = listOf(5_000L, 10_000L, 15_000L, 30_000L).map {
                            SheetOption("${it / 1000}s", it)
                        },
                        selected = gesture.leftStepMs,
                        onSelect = { step ->
                            viewModel.update {
                                it.copy(gesture = it.gesture.copy(leftStepMs = step, rightStepMs = step))
                            }
                        },
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.gesture_long_press)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.gesture_long_press),
                        subtitle = stringResource(
                            if (gesture.longPressEnabled) {
                                R.string.gesture_long_press_desc_on
                            } else {
                                R.string.gesture_long_press_desc_off
                            },
                        ),
                        icon = XPlayerIcons.Bolt,
                        checked = gesture.longPressEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(longPressEnabled = enabled)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_long_speed),
                        subtitle = "${Formatters.speed(gesture.longPressSpeed)}×",
                        icon = XPlayerIcons.Speed,
                        options = listOf(1.5f, 2f, 2.5f, 3f, 4f).map {
                            SheetOption("${Formatters.speed(it)}×", it)
                        },
                        selected = gesture.longPressSpeed,
                        onSelect = { speed ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(longPressSpeed = speed)) }
                        },
                        enabled = gesture.longPressEnabled,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.gesture_long_drag),
                        subtitle = stringResource(
                            if (gesture.longPressDragSpeed) {
                                R.string.gesture_long_drag_desc_on
                            } else {
                                R.string.gesture_long_drag_desc_off
                            },
                        ),
                        icon = XPlayerIcons.DragHandle,
                        checked = gesture.longPressDragSpeed,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(longPressDragSpeed = enabled)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_long_delay),
                        subtitle = "${gesture.longPressDelayMs} ms",
                        icon = XPlayerIcons.Tune,
                        options = listOf(300L, 500L).map { SheetOption("$it ms", it) },
                        selected = gesture.longPressDelayMs,
                        onSelect = { delay ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(longPressDelayMs = delay)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.gesture_zoom),
                        subtitle = stringResource(gesture.zoomMode.labelRes()),
                        icon = XPlayerIcons.Zoom,
                        options = ZoomMode.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = gesture.zoomMode,
                        onSelect = { mode ->
                            viewModel.update { it.copy(gesture = it.gesture.copy(zoomMode = mode)) }
                        },
                        enabled = true,
                    )
                }
            }
        }
    }
}

private fun seekSpanLabel(span: SeekSpan): String = if (span.ratioOfDuration) {
    "25%"
} else {
    "${span.seconds}s"
}

@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val playback = settings.playback

    SettingsPageScaffold(title = stringResource(R.string.settings_group_playback), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 40.dp),
        ) {
            item {
                SettingGroup(title = stringResource(R.string.settings_group_playback)) {
                    PickerSettingRow(
                        title = stringResource(R.string.settings_default_speed),
                        subtitle = "${Formatters.speed(playback.defaultSpeed)}×",
                        icon = XPlayerIcons.Speed,
                        options = listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map {
                            SheetOption("${Formatters.speed(it)}×", it)
                        },
                        selected = playback.defaultSpeed,
                        onSelect = { speed ->
                            viewModel.update { it.copy(playback = it.playback.copy(defaultSpeed = speed)) }
                        },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_auto_next),
                        subtitle = stringResource(
                            if (playback.autoPlayNext) {
                                R.string.settings_auto_next_desc_on
                            } else {
                                R.string.settings_auto_next_desc_off
                            },
                        ),
                        icon = XPlayerIcons.SkipNext,
                        checked = playback.autoPlayNext,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(playback = it.playback.copy(autoPlayNext = enabled)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.settings_background),
                        subtitle = stringResource(playback.backgroundBehavior.labelRes()),
                        icon = XPlayerIcons.Headphones,
                        options = BackgroundBehavior.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = playback.backgroundBehavior,
                        onSelect = { behavior ->
                            viewModel.update { it.copy(playback = it.playback.copy(backgroundBehavior = behavior)) }
                        },
                        enabled = true,
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

    SettingsPageScaffold(title = stringResource(R.string.player_subtitle_style), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 40.dp),
        ) {
            item {
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
                        subtitle = "-",
                        icon = XPlayerIcons.Palette,
                        options = listOf(
                            SheetOption(stringResource(R.string.settings_sub_color_white), 0xFFFFFFFF),
                            SheetOption(stringResource(R.string.settings_sub_color_yellow), 0xFFFFEB3B),
                            SheetOption(stringResource(R.string.settings_sub_color_cyan), 0xFF80DEEA),
                            SheetOption(stringResource(R.string.settings_sub_color_green), 0xFFB9F6CA),
                        ),
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
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_sub_shadow),
                        subtitle = stringResource(
                            if (subtitle.shadow) {
                                R.string.settings_sub_shadow_desc_on
                            } else {
                                R.string.settings_sub_shadow_desc_off
                            },
                        ),
                        icon = XPlayerIcons.Brightness,
                        checked = subtitle.shadow,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(subtitle = it.subtitle.copy(shadow = enabled)) }
                        },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_sub_bg),
                        subtitle = stringResource(
                            if (subtitle.backgroundBar) {
                                R.string.settings_sub_bg_desc_on
                            } else {
                                R.string.settings_sub_bg_desc_off
                            },
                        ),
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
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_sub_force_ass),
                        subtitle = stringResource(
                            if (subtitle.forceAssOverride) {
                                R.string.settings_sub_force_ass_desc_on
                            } else {
                                R.string.settings_sub_force_ass_desc_off
                            },
                        ),
                        icon = XPlayerIcons.Subtitles,
                        checked = subtitle.forceAssOverride,
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = stringResource(R.string.folders_hidden_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 24.dp,
                            top = padding.calculateTopPadding() + 24.dp,
                        ),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = 40.dp,
                ),
            ) {
                items(hidden, key = { it.folderPath }) { entry ->
                    SettingRow(
                        title = entry.folderPath.substringAfterLast('/'),
                        subtitle = entry.folderPath,
                        icon = Icons.Default.Lock,
                        onClick = { viewModel.unhideFolder(entry.folderPath) },
                        trailing = { TextButton(onClick = { viewModel.unhideFolder(entry.folderPath) }) { Text(stringResource(R.string.folders_unhide)) } },
                        showDivider = true,
                    )
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    SettingsPageScaffold(title = stringResource(R.string.settings_group_about), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                SettingGroup(title = "XPlayer") {
                    SettingRow(
                        title = stringResource(R.string.settings_version),
                        subtitle = stringResource(R.string.settings_version_desc, viewModel.versionName),
                        icon = Icons.Default.Star,
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_engines),
                        subtitle = stringResource(R.string.settings_engines_desc, "Android"),
                        icon = XPlayerIcons.Memory,
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_source),
                        subtitle = stringResource(R.string.settings_source_desc),
                        icon = Icons.Default.Info,
                        showDivider = false,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.mpv_lab_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}
