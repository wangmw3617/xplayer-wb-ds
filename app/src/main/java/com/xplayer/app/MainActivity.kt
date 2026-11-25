package com.xplayer.app

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xplayer.app.data.datastore.AnimationLevel
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.datastore.BottomBarStyle
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.SourceType
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.MediaUriResolver
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.ui.common.rememberAppVM
import com.xplayer.app.ui.components.LocalAppBackdropState
import com.xplayer.app.ui.components.frostedBackdropSource
import com.xplayer.app.ui.components.rememberFrostedBackdropState
import com.xplayer.app.ui.folders.FoldersScreen
import com.xplayer.app.ui.home.HomeScreen
import com.xplayer.app.ui.media.MediaInfoScreen
import com.xplayer.app.ui.navigation.Routes
import com.xplayer.app.ui.navigation.XPlayerBottomBar
import com.xplayer.app.ui.permission.rememberStoragePermission
import com.xplayer.app.ui.player.PlayerActivityBridge
import com.xplayer.app.ui.player.PlayerScreen
import com.xplayer.app.ui.player.PlayerViewModel
import com.xplayer.app.ui.settings.AboutScreen
import com.xplayer.app.ui.settings.AppearanceSettingsScreen
import com.xplayer.app.ui.settings.CodecScreen
import com.xplayer.app.ui.settings.EngineSettingsScreen
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /**
     * 外部「打开方式」传进来的媒体 Uri。
     * 用 StateFlow 而不是 Compose State：消费时置空不会把正在处理的协程一起取消。
     */
    private val externalMediaUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            externalMediaUri.value = mediaUriOf(intent)
        }

        setContent {
            val container = (application as XPlayerApp).container
            val settingsOrNull by container.settingsRepository.settings
                .collectAsState(initial = null)
            val settings = settingsOrNull ?: AppSettings()

            XPlayerTheme(settings.appearance) {
                var inPlayerRoute by remember { mutableStateOf(false) }
                val view = LocalView.current
                val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
                LaunchedEffect(darkSurface, inPlayerRoute) {
                    if (!inPlayerRoute) {
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !darkSurface
                            isAppearanceLightNavigationBars = !darkSurface
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    XPlayerNavHost(
                        autoScanEnabled = settingsOrNull?.media?.autoScanOnLaunch,
                        bottomBarStyle = settings.appearance.bottomBarStyle,
                        animationLevel = settings.appearance.animationLevel,
                        externalMediaUri = externalMediaUri,
                        onExternalMediaConsumed = { externalMediaUri.value = null },
                        onPlayerRouteChanged = { inPlayerRoute = it },
                    )
                }
            }
        }
    }

    /** 应用已在前台时，文件管理器再发一次 VIEW 不会重建 Activity，走这里 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mediaUriOf(intent)?.let { externalMediaUri.value = it }
    }

    private fun mediaUriOf(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        return uri.takeIf {
            scheme == "content" || scheme == "file" || scheme == "http" ||
                scheme == "https" || scheme == "rtsp" || scheme == "rtmp"
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
private fun XPlayerNavHost(
    autoScanEnabled: Boolean?,
    bottomBarStyle: BottomBarStyle,
    animationLevel: AnimationLevel,
    externalMediaUri: StateFlow<Uri?>,
    onExternalMediaConsumed: () -> Unit,
    onPlayerRouteChanged: (Boolean) -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        onPlayerRouteChanged(currentRoute?.startsWith("player") == true)
    }
    val context = LocalContext.current
    val container = (context.applicationContext as XPlayerApp).container
    val appBackdropState = rememberFrostedBackdropState()

    val permission = rememberStoragePermission()
    var autoScanned by rememberSaveable { mutableStateOf(false) }

    // 等 DataStore 真实设置到达后再决定是否自动扫描，避免冷启动默认值误触发
    LaunchedEffect(permission.granted, autoScanEnabled) {
        if (permission.granted && autoScanEnabled == true && !autoScanned) {
            autoScanned = true
            container.mediaScanner.triggerScan()
        }
    }

    fun openPlayer(
        sourceType: String,
        key: String,
        title: String,
        startMs: Long,
        ass: Boolean,
        allowReplace: Boolean = false,
    ) {
        // 防止快速连点本地视频时连续压入多个播放页并发初始化解码器导致卡死
        if (!allowReplace && navController.currentDestination?.route?.startsWith("player") == true) {
            return
        }
        navController.navigate(Routes.player(sourceType, key, title, startMs, ass))
    }

    // 其他应用「打开方式」传进来的文件：解析后直接进播放页
    LaunchedEffect(Unit) {
        externalMediaUri.filterNotNull().collectLatest { uri ->
            val source = withContext(Dispatchers.IO) { MediaUriResolver.resolve(context, uri) }
            // 置空不会打断本协程：上游已 filterNotNull，空值不会再下发给 collectLatest
            onExternalMediaConsumed()
            if (source == null) {
                Toast.makeText(context, R.string.player_open_unsupported, Toast.LENGTH_SHORT).show()
                return@collectLatest
            }
            // 已停在播放页时先退出去，否则旧内核会继续出声
            navController.popBackStack(Routes.PLAYER, inclusive = true)
            when (source) {
                is LocalFileSource -> openPlayer("local", source.path, source.displayName, 0L, source.hasEmbeddedAss, allowReplace = true)
                is ContentUriSource -> openPlayer("uri", source.uri, source.displayName, 0L, false, allowReplace = true)
                is NetworkUrlSource -> openPlayer("network", source.url, source.displayName, 0L, false, allowReplace = true)
            }
        }
    }

    val backdropRecordingEnabled =
        bottomBarStyle == BottomBarStyle.LIQUID_GLASS && currentRoute in Routes.mainTabs

    CompositionLocalProvider(LocalAppBackdropState provides appBackdropState) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .frostedBackdropSource(appBackdropState, enabled = backdropRecordingEnabled)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        when (animationLevel) {
                            AnimationLevel.STANDARD -> slideInHorizontally(tween(260)) { it / 8 } + fadeIn(tween(220))
                            AnimationLevel.SNAPPY -> fadeIn(tween(130))
                            AnimationLevel.OFF -> EnterTransition.None
                        }
                    },
                    exitTransition = {
                        when (animationLevel) {
                            AnimationLevel.STANDARD -> fadeOut(tween(180))
                            AnimationLevel.SNAPPY -> fadeOut(tween(100))
                            AnimationLevel.OFF -> ExitTransition.None
                        }
                    },
                    popEnterTransition = {
                        when (animationLevel) {
                            AnimationLevel.STANDARD -> fadeIn(tween(220))
                            AnimationLevel.SNAPPY -> fadeIn(tween(120))
                            AnimationLevel.OFF -> EnterTransition.None
                        }
                    },
                    popExitTransition = {
                        when (animationLevel) {
                            AnimationLevel.STANDARD -> slideOutHorizontally(tween(240)) { it / 8 } + fadeOut(tween(200))
                            AnimationLevel.SNAPPY -> fadeOut(tween(110))
                            AnimationLevel.OFF -> ExitTransition.None
                        }
                    },
                ) {
            composable(Routes.HOME) {
                HomeScreen(
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
                    onOpenInfo = { video ->
                        navController.navigate(Routes.mediaInfo("local", video.path, video.title))
                    },
                )
            }

            composable(Routes.FOLDERS) {
                FoldersScreen(
                    onPlay = { video, start ->
                        openPlayer("local", video.path, video.title, start, video.hasAssSubtitle)
                    },
                    onOpenHiddenFolders = { navController.navigate(Routes.HIDDEN_FOLDERS) },
                    onOpenInfo = { video ->
                        navController.navigate(Routes.mediaInfo("local", video.path, video.title))
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigate = { route -> navController.navigate(route) },
                    permissionGranted = permission.granted,
                    onRequestPermission = permission.request,
                )
            }

            composable(Routes.APPEARANCE) {
                val vm = rememberAppVM { SettingsViewModel(it) }
                AppearanceSettingsScreen(onBack = { navController.popBackStack() }, viewModel = vm)
            }

            composable(Routes.ENGINE) {
                val vm = rememberAppVM { SettingsViewModel(it) }
                EngineSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCodec = { navController.navigate(Routes.CODEC) },
                    viewModel = vm,
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
                route = Routes.MEDIA_INFO,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType; defaultValue = "local" },
                    navArgument("key") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val args = entry.arguments
                MediaInfoScreen(
                    sourceType = args?.getString("type") ?: "local",
                    key = args?.getString("key").orEmpty(),
                    title = args?.getString("title").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
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
                val ass = args?.getBoolean("ass") ?: false
                val viewModel = rememberAppVM(key = "player:$type:$key") { container ->
                    PlayerViewModel(container, type, key, title, start, ass)
                }
                PlayerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenMediaInfo = {
                        val current = viewModel.controller.currentSource()
                        val infoType = when (current) {
                            is ContentUriSource -> "uri"
                            is NetworkUrlSource -> "network"
                            is LocalFileSource -> "local"
                            null -> viewModel.sourceKind
                        }
                        navController.navigate(
                            Routes.mediaInfo(
                                infoType,
                                current?.key ?: viewModel.activeKey,
                                current?.displayName ?: viewModel.activeTitle,
                            ),
                        )
                    },
                )
            }
        }
        }

        if (currentRoute in Routes.mainTabs) {
            XPlayerBottomBar(
                currentRoute = currentRoute,
                style = bottomBarStyle,
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
}
