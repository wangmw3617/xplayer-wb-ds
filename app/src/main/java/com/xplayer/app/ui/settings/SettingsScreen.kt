package com.xplayer.app.ui.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showSeedSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
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
