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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.xplayer.app.data.datastore.SingleTapAction
import com.xplayer.app.data.datastore.labelRes
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.data.source.SidecarSubtitle
import com.xplayer.app.player.ScreenshotSaver
import com.xplayer.app.player.engine.EngineType
import com.xplayer.app.player.engine.PlayerUiState
import com.xplayer.app.player.pip.PipController
import com.xplayer.app.player.pip.PlayerCommand
import com.xplayer.app.player.pip.PlayerCommandBus
import com.xplayer.app.player.service.PlaybackService
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.util.Formatters
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    // 将高频刷新的 positionMs / bufferedMs 剥离出根作用域，仅让进度条区域订阅，避免播放期间整页每 300ms 重组
    val state by remember(viewModel) {
        viewModel.state
            .map { it.copy(positionMs = 0L, bufferedMs = 0L) }
            .distinctUntilChanged()
    }.collectAsState(initial = viewModel.state.value.copy(positionMs = 0L, bufferedMs = 0L))
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var controlsVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var rotationMode by remember { mutableStateOf(0) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
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
                val saved = ScreenshotSaver.save(context, bitmap, settings.playback.screenshotFormat)
                snackbar.showSnackbar(
                    context.getString(
                        if (saved != null) R.string.player_screenshot_saved else R.string.player_screenshot_failed,
                    ),
                )
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
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
    LaunchedEffect(controlsVisible, state.isPlaying, sheet) {
        if (controlsVisible && state.isPlaying && !viewModel.locked && sheet == null) {
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
                                startBackgroundAudio(context, viewModel, viewModel.state.value.positionMs)?.let {
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
            positionMsProvider = { viewModel.state.value.positionMs },
            durationMsProvider = { viewModel.state.value.durationMs },
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
                } else if (settings.gesture.singleTapAction == SingleTapAction.PLAY_PAUSE) {
                    viewModel.controller.togglePlayPause()
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

        // 顶部 / 中央 / 底部控制层（布局参考 YouTube 安卓端，进度条与侧边面板借鉴哔哩哔哩）
        AnimatedVisibility(
            visible = controlsVisible && !viewModel.locked && sheet == null,
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
                    onBack = onBack,
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
                    progressState = viewModel.state,
                    speed = state.speed,
                    hasPrevious = viewModel.controller.hasPrevious,
                    hasNext = viewModel.controller.hasNext,
                    onSeekCommit = { viewModel.controller.seekTo(it) },
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

        // 锁屏状态：只显示一个淡入的解锁按钮（避开屏幕边缘与挖孔区）
        if (viewModel.locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .displayCutoutPadding()
                    .padding(start = 24.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
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
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(bottom = 104.dp),
        )

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
                    if (startBackgroundAudio(context, viewModel, viewModel.state.value.positionMs) != null) {
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
}

@Composable
private fun PlayerTopBar(
    title: String,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .displayCutoutPadding()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            contentDescription = stringResource(R.string.common_back),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
    progressState: StateFlow<PlayerUiState>,
    speed: Float,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onSeekCommit: (Long) -> Unit,
    onSpeed: () -> Unit,
    onTracks: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        PlayerProgressSection(
            stateFlow = progressState,
            onSeekCommit = onSeekCommit,
        )
        Spacer(Modifier.height(4.dp))
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
                    text = "${Formatters.speed(speed)}×",
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
private fun PlayerProgressSection(
    stateFlow: StateFlow<PlayerUiState>,
    onSeekCommit: (Long) -> Unit,
) {
    val state by stateFlow.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showRemaining by remember { mutableStateOf(false) }

    val duration = state.durationMs.coerceAtLeast(1L)
    val position = if (dragging) dragValue.toLong() else state.positionMs
    val durationText = remember(state.durationMs) { Formatters.duration(state.durationMs) }

    if (dragging) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "${Formatters.duration(position)} / $durationText",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Formatters.duration(position),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        BilibiliSeekBar(
            positionMs = position,
            bufferedMs = state.bufferedMs,
            durationMs = duration,
            dragging = dragging,
            onSeekPreview = {
                dragging = true
                dragValue = it
            },
            onSeekCommit = {
                dragging = false
                onSeekCommit(it.toLong())
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            text = if (showRemaining) {
                "-" + Formatters.duration((state.durationMs - position).coerceAtLeast(0))
            } else {
                durationText
            },
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { showRemaining = !showRemaining },
            ),
        )
    }
}

/** 哔哩哔哩风格纤细进度条：细圆轨 + 缓冲条 + 主题色已播轨 + 紧凑白点游标（拖动加粗） */
@Composable
private fun BilibiliSeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    dragging: Boolean,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val total = durationMs.coerceAtLeast(1L).toFloat()
    val fraction = (positionMs.toFloat() / total).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedMs.toFloat() / total).coerceIn(0f, 1f)
    val latestTotal by rememberUpdatedState(total)
    val latestOnPreview by rememberUpdatedState(onSeekPreview)
    val latestOnCommit by rememberUpdatedState(onSeekCommit)

    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.coerceAtLeast(1).toFloat()
                    var currentTarget = ((down.position.x / w).coerceIn(0f, 1f)) * latestTotal
                    latestOnPreview(currentTarget)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        currentTarget = ((change.position.x / w).coerceIn(0f, 1f)) * latestTotal
                        latestOnPreview(currentTarget)
                        change.consume()
                    }
                    latestOnCommit(currentTarget)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        ) {
            val y = size.height / 2f
            val trackHeight = if (dragging) 4.5.dp.toPx() else 3.dp.toPx()
            val thumbRadius = if (dragging) 7.5.dp.toPx() else 5.5.dp.toPx()
            val usableWidth = (size.width - thumbRadius * 2f).coerceAtLeast(1f)
            val startX = thumbRadius
            val endX = startX + usableWidth
            val playedX = startX + usableWidth * fraction
            val bufferedX = startX + usableWidth * bufferedFraction

            // 底层未播轨道
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(startX, y),
                end = Offset(endX, y),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            // 缓冲进度
            if (bufferedX > playedX) {
                drawLine(
                    color = Color.White.copy(alpha = 0.50f),
                    start = Offset(playedX, y),
                    end = Offset(bufferedX, y),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round,
                )
            }
            // 已播进度
            drawLine(
                color = activeColor,
                start = Offset(startX, y),
                end = Offset(playedX, y),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            // 拖动光晕 + 白色圆点游标
            if (dragging) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.35f),
                    radius = thumbRadius + 4.dp.toPx(),
                    center = Offset(playedX, y),
                )
            }
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(playedX, y),
            )
            drawCircle(
                color = activeColor,
                radius = thumbRadius * 0.36f,
                center = Offset(playedX, y),
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
