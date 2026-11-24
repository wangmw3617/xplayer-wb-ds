package com.xplayer.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xplayer.app.R
import com.xplayer.app.data.datastore.AspectMode
import com.xplayer.app.data.datastore.BackgroundBehavior
import com.xplayer.app.data.datastore.GestureAction
import com.xplayer.app.data.datastore.labelRes
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.data.source.SidecarSubtitle
import com.xplayer.app.player.ScreenshotSaver
import com.xplayer.app.player.engine.EngineType
import com.xplayer.app.player.pip.PipController
import com.xplayer.app.player.pip.PlayerCommand
import com.xplayer.app.player.pip.PlayerCommandBus
import com.xplayer.app.player.service.PlaybackService
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.util.Formatters
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class PlayerSheet { TRACKS, SPEED, ASPECT, SUBTITLE, SETTINGS }

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenMediaInfo: () -> Unit = {},
) {
    val engine by viewModel.engine.collectAsState()
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val mediaSummary by viewModel.mediaSummary.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var controlsVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var rotationMode by remember { mutableStateOf(0) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var showRemaining by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var muted by remember { mutableStateOf(false) }
    var serviceRunning by remember { mutableStateOf(false) }
    var orientationApplied by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it > 0f } ?: 0.5f,
        )
    }

    fun applyBrightness(value: Float) {
        brightness = value
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
        }
    }

    fun applyVolume(value: Float) {
        volume = value
        if (value <= 1f) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (value * maxVolume).roundToInt(), 0)
            viewModel.controller.setVolume(1f)
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            viewModel.controller.setVolume(value)
        }
    }

    fun takeScreenshot() {
        scope.launch {
            val bitmap = viewModel.capture()
            if (bitmap == null) {
                snackbar.showSnackbar(context.getString(R.string.player_screenshot_failed))
            } else {
                ScreenshotSaver.save(context, bitmap, settings.playback.screenshotFormat)
                snackbar.showSnackbar(context.getString(R.string.player_screenshot_saved))
            }
        }
    }

    // Android 13+ 显示媒体通知卡片需要通知权限
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun handleAction(action: GestureAction) {
        when (action) {
            GestureAction.SEEK_BACK -> viewModel.controller.seekBy(-settings.gesture.leftStepMs)
            GestureAction.SEEK_FORWARD -> viewModel.controller.seekBy(settings.gesture.rightStepMs)
            GestureAction.PLAY_PAUSE -> viewModel.controller.togglePlayPause()
            GestureAction.PREV -> viewModel.controller.previous()
            GestureAction.NEXT -> viewModel.controller.next()
            GestureAction.MUTE -> {
                muted = !muted
                viewModel.controller.setVolume(if (muted) 0f else 1f)
            }
            GestureAction.SCREENSHOT -> takeScreenshot()
            GestureAction.ASPECT_TOGGLE -> {
                viewModel.setAspect(if (viewModel.aspectMode == AspectMode.FIT) AspectMode.CROP else AspectMode.FIT)
                zoomScale = 1f
            }
            GestureAction.DISABLED -> Unit
        }
    }

    // 沉浸式全屏
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // 通知 / 提示
    LaunchedEffect(Unit) {
        viewModel.notice.collect { notice ->
            val text = notice.arg?.let { context.getString(notice.messageRes, it) }
                ?: context.getString(notice.messageRes)
            snackbar.showSnackbar(text)
        }
    }

    // 断点续播提示（附「从头播放」快捷入口）
    LaunchedEffect(state.ready) {
        if (state.ready && !viewModel.resumeNotified && viewModel.resumePosition.value > 5_000L) {
            viewModel.markResumeNotified()
            val result = snackbar.showSnackbar(
                message = context.getString(
                    R.string.player_resume_toast,
                    Formatters.duration(viewModel.resumePosition.value),
                ),
                actionLabel = context.getString(R.string.player_play_from_start),
                withDismissAction = true,
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.controller.seekTo(0L)
            }
        }
    }

    // 控制栏自动隐藏
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying && !viewModel.locked) {
            kotlinx.coroutines.delay(4_000)
            controlsVisible = false
        }
    }

    // 初次按视频比例决定方向
    LaunchedEffect(state.videoWidth, state.videoHeight) {
        if (!orientationApplied && state.videoWidth > 0 && state.videoHeight > 0) {
            orientationApplied = true
            activity?.requestedOrientation = if (state.videoWidth > state.videoHeight) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // 后台行为：PiP / 后台音频 / 暂停
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestState by rememberUpdatedState(state)
    val latestSettings by rememberUpdatedState(settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        activity?.isInPictureInPictureMode == true
                    when (latestSettings.playback.backgroundBehavior) {
                        BackgroundBehavior.PAUSE -> viewModel.controller.pause()
                        BackgroundBehavior.AUDIO -> {
                            if (!inPip && latestState.isPlaying) {
                                startBackgroundAudio(context, viewModel, latestState.positionMs)?.let {
                                    serviceRunning = true
                                    viewModel.controller.pause()
                                }
                            }
                        }
                        BackgroundBehavior.PIP -> Unit
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (serviceRunning) {
                        context.stopService(Intent(context, PlaybackService::class.java))
                        serviceRunning = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 按 Home 自动进画中画（后台行为 = PIP 时）
    DisposableEffect(activity) {
        PlayerActivityBridge.autoPip = {
            if (activity?.isInPictureInPictureMode == true) {
                true
            } else {
                val wantPip = latestSettings.playback.backgroundBehavior == BackgroundBehavior.PIP &&
                    latestState.isPlaying && !latestState.ended
                if (wantPip && activity != null) {
                    PipController.enter(
                        activity = activity,
                        videoWidth = latestState.videoWidth,
                        videoHeight = latestState.videoHeight,
                        isPlaying = true,
                    )
                } else {
                    false
                }
            }
        }
        PlayerActivityBridge.onPipChanged = { inPip ->
            controlsVisible = !inPip
        }
        onDispose {
            PlayerActivityBridge.autoPip = null
            PlayerActivityBridge.onPipChanged = null
        }
    }

    // 画中画窗口内的遥控按钮
    LaunchedEffect(Unit) {
        PlayerCommandBus.commands.collect { command ->
            when (command) {
                PlayerCommand.TOGGLE_PLAY -> viewModel.controller.togglePlayPause()
                PlayerCommand.REWIND -> viewModel.controller.seekBy(-10_000)
                PlayerCommand.FORWARD -> viewModel.controller.seekBy(10_000)
            }
        }
    }

    // 让系统提前知道「回桌面要自动进 PiP」，并随播放状态刷新窗口内动作图标
    LaunchedEffect(state.isPlaying, settings.playback.backgroundBehavior, state.videoWidth, state.videoHeight) {
        val act = activity ?: return@LaunchedEffect
        PipController.updateActions(
            activity = act,
            videoWidth = state.videoWidth,
            videoHeight = state.videoHeight,
            isPlaying = state.isPlaying,
            autoEnter = settings.playback.backgroundBehavior == BackgroundBehavior.PIP &&
                state.isPlaying && !state.ended,
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        engine?.let { currentEngine ->
            key(currentEngine) {
                AndroidView(
                    factory = { ctx -> currentEngine.createVideoView(ctx) },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                        ),
                )
            }
            LaunchedEffect(currentEngine) {
                viewModel.startPlayback()
            }
        }

        PlayerGestureOverlay(
            prefs = settings.gesture,
            locked = viewModel.locked,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            brightness = brightness,
            volume = volume,
            onBrightness = ::applyBrightness,
            onVolume = ::applyVolume,
            onSeekPreview = { },
            onSeekCommit = { target -> viewModel.controller.seekTo(target) },
            onSingleTap = {
                if (viewModel.locked) {
                    // 锁屏时单击唤出解锁入口
                    controlsVisible = true
                } else {
                    controlsVisible = !controlsVisible
                }
            },
            onDoubleTap = ::handleAction,
            onLongPressSpeed = { speed ->
                if (speed == null) {
                    viewModel.controller.setSpeed(settings.playback.defaultSpeed)
                } else {
                    viewModel.controller.setSpeed(speed)
                }
            },
            onZoom = { scale, _ -> zoomScale = scale },
        )

        // 顶部 / 中央 / 底部控制层（布局参考 YouTube 安卓端，配色圆角沿用应用主题）
        AnimatedVisibility(
            visible = controlsVisible && !viewModel.locked,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.55f),
                                0.22f to Color.Transparent,
                                0.72f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.62f),
                            ),
                        ),
                )

                PlayerTopBar(
                    title = viewModel.controller.currentSource()?.displayName ?: "",
                    summary = mediaSummary,
                    onBack = onBack,
                    onInfo = onOpenMediaInfo,
                    onRotate = {
                        rotationMode = (rotationMode + 1) % 3
                        applyRotation(activity, rotationMode)
                    },
                    onPip = {
                        val ok = activity?.let {
                            PipController.enter(it, state.videoWidth, state.videoHeight, state.isPlaying)
                        } ?: false
                        if (!ok) {
                            scope.launch {
                                snackbar.showSnackbar(context.getString(R.string.player_pip_failed))
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                PlayerCenterControls(
                    isPlaying = state.isPlaying,
                    onRewind = { viewModel.controller.seekBy(-10_000) },
                    onForward = { viewModel.controller.seekBy(10_000) },
                    onTogglePlay = viewModel.controller::togglePlayPause,
                    modifier = Modifier.align(Alignment.Center),
                )

                PlayerBottomBar(
                    state = state,
                    dragging = dragging,
                    dragValue = dragValue,
                    showRemaining = showRemaining,
                    hasPrevious = viewModel.controller.hasPrevious,
                    hasNext = viewModel.controller.hasNext,
                    onSeekPreview = {
                        dragging = true
                        dragValue = it
                    },
                    onSeekCommit = {
                        dragging = false
                        viewModel.controller.seekTo(it.toLong())
                    },
                    onToggleRemaining = { showRemaining = !showRemaining },
                    onSpeed = { sheet = PlayerSheet.SPEED },
                    onTracks = { sheet = PlayerSheet.TRACKS },
                    onPrevious = viewModel.controller::previous,
                    onNext = viewModel.controller::next,
                    onLock = {
                        viewModel.toggleLock()
                        controlsVisible = false
                    },
                    onSettings = { sheet = PlayerSheet.SETTINGS },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // 锁屏状态：只显示一个淡入的解锁按钮
        if (viewModel.locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp)
                    .size(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { viewModel.toggleLock() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.player_unlock_hint),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // 缓冲指示
        if (state.isBuffering && !state.ended) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )
    }

    when (sheet) {
        PlayerSheet.TRACKS -> TracksSheet(
            audioTracks = state.audioTracks,
            subtitleTracks = state.subtitleTracks,
            selectedAudio = state.selectedAudioId,
            selectedSubtitle = state.selectedSubtitleId,
            subtitleDelayMs = state.subtitleDelayMs,
            supportsDelay = state.supportsSubtitleDelay,
            onSelectAudio = { viewModel.controller.selectAudio(it) },
            onSelectSubtitle = { viewModel.controller.selectSubtitle(it) },
            onDelayChange = { viewModel.controller.setSubtitleDelay(it) },
            onImportSubtitle = { sub: SidecarSubtitle -> viewModel.controller.addExternalSubtitle(sub) },
            onDismiss = { sheet = null },
        )
        PlayerSheet.SPEED -> SpeedSheet(
            current = state.speed,
            onSpeed = { viewModel.controller.setSpeed(it) },
            onDismiss = { sheet = null },
        )
        PlayerSheet.ASPECT -> AspectSheet(
            current = viewModel.aspectMode,
            onSelect = { viewModel.setAspect(it) },
            onDismiss = { sheet = null },
        )
        PlayerSheet.SUBTITLE -> SubtitleStyleSheet(
            style = viewModel.subtitleStyle,
            onChange = { viewModel.applySubtitleStyle(it) },
            onDismiss = { sheet = null },
        )
        PlayerSheet.SETTINGS -> PlayerSettingsSheet(
            engine = state.engine,
            mpvAvailable = com.xplayer.app.player.engine.EngineAvailability.mpvAvailable,
            mpvBlockedByUri = viewModel.sourceKind == "uri",
            aspectLabel = stringResource(viewModel.aspectMode.labelRes()),
            rotationMode = rotationMode,
            onSwitchEngine = { target ->
                sheet = null
                if (target != state.engine) viewModel.switchEngine(target)
            },
            onAspect = { sheet = PlayerSheet.ASPECT },
            onSubtitleStyle = { sheet = PlayerSheet.SUBTITLE },
            onScreenshot = {
                sheet = null
                takeScreenshot()
            },
            onBackgroundAudio = {
                sheet = null
                ensureNotificationPermission()
                if (startBackgroundAudio(context, viewModel, state.positionMs) != null) {
                    serviceRunning = true
                    viewModel.controller.pause()
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.player_background_started))
                    }
                }
            },
            onPip = {
                sheet = null
                val ok = activity?.let {
                    PipController.enter(it, state.videoWidth, state.videoHeight, state.isPlaying)
                } ?: false
                if (!ok) {
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.player_pip_failed))
                    }
                }
            },
            onRotate = { mode ->
                rotationMode = mode
                applyRotation(activity, mode)
            },
            onMediaInfo = {
                sheet = null
                onOpenMediaInfo()
            },
            onLock = {
                sheet = null
                viewModel.toggleLock()
                controlsVisible = false
            },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    summary: String?,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onRotate: () -> Unit,
    onPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            contentDescription = stringResource(R.string.common_back),
        )
        Spacer(Modifier.width(6.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 一行技术参数：容器/编码/分辨率/帧率/HDR/音轨/码率，点开是完整的媒体信息页
            if (!summary.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onInfo)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.action_media_info),
                        tint = Color.White.copy(alpha = 0.66f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = summary,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        OverlayIconButton(
            icon = XPlayerIcons.RotateScreen,
            onClick = onRotate,
            contentDescription = stringResource(R.string.player_rotate),
        )
        OverlayIconButton(
            icon = XPlayerIcons.PictureInPicture,
            onClick = onPip,
            contentDescription = stringResource(R.string.player_pip),
        )
    }
}

/** 中央三键：后退 10s / 播放暂停 / 前进 10s，主按钮用主题强调色 */
@Composable
private fun PlayerCenterControls(
    isPlaying: Boolean,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        SkipByTenButton(forward = false, onClick = onRewind)
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) XPlayerIcons.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.player_pause else R.string.player_play,
                ),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        SkipByTenButton(forward = true, onClick = onForward)
    }
}

@Composable
private fun SkipByTenButton(forward: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer(scaleX = if (forward) -1f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = XPlayerIcons.Replay,
                contentDescription = stringResource(
                    if (forward) R.string.player_seek_forward_10 else R.string.player_seek_back_10,
                ),
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = "10",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PlayerBottomBar(
    state: com.xplayer.app.player.engine.PlayerUiState,
    dragging: Boolean,
    dragValue: Float,
    showRemaining: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    onToggleRemaining: () -> Unit,
    onSpeed: () -> Unit,
    onTracks: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    val position = if (dragging) dragValue.toLong() else state.positionMs
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Formatters.duration(position),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
            )
            Slider(
                value = position.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = onSeekPreview,
                onValueChangeFinished = { onSeekCommit(position.toFloat()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
            Text(
                text = if (showRemaining) {
                    "-" + Formatters.duration((state.durationMs - position).coerceAtLeast(0))
                } else {
                    Formatters.duration(state.durationMs)
                },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleRemaining,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayIconButton(
                icon = Icons.Default.Lock,
                onClick = onLock,
                contentDescription = stringResource(R.string.player_lock),
            )
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(50),
                    )
                    .clickable(onClick = onSpeed)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${Formatters.speed(state.speed)}×",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(2.dp))
            OverlayIconButton(
                icon = XPlayerIcons.Subtitles,
                onClick = onTracks,
                contentDescription = stringResource(R.string.player_tracks),
            )
            Spacer(Modifier.weight(1f))
            OverlayIconButton(
                icon = XPlayerIcons.SkipPrevious,
                enabled = hasPrevious,
                onClick = onPrevious,
                contentDescription = stringResource(R.string.player_prev),
            )
            OverlayIconButton(
                icon = XPlayerIcons.SkipNext,
                enabled = hasNext,
                onClick = onNext,
                contentDescription = stringResource(R.string.player_next),
            )
            OverlayIconButton(
                icon = Icons.Default.Settings,
                onClick = onSettings,
                contentDescription = stringResource(R.string.player_settings),
            )
        }
    }
}

@Composable
private fun OverlayIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 30.dp,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = Color.White.copy(alpha = if (enabled) 0.95f else 0.3f),
        modifier = Modifier
            .size(size + 12.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp),
    )
}

private fun applyRotation(activity: Activity?, mode: Int) {
    activity?.requestedOrientation = when (mode) {
        1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

private fun startBackgroundAudio(
    context: Context,
    viewModel: PlayerViewModel,
    positionMs: Long,
): Intent? {
    val source = viewModel.controller.currentSource() ?: return null
    val uri = when (source) {
        is LocalFileSource -> Uri.fromFile(File(source.path)).toString()
        is ContentUriSource -> source.uri
        is NetworkUrlSource -> source.url
    }
    val intent = PlaybackService.startIntent(context, uri, source.displayName, positionMs)
    return runCatching {
        ContextCompat.startForegroundService(context, intent)
        intent
    }.getOrNull()
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
