package com.xplayer.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.SourceType
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.ui.common.rememberAppVM
import com.xplayer.app.ui.folders.FoldersScreen
import com.xplayer.app.ui.home.HomeScreen
import com.xplayer.app.ui.navigation.Routes
import com.xplayer.app.ui.navigation.XPlayerBottomBar
import com.xplayer.app.ui.permission.rememberStoragePermission
import com.xplayer.app.ui.player.PlayerActivityBridge
import com.xplayer.app.ui.player.PlayerScreen
import com.xplayer.app.ui.player.PlayerViewModel
import com.xplayer.app.ui.settings.AboutScreen
import com.xplayer.app.ui.settings.CodecScreen
import com.xplayer.app.ui.settings.GestureSettingsScreen
import com.xplayer.app.ui.settings.HiddenFoldersScreen
import com.xplayer.app.ui.settings.MpvLabScreen
import com.xplayer.app.ui.settings.PlaybackSettingsScreen
import com.xplayer.app.ui.settings.SettingsScreen
import com.xplayer.app.ui.settings.SettingsViewModel
import com.xplayer.app.ui.settings.SubtitleSettingsScreen
import com.xplayer.app.ui.theme.XPlayerTheme
import com.xplayer.app.ui.videos.VideosScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val container = (application as XPlayerApp).container
            val settings by container.settingsRepository.settings
                .collectAsState(initial = AppSettings())

            XPlayerTheme(settings.appearance) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    XPlayerNavHost(autoScanOnLaunch = settings.media.autoScanOnLaunch)
                }
            }
        }
    }

    /**
     * 播放中按 Home：若后台行为设为画中画且正在播放，自动进入 PiP。
     * 播放页未挂载时 autoPip 为 null，交回系统默认行为。
     */
    override fun onUserLeaveHint() {
        val handled = PlayerActivityBridge.autoPip?.invoke() == true
        if (!handled) super.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerActivityBridge.onPipChanged?.invoke(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        if (isFinishing) PlayerActivityBridge.clear()
        super.onDestroy()
    }
}

@Composable
private fun XPlayerNavHost(autoScanOnLaunch: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val container = (LocalContext.current.applicationContext as XPlayerApp).container

    val permission = rememberStoragePermission()
    var autoScanned by rememberSaveable { mutableStateOf(false) }

    // 首次拿到权限后自动扫库一次，后续由用户手动触发
    LaunchedEffect(permission.granted, autoScanOnLaunch) {
        if (permission.granted && autoScanOnLaunch && !autoScanned) {
            autoScanned = true
            withContext(Dispatchers.IO) { runCatching { container.mediaScanner.scan() } }
        }
    }

    fun openPlayer(sourceType: String, key: String, title: String, startMs: Long, ass: Boolean) {
        navController.navigate(Routes.player(sourceType, key, title, startMs, ass))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onPlayVideo = { video: VideoEntity, start: Long ->
                        openPlayer("local", video.path, video.title, start, video.hasAssSubtitle)
                    },
                    onResumeHistory = { entry: PlaybackHistoryEntity ->
                        openPlayer(
                            if (entry.sourceType == SourceType.LOCAL) "local" else "network",
                            entry.mediaId,
                            entry.title,
                            0L,
                            false,
                        )
                    },
                    onPlayNetwork = { url, title ->
                        openPlayer("network", url, title?.takeIf { it.isNotBlank() } ?: url, 0L, false)
                    },
                    onOpenCodec = { navController.navigate(Routes.CODEC) },
                    onOpenVideos = { navController.navigate(Routes.VIDEOS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    permissionGranted = permission.granted,
                    onRequestPermission = permission.request,
                )
            }

            composable(Routes.VIDEOS) {
                VideosScreen(
                    onPlay = { video, start ->
                        openPlayer("local", video.path, video.title, start, video.hasAssSubtitle)
                    },
                )
            }

            composable(Routes.FOLDERS) {
                FoldersScreen(
                    onPlay = { video, start ->
                        openPlayer("local", video.path, video.title, start, video.hasAssSubtitle)
                    },
                    onOpenHiddenFolders = { navController.navigate(Routes.HIDDEN_FOLDERS) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigate = { route -> navController.navigate(route) },
                    permissionGranted = permission.granted,
                    onRequestPermission = permission.request,
                )
            }

            composable(Routes.GESTURE) {
                val vm = rememberAppVM { SettingsViewModel(it) }
                GestureSettingsScreen(onBack = { navController.popBackStack() }, viewModel = vm)
            }

            composable(Routes.PLAYBACK) {
                val vm = rememberAppVM { SettingsViewModel(it) }
                PlaybackSettingsScreen(onBack = { navController.popBackStack() }, viewModel = vm)
            }

            composable(Routes.SUBTITLE) {
                val vm = rememberAppVM { SettingsViewModel(it) }
                SubtitleSettingsScreen(onBack = { navController.popBackStack() }, viewModel = vm)
            }

            composable(Routes.MPV_LAB) {
                val vm = rememberAppVM { SettingsViewModel(it) }
                MpvLabScreen(onBack = { navController.popBackStack() }, viewModel = vm)
            }

            composable(Routes.HIDDEN_FOLDERS) {
                val vm = rememberAppVM { SettingsViewModel(it) }
                HiddenFoldersScreen(onBack = { navController.popBackStack() }, viewModel = vm)
            }

            composable(Routes.ABOUT) {
                val vm = rememberAppVM { SettingsViewModel(it) }
                AboutScreen(onBack = { navController.popBackStack() }, viewModel = vm)
            }

            composable(Routes.CODEC) {
                CodecScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType; defaultValue = "local" },
                    navArgument("key") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("start") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("ass") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val args = entry.arguments
                val type = args?.getString("type") ?: "local"
                val key = args?.getString("key").orEmpty()
                val title = args?.getString("title").orEmpty()
                val start = args?.getLong("start") ?: 0L
                val viewModel = rememberAppVM(key = "player:$type:$key") { container ->
                    PlayerViewModel(container, type, key, title, start)
                }
                PlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }

        if (currentRoute in Routes.mainTabs) {
            XPlayerBottomBar(
                currentRoute = currentRoute,
                onSelect = { target ->
                    if (target != currentRoute) {
                        navController.navigate(target) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
