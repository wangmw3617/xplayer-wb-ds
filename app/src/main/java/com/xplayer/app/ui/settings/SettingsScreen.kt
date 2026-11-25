package com.xplayer.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xplayer.app.R
import com.xplayer.app.data.datastore.ThemeMode
import com.xplayer.app.data.datastore.labelRes
import com.xplayer.app.ui.common.rememberAppVM
import com.xplayer.app.ui.components.ChevronTrailing
import com.xplayer.app.ui.components.FrostedBackdropState
import com.xplayer.app.ui.components.FrostedGlassSurface
import com.xplayer.app.ui.components.FrostedHeaderScrim
import com.xplayer.app.ui.components.InlinePickerRow
import com.xplayer.app.ui.components.SettingGroup
import com.xplayer.app.ui.components.SettingRow
import com.xplayer.app.ui.components.SettingSwitchRow
import com.xplayer.app.ui.components.SheetOption
import com.xplayer.app.ui.components.frostedBackdropSource
import com.xplayer.app.ui.components.rememberFrostedBackdropState
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.ui.navigation.Routes
import com.xplayer.app.ui.permission.PermissionBanner
import kotlinx.coroutines.launch

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
    val hidden by viewModel.hiddenFolders.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val headerInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backdropState = rememberFrostedBackdropState()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = headerInset + 72.dp,
                    bottom = 118.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (!permissionGranted) {
                    item {
                        PermissionBanner(
                            onRequest = onRequestPermission,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                // 顶部核心卡片：复刻 FlClash 截图简洁单行分组结构
                item {
                    SettingGroup {
                        SettingRow(
                            title = stringResource(R.string.settings_group_appearance),
                            icon = XPlayerIcons.Palette,
                            onClick = { onNavigate(Routes.APPEARANCE) },
                            trailing = { ChevronTrailing() },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_group_engine),
                            icon = XPlayerIcons.Memory,
                            onClick = { onNavigate(Routes.ENGINE) },
                            trailing = { ChevronTrailing() },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_scan_now),
                            icon = Icons.Default.Refresh,
                            onClick = viewModel::scan,
                            trailing = {
                                if (scanState.scanning) {
                                    Text(
                                        text = "${scanState.scanned}/${scanState.total}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    ChevronTrailing(videoCount.takeIf { it > 0 }?.toString())
                                }
                            },
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_scan_auto),
                            icon = XPlayerIcons.Storage,
                            checked = settings.media.autoScanOnLaunch,
                            showDivider = false,
                            onCheckedChange = { enabled ->
                                viewModel.update { it.copy(media = it.media.copy(autoScanOnLaunch = enabled)) }
                            },
                        )
                    }
                }

                // 主设置卡片：复刻 FlClash「设置」区段标题与单行控件风格
                item {
                    SettingGroup(title = stringResource(R.string.settings_title)) {
                        InlinePickerRow(
                            title = stringResource(R.string.settings_dark),
                            selectedLabel = stringResource(settings.appearance.themeMode.labelRes()),
                            icon = XPlayerIcons.Brightness,
                            options = ThemeMode.entries.map {
                                SheetOption(stringResource(it.labelRes()), it)
                            },
                            selected = settings.appearance.themeMode,
                            onSelect = { mode ->
                                viewModel.update { it.copy(appearance = it.appearance.copy(themeMode = mode)) }
                            },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_group_playback),
                            icon = XPlayerIcons.Speed,
                            onClick = { onNavigate(Routes.PLAYBACK) },
                            trailing = { ChevronTrailing() },
                        )
                        SettingRow(
                            title = stringResource(R.string.player_subtitle_style),
                            icon = XPlayerIcons.Subtitles,
                            onClick = { onNavigate(Routes.SUBTITLE) },
                            trailing = { ChevronTrailing() },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_group_gesture),
                            icon = XPlayerIcons.Tune,
                            onClick = { onNavigate(Routes.GESTURE) },
                            trailing = { ChevronTrailing() },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_group_mpv),
                            icon = Icons.Default.Build,
                            onClick = { onNavigate(Routes.MPV_LAB) },
                            trailing = { ChevronTrailing() },
                        )
                        SettingRow(
                            title = stringResource(R.string.folders_hidden),
                            icon = XPlayerIcons.Folder,
                            onClick = { onNavigate(Routes.HIDDEN_FOLDERS) },
                            trailing = { ChevronTrailing(hidden.size.takeIf { it > 0 }?.toString()) },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_group_about),
                            icon = Icons.Default.Info,
                            onClick = { onNavigate(Routes.ABOUT) },
                            trailing = { ChevronTrailing("v${viewModel.versionName}") },
                            showDivider = false,
                        )
                    }
                }
            }
        }

        SettingsPillHeader(
            title = stringResource(R.string.settings_title),
            backdropState = backdropState,
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
                .padding(bottom = 98.dp),
        )
    }
}

/**
 * 左上角圆形（全圆角）磨砂玻璃「设置」控件 + 顶部渐变遮罩，点击回滚顶部。
 */
@Composable
private fun SettingsPillHeader(
    title: String,
    backdropState: FrostedBackdropState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        FrostedHeaderScrim(modifier = Modifier.matchParentSize())
        Column {
            FrostedGlassSurface(
                backdropState = backdropState,
                shape = CircleShape,
                onClick = onClick,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
