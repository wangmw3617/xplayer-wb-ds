package com.xplayer.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.datastore.ColorSeed
import com.xplayer.app.data.datastore.EnginePolicy
import com.xplayer.app.data.datastore.MpvHwDecoder
import com.xplayer.app.data.datastore.ThemeMode
import com.xplayer.app.data.datastore.labelRes
import com.xplayer.app.data.datastore.summaryRes
import com.xplayer.app.ui.common.rememberAppVM
import com.xplayer.app.ui.components.ChevronTrailing
import com.xplayer.app.ui.components.PickerSettingRow
import com.xplayer.app.ui.components.SettingGroup
import com.xplayer.app.ui.components.SettingRow
import com.xplayer.app.ui.components.SettingSwitchRow
import com.xplayer.app.ui.components.SheetOption
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.ui.permission.PermissionBanner
import com.xplayer.app.ui.navigation.Routes

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    val viewModel = rememberAppVM { SettingsViewModel(it) }
    val settings by viewModel.settings.collectAsState()
    val videoCount by viewModel.videoCount.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val shaders by viewModel.shaders.collectAsState()
    val hidden by viewModel.hiddenFolders.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val headerInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var showSeedSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                // 顶部留出胶囊标题的高度，内容可以滚动到标题下面
                top = headerInset + 92.dp,
                bottom = 110.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (!permissionGranted) {
                item {
                    PermissionBanner(
                        onRequest = onRequestPermission,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.settings_group_appearance)) {
                    PickerSettingRow(
                        title = stringResource(R.string.settings_dark),
                        subtitle = stringResource(settings.appearance.themeMode.labelRes()),
                        icon = XPlayerIcons.Palette,
                        options = ThemeMode.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = settings.appearance.themeMode,
                        onSelect = { mode ->
                            viewModel.update { it.copy(appearance = it.appearance.copy(themeMode = mode)) }
                        },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(
                            if (settings.appearance.dynamicColor) {
                                R.string.settings_dynamic_color_on
                            } else {
                                R.string.settings_dynamic_color_off
                            },
                        ),
                        icon = XPlayerIcons.Storage,
                        checked = settings.appearance.dynamicColor,
                        onCheckedChange = { enabled ->
                            viewModel.update {
                                it.copy(appearance = it.appearance.copy(dynamicColor = enabled))
                            }
                        },
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_seed),
                        subtitle = stringResource(R.string.settings_seed_desc, stringResource(settings.appearance.seed.labelRes())),
                        icon = XPlayerIcons.Bolt,
                        onClick = { showSeedSheet = true },
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.settings_group_engine)) {
                    PickerSettingRow(
                        title = stringResource(R.string.settings_engine_policy),
                        subtitle = stringResource(settings.engine.policy.summaryRes()),
                        icon = XPlayerIcons.Memory,
                        options = EnginePolicy.entries.map {
                            SheetOption(stringResource(it.labelRes()), it, stringResource(it.summaryRes()))
                        },
                        selected = settings.engine.policy,
                        onSelect = { policy ->
                            viewModel.update { it.copy(engine = it.engine.copy(policy = policy)) }
                        },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_ass_auto),
                        subtitle = stringResource(
                            if (settings.engine.assAutoSwitch) {
                                R.string.settings_ass_auto_desc_on
                            } else {
                                R.string.settings_ass_auto_desc_off
                            },
                        ),
                        icon = XPlayerIcons.Subtitles,
                        checked = settings.engine.assAutoSwitch,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(engine = it.engine.copy(assAutoSwitch = enabled)) }
                        },
                    )
                    PickerSettingRow(
                        title = stringResource(R.string.settings_mpv_decoder),
                        subtitle = stringResource(settings.engine.mpvHwDecoder.labelRes()),
                        icon = XPlayerIcons.Tune,
                        options = MpvHwDecoder.entries.map {
                            SheetOption(stringResource(it.labelRes()), it)
                        },
                        selected = settings.engine.mpvHwDecoder,
                        onSelect = { decoder ->
                            viewModel.update { it.copy(engine = it.engine.copy(mpvHwDecoder = decoder)) }
                        },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_frame_rate),
                        subtitle = stringResource(
                            if (settings.engine.frameRateMatching) {
                                R.string.settings_frame_rate_desc_on
                            } else {
                                R.string.settings_frame_rate_desc_off
                            },
                        ),
                        icon = XPlayerIcons.Speed,
                        checked = settings.engine.frameRateMatching,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(engine = it.engine.copy(frameRateMatching = enabled)) }
                        },
                    )
                    SettingRow(
                        title = stringResource(R.string.codec_title),
                        subtitle = stringResource(R.string.home_codec_desc),
                        icon = XPlayerIcons.Memory,
                        onClick = { onNavigate(Routes.CODEC) },
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.settings_group_gesture)) {
                    SettingRow(
                        title = stringResource(R.string.settings_group_gesture),
                        subtitle = stringResource(R.string.gesture_left_action) + " · " +
                            stringResource(R.string.gesture_double_center),
                        icon = XPlayerIcons.Tune,
                        onClick = { onNavigate(Routes.GESTURE) },
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.settings_group_playback)) {
                    SettingRow(
                        title = stringResource(R.string.settings_default_speed),
                        subtitle = stringResource(
                            R.string.settings_default_speed_desc,
                            com.xplayer.app.util.Formatters.speed(settings.playback.defaultSpeed) + "×",
                        ),
                        icon = XPlayerIcons.Speed,
                        onClick = { onNavigate(Routes.PLAYBACK) },
                        trailing = { ChevronTrailing() },
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_sub_font_size),
                        subtitle = stringResource(R.string.settings_sub_font_size_desc, settings.subtitle.fontScalePercent),
                        icon = XPlayerIcons.Subtitles,
                        onClick = { onNavigate(Routes.SUBTITLE) },
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.settings_group_mpv)) {
                    SettingRow(
                        title = stringResource(R.string.mpv_conf),
                        subtitle = stringResource(R.string.mpv_conf_desc),
                        icon = Icons.Default.Build,
                        onClick = { onNavigate(Routes.MPV_LAB) },
                        trailing = { ChevronTrailing() },
                    )
                    SettingRow(
                        title = stringResource(R.string.mpv_shaders),
                        subtitle = stringResource(
                            R.string.mpv_shaders_desc,
                            shaders.size,
                            shaders.count { it.enabled },
                        ),
                        icon = XPlayerIcons.Grid,
                        onClick = { onNavigate(Routes.MPV_LAB) },
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.settings_group_media)) {
                    SettingRow(
                        title = stringResource(R.string.settings_scan_now),
                        subtitle = stringResource(R.string.settings_scan_now_desc, videoCount),
                        icon = Icons.Default.Refresh,
                        onClick = viewModel::scan,
                        trailing = {
                            if (scanState.scanning) {
                                Text(
                                    text = "${scanState.scanned}/${scanState.total}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                ChevronTrailing()
                            }
                        },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_scan_auto),
                        subtitle = stringResource(
                            if (settings.media.autoScanOnLaunch) {
                                R.string.settings_scan_auto_desc_on
                            } else {
                                R.string.settings_scan_auto_desc_off
                            },
                        ),
                        icon = XPlayerIcons.Storage,
                        checked = settings.media.autoScanOnLaunch,
                        onCheckedChange = { enabled ->
                            viewModel.update { it.copy(media = it.media.copy(autoScanOnLaunch = enabled)) }
                        },
                    )
                    SettingRow(
                        title = stringResource(R.string.folders_hidden),
                        subtitle = stringResource(R.string.folders_hidden_desc),
                        icon = XPlayerIcons.Folder,
                        onClick = { onNavigate(Routes.HIDDEN_FOLDERS) },
                        trailing = { ChevronTrailing(hidden.size.takeIf { it > 0 }?.toString()) },
                        showDivider = false,
                    )
                }
            }

            item {
                SettingGroup(title = stringResource(R.string.settings_group_about)) {
                    SettingRow(
                        title = stringResource(R.string.settings_version),
                        subtitle = stringResource(R.string.settings_version_desc, viewModel.versionName),
                        icon = Icons.Default.Star,
                        onClick = { onNavigate(Routes.ABOUT) },
                        trailing = { ChevronTrailing() },
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_engines),
                        subtitle = stringResource(R.string.settings_engines_desc, "0.2.2"),
                        icon = Icons.Default.Info,
                        onClick = { onNavigate(Routes.ABOUT) },
                        trailing = { ChevronTrailing() },
                        showDivider = false,
                    )
                }
            }
        }

        SettingsPillHeader(
            title = stringResource(R.string.settings_title),
            onClick = {
                scope.launch { listState.animateScrollToItem(0) }
            },
            modifier = Modifier.align(Alignment.TopStart),
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )
    }

    if (showSeedSheet) {
        com.xplayer.app.ui.components.OptionSheet(
            title = stringResource(R.string.settings_seed),
            options = ColorSeed.entries.map {
                SheetOption(stringResource(it.labelRes()), it)
            },
            selected = settings.appearance.seed,
            onSelect = { seed ->
                viewModel.update {
                    it.copy(appearance = it.appearance.copy(seed = seed, dynamicColor = false))
                }
            },
            onDismiss = { showSeedSheet = false },
        )
    }
}

/**
 * 左上角胶囊标题 + 顶部透明渐变遮罩。
 * 内容滚动到标题下方时会被渐变自然吞掉，视觉上和 FlClash 的设置页一致。
 */
@Composable
private fun SettingsPillHeader(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to background,
                        0.48f to background.copy(alpha = 0.94f),
                        0.80f to background.copy(alpha = 0.52f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(50),
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = 7.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}
