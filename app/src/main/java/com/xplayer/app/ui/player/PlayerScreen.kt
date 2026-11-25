package com.xplayer.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.xplayer.app.data.datastore.RotationMode
import com.xplayer.app.data.datastore.SingleTapAction
import com.xplayer.app.data.datastore.labelRes
import com.xplayer.app.data.source.ContentUriSource
import com.xplayer.app.data.source.LocalFileSource
import com.xplayer.app.data.source.NetworkUrlSource
import com.xplayer.app.data.source.SidecarSubtitle
import com.xplayer.app.player.ScreenshotSaver
import com.xplayer.app.player.engine.EngineAvailability
import com.xplayer.app.player.engine.EngineType
import com.xplayer.app.player.engine.PlayerUiState
import com.xplayer.app.player.pip.PipController
import com.xplayer.app.player.pip.PlayerCommand
import com.xplayer.app.player.pip.PlayerCommandBus
import com.xplayer.app.player.service.PlaybackService
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class PlayerSheet { TRACKS, SPEED, ASPECT, SUBTITLE, SETTINGS }

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenMediaInfo: () -> Unit = {},
) {
    val engine by viewModel.engine.collectAsState()
    // 剥离高频 positionMs / bufferedMs 避免每 300ms 触发根播放页重组
    val state by remember(viewModel) {
        viewModel.state
            .map { it.copy(positionMs = 0L, bufferedMs = 0L) }
            .distinctUntilChanged()
    }.collectAsState(initial = viewModel.state.value.copy(positionMs = 0L, bufferedMs = 0L))
    val settings by viewModel.settings.collectAsState()
    val hasPrevious by viewModel.canPrevious.collectAsState()
    val hasNext by viewModel.canNext.collectAsState()

    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val bridgeOwner = remember { Any() }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }
    var lockButtonVisible by remember { mutableStateOf(true) }
    var inPipMode by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var videoBounds by remember { mutableStateOf<Rect?>(null) }
    var preLongPressSpeed by remember { mutableFloatStateOf(state.speed) }
    var volumeStepHint by remember { mutableIntStateOf(-1) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volumeLevel by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it > 0f } ?: 0.5f,
        )
    }

    fun bumpControlsTimer() {
        interactionTick++
    }

    fun currentLiveVolume(): Float = if (volumeLevel > 1f) {
        volumeLevel
    } else {
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
    }

    fun applyBrightness(value: Float) {
        val clamped = value.coerceIn(0.02f, 1f)
        if (kotlin.math.abs(clamped - brightness) < 0.005f) return
        brightness = clamped
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = clamped }
        }
    }

    fun applyVolume(value: Float) {
        volumeLevel = value
        if (value > 0f && viewModel.muted) {
            viewModel.setMutedState(false)
        }
        if (value <= 1f) {
            val targetStep = (value * maxVolume).roundToInt()
            if (targetStep != volumeStepHint) {
                volumeStepHint = targetStep
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
            }
            viewModel.controller.setVolume(1f)
        } else {
            if (volumeStepHint != maxVolume) {
                volumeStepHint = maxVolume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            }
            viewModel.controller.setVolume(value)
        }
    }

    fun stopBackgroundServiceIfRunning() {
        if (viewModel.backgroundServiceActive) {
            PlaybackService.liveState.value?.let { bgState ->
                if (bgState.uri == currentPlaybackUri(viewModel)) {
                    viewModel.syncFromBackgroundAudio(bgState.positionMs, resumePlaying = false)
                }
            }
            context.stopService(Intent(context, PlaybackService::class.java))
            PlaybackService.clearLiveState()
            viewModel.backgroundServiceActive = false
        }
    }

    fun togglePlayPauseSafe() {
        bumpControlsTimer()
        stopBackgroundServiceIfRunning()
        viewModel.controller.togglePlayPause()
    }

    fun enterPipOrWarn() {
        val ok = activity?.let {
            PipController.enter(
                activity = it,
                videoWidth = state.videoWidth,
                videoHeight = state.videoHeight,
                isPlaying = state.isPlaying,
                autoEnter = settings.playback.backgroundBehavior == BackgroundBehavior.PIP &&
                    state.isPlaying && !state.ended,
                sourceBounds = videoBounds,
            )
        } ?: false
        if (!ok) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.player_pip_failed)) }
        }
    }

    fun startBgAudioAndPause() {
        startBackgroundAudio(context, viewModel, viewModel.state.value.positionMs)?.let {
            viewModel.backgroundServiceActive = true
            viewModel.controller.pause()
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

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun handleAction(action: GestureAction) {
        bumpControlsTimer()
        when (action) {
            GestureAction.SEEK_BACK -> viewModel.controller.seekBy(-settings.gesture.leftStepMs)
            GestureAction.SEEK_FORWARD -> viewModel.controller.seekBy(settings.gesture.rightStepMs)
            GestureAction.PLAY_PAUSE -> togglePlayPauseSafe()
            GestureAction.PREV -> viewModel.controller.previous()
            GestureAction.NEXT -> viewModel.controller.next()
            GestureAction.MUTE -> {
                val nextMuted = !viewModel.muted
                viewModel.setMutedState(nextMuted)
                val targetVol = if (nextMuted) 0f else if (volumeLevel > 1f) volumeLevel else 1f
                viewModel.controller.setVolume(targetVol)
            }
            GestureAction.SCREENSHOT -> takeScreenshot()
            GestureAction.ASPECT_TOGGLE -> {
                viewModel.setAspect(if (viewModel.aspectMode == AspectMode.FIT) AspectMode.CROP else AspectMode.FIT)
                viewModel.resetZoom()
            }
            GestureAction.DISABLED -> Unit
        }
    }

    // 返回键拦截：先关面板或拦截锁屏退出，避免误退播放页
    BackHandler {
        when {
            sheet == PlayerSheet.ASPECT || sheet == PlayerSheet.SUBTITLE -> sheet = PlayerSheet.SETTINGS
            sheet != null -> sheet = null
            viewModel.locked -> {
                lockButtonVisible = true
                scope.launch { snackbar.showSnackbar(context.getString(R.string.player_unlock_back_hint)) }
            }
            else -> onBack()
        }
    }

    // 播放页保活亮屏 + 沉浸式全屏 & 浅色系统栏图标（防止浅色主题下滑动唤出状态栏时图标变黑不可见）
    DisposableEffect(view, state.isPlaying) {
        view.keepScreenOn = state.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            // 新播放页若已接管（外链切歌场景），不破坏新页的全屏与方向状态
            if (!PlayerActivityBridge.isCurrentOwner(bridgeOwner) && PlayerActivityBridge.autoPip != null) {
                return@onDispose
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 提示消息
    LaunchedEffect(Unit) {
        viewModel.notice.collect { notice ->
            val text = notice.arg?.let { context.getString(notice.messageRes, it) }
                ?: context.getString(notice.messageRes)
            snackbar.showSnackbar(text)
        }
    }

    // 断点续播提示
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
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.controller.seekTo(0L, persistImmediate = true)
            }
        }
    }

    // 控制栏自动隐藏（拖动进度条期间保持常亮）
    LaunchedEffect(controlsVisible, state.isPlaying, viewModel.locked, sheet, isSeeking, interactionTick) {
        if (controlsVisible && state.isPlaying && !viewModel.locked && sheet == null && !isSeeking) {
            delay(4_000L)
            controlsVisible = false
        }
    }

    // 锁屏提示按钮 3.5 秒后自动淡出
    LaunchedEffect(viewModel.locked, lockButtonVisible) {
        if (viewModel.locked && lockButtonVisible) {
            delay(3_500L)
            lockButtonVisible = false
        }
    }

    // 按视频分辨率自动设置横竖屏（同步写入 rotationMode，避免首次旋转需点击两次）
    LaunchedEffect(state.videoWidth, state.videoHeight) {
        if (!viewModel.orientationApplied && state.videoWidth > 0 && state.videoHeight > 0) {
            viewModel.orientationApplied = true
            val initialMode = if (state.videoWidth > state.videoHeight) {
                RotationMode.LANDSCAPE
            } else {
                RotationMode.PORTRAIT
            }
            viewModel.setRotation(initialMode)
            applyRotation(activity, initialMode)
        }
    }

    // 后台行为 + 返回前台状态无缝回传
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestState by rememberUpdatedState(state)
    val latestSettings by rememberUpdatedState(settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val inPip = activity?.isInPictureInPictureMode == true
                    when (latestSettings.playback.backgroundBehavior) {
                        BackgroundBehavior.PAUSE -> if (!inPip) viewModel.controller.pause()
                        BackgroundBehavior.AUDIO -> {
                            if (!inPip && latestState.isPlaying) {
                                if (canPostNotifications()) {
                                    startBgAudioAndPause()
                                } else {
                                    // Android 13+ 无通知权限时不强行启动前台服务，改为稳妥暂停
                                    viewModel.controller.pause()
                                }
                            }
                        }
                        BackgroundBehavior.PIP -> {
                            if (!inPip && latestState.isPlaying) {
                                viewModel.controller.pause()
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    val bgState = PlaybackService.liveState.value
                    if (viewModel.backgroundServiceActive || bgState != null) {
                        val sameMedia = bgState?.uri == currentPlaybackUri(viewModel)
                        context.stopService(Intent(context, PlaybackService::class.java))
                        PlaybackService.clearLiveState()
                        if (sameMedia && bgState != null) {
                            viewModel.syncFromBackgroundAudio(bgState.positionMs, resumePlaying = bgState.isPlaying)
                        } else {
                            viewModel.backgroundServiceActive = false
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // PiP 桥接注册（带 owner token + 退出自动重置 autoEnter）
    DisposableEffect(activity) {
        PlayerActivityBridge.register(
            owner = bridgeOwner,
            autoPip = {
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
                            autoEnter = true,
                            sourceBounds = videoBounds,
                        )
                    } else {
                        false
                    }
                }
            },
            onPipChanged = { inPip ->
                inPipMode = inPip
                controlsVisible = !inPip
                if (inPip) {
                    sheet = null
                } else {
                    val window = activity?.window
                    window?.let {
                        WindowCompat.getInsetsController(it, it.decorView).hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            },
        )
        onDispose {
            PlayerActivityBridge.unregister(bridgeOwner)
            activity?.let { act ->
                if (PlayerActivityBridge.autoPip == null) {
                    PipController.updateActions(
                        activity = act,
                        videoWidth = 0,
                        videoHeight = 0,
                        isPlaying = false,
                        autoEnter = false,
                    )
                }
            }
        }
    }

    // PiP 窗口遥控动作
    LaunchedEffect(Unit) {
        PlayerCommandBus.commands.collect { command ->
            when (command) {
                PlayerCommand.TOGGLE_PLAY -> togglePlayPauseSafe()
                PlayerCommand.REWIND -> viewModel.controller.seekBy(-10_000L)
                PlayerCommand.FORWARD -> viewModel.controller.seekBy(10_000L)
            }
        }
    }

    // 同步系统自动进画中画及窗口图标
    LaunchedEffect(
        state.isPlaying,
        state.ended,
        settings.playback.backgroundBehavior,
        state.videoWidth,
        state.videoHeight,
        videoBounds,
    ) {
        val act = activity ?: return@LaunchedEffect
        PipController.updateActions(
            activity = act,
            videoWidth = state.videoWidth,
            videoHeight = state.videoHeight,
            isPlaying = state.isPlaying,
            autoEnter = settings.playback.backgroundBehavior == BackgroundBehavior.PIP &&
                state.isPlaying && !state.ended,
            sourceBounds = videoBounds,
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        engine?.let { currentEngine ->
            key(currentEngine) {
                AndroidView(
                    factory = { ctx -> currentEngine.createVideoView(ctx) },
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInWindow()
                            videoBounds = Rect(
                                b.left.roundToInt(),
                                b.top.roundToInt(),
                                b.right.roundToInt(),
                                b.bottom.roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            scaleX = viewModel.zoomScale
                            scaleY = viewModel.zoomScale
                            translationX = viewModel.zoomOffset.x
                            translationY = viewModel.zoomOffset.y
                        },
                )
            }
            LaunchedEffect(currentEngine) {
                viewModel.startPlayback()
            }
        }

        if (!inPipMode) {
            PlayerGestureOverlay(
                prefs = settings.gesture,
                locked = viewModel.locked,
                zoomScale = viewModel.zoomScale,
                zoomPan = viewModel.zoomOffset,
                positionMsProvider = { viewModel.state.value.positionMs },
                durationMsProvider = { viewModel.state.value.durationMs },
                brightnessProvider = { brightness },
                volumeProvider = ::currentLiveVolume,
                onBrightness = ::applyBrightness,
                onVolume = ::applyVolume,
                onSeekCommit = { target ->
                    bumpControlsTimer()
                    viewModel.controller.seekTo(target)
                },
                onSingleTap = {
                    if (viewModel.locked) {
                        lockButtonVisible = !lockButtonVisible
                    } else if (settings.gesture.singleTapAction == SingleTapAction.PLAY_PAUSE) {
                        togglePlayPauseSafe()
                        controlsVisible = true
                    } else {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) bumpControlsTimer()
                    }
                },
                onDoubleTap = ::handleAction,
                onLongPressStart = { targetSpeed ->
                    preLongPressSpeed = state.speed
                    viewModel.controller.setSpeed(targetSpeed)
                },
                onLongPressSpeed = { speed -> viewModel.controller.setSpeed(speed) },
                onLongPressEnd = { viewModel.controller.setSpeed(preLongPressSpeed) },
                onZoom = { scale, offset -> viewModel.updateZoom(scale, offset) },
                onAspectPinchToggle = { crop ->
                    viewModel.setAspect(if (crop) AspectMode.CROP else AspectMode.FIT)
                    viewModel.resetZoom()
                },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !viewModel.locked && !inPipMode && sheet == null && state.error == null,
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
                    title = viewModel.activeTitle,
                    onBack = onBack,
                    onRotate = {
                        bumpControlsTimer()
                        val entries = RotationMode.entries
                        val next = entries[(entries.indexOf(viewModel.rotationMode) + 1) % entries.size]
                        viewModel.setRotation(next)
                        applyRotation(activity, next)
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(R.string.player_rotate_mode, context.getString(next.labelRes())),
                            )
                        }
                    },
                    onPip = { enterPipOrWarn() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                if (state.ended && !state.isPlaying) {
                    PlayerEndedOverlay(
                        hasNext = hasNext,
                        onReplay = {
                            bumpControlsTimer()
                            viewModel.controller.seekTo(0L, persistImmediate = true)
                            togglePlayPauseSafe()
                        },
                        onNext = {
                            bumpControlsTimer()
                            viewModel.controller.next()
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    PlayerCenterControls(
                        isPlaying = state.isPlaying,
                        onRewind = {
                            bumpControlsTimer()
                            viewModel.controller.seekBy(-10_000L)
                        },
                        onForward = {
                            bumpControlsTimer()
                            viewModel.controller.seekBy(10_000L)
                        },
                        onTogglePlay = ::togglePlayPauseSafe,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                PlayerBottomBar(
                    progressState = viewModel.state,
                    speed = state.speed,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onDraggingChanged = { active ->
                        isSeeking = active
                        if (!active) bumpControlsTimer()
                    },
                    onSeekCommit = {
                        bumpControlsTimer()
                        viewModel.controller.seekTo(it)
                    },
                    onSpeed = { sheet = PlayerSheet.SPEED },
                    onTracks = { sheet = PlayerSheet.TRACKS },
                    onPrevious = {
                        bumpControlsTimer()
                        viewModel.controller.previous()
                    },
                    onNext = {
                        bumpControlsTimer()
                        viewModel.controller.next()
                    },
                    onLock = {
                        viewModel.toggleLock()
                        lockButtonVisible = true
                        controlsVisible = false
                    },
                    onSettings = { sheet = PlayerSheet.SETTINGS },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // 错误恢复浮层（当双内核兜底仍失败或外链无法读取时提供重试与切核）
        state.error?.let { errorCode ->
            if (!inPipMode) {
                PlayerErrorOverlay(
                    message = errorCode,
                    canSwitchEngine = EngineAvailability.mpvAvailable &&
                        viewModel.controller.currentSource() !is ContentUriSource,
                    currentEngine = state.engine,
                    onRetry = { viewModel.controller.retry() },
                    onSwitchEngine = {
                        val alt = if (state.engine == EngineType.MEDIA3) EngineType.MPV else EngineType.MEDIA3
                        viewModel.switchEngine(alt)
                    },
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // 锁屏按钮：随点击显隐并在 3.5 秒无操作后淡出
        AnimatedVisibility(
            visible = viewModel.locked && lockButtonVisible && !inPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .displayCutoutPadding()
                .padding(start = 24.dp),
        ) {
            Box(
                modifier = Modifier
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

        if (state.isBuffering && !state.ended && state.error == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        if (!inPipMode) {
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .displayCutoutPadding()
                    .navigationBarsPadding()
                    .padding(bottom = 104.dp),
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
                onImportFailed = {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.player_subtitle_import_failed)) }
                },
                onDismiss = { sheet = null },
            )
            PlayerSheet.SPEED -> SpeedSheet(
                current = state.speed,
                onSpeed = { viewModel.controller.setSpeed(it) },
                onDismiss = { sheet = null },
            )
            PlayerSheet.ASPECT -> AspectSheet(
                current = viewModel.aspectMode,
                onSelect = {
                    viewModel.setAspect(it)
                    viewModel.resetZoom()
                },
                onDismiss = { sheet = null },
            )
            PlayerSheet.SUBTITLE -> SubtitleStyleSheet(
                style = viewModel.subtitleStyle,
                onChange = { viewModel.applySubtitleStyle(it) },
                onDismiss = { sheet = null },
            )
            PlayerSheet.SETTINGS -> PlayerSettingsSheet(
                engine = state.engine,
                mpvAvailable = EngineAvailability.mpvAvailable,
                mpvBlockedByUri = viewModel.controller.currentSource() is ContentUriSource,
                aspectLabel = stringResource(viewModel.aspectMode.labelRes()),
                rotationMode = viewModel.rotationMode,
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
                    startBgAudioAndPause()
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.player_background_started))
                    }
                },
                onPip = {
                    sheet = null
                    enterPipOrWarn()
                },
                onRotate = { mode ->
                    viewModel.setRotation(mode)
                    applyRotation(activity, mode)
                },
                onMediaInfo = {
                    sheet = null
                    onOpenMediaInfo()
                },
                onLock = {
                    sheet = null
                    viewModel.toggleLock()
                    lockButtonVisible = true
                    controlsVisible = false
                },
                onDismiss = { sheet = null },
            )
            null -> Unit
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    message: String,
    canSwitchEngine: Boolean,
    currentEngine: EngineType,
    onRetry: () -> Unit,
    onSwitchEngine: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 360.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xDD14161E),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.player_error),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.common_back), color = Color.White)
                }
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_retry))
                }
            }
            if (canSwitchEngine) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = onSwitchEngine) {
                    val altName = if (currentEngine == EngineType.MEDIA3) "libmpv" else "Media3"
                    Text(
                        text = "${stringResource(R.string.player_switch_engine)} → $altName",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerEndedOverlay(
    hasNext: Boolean,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onReplay),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.68f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.player_replay), color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        if (hasNext) {
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onNext),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = XPlayerIcons.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.player_next),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
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
    onDraggingChanged: (Boolean) -> Unit,
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
            onDraggingChanged = onDraggingChanged,
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
                    text = stringResource(R.string.settings_speed_x, Formatters.speed(speed)),
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
    onDraggingChanged: (Boolean) -> Unit,
    onSeekCommit: (Long) -> Unit,
) {
    val state by stateFlow.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showRemaining by remember { mutableStateOf(false) }

    val duration = state.durationMs
    val seekable = duration > 0L
    val safeDuration = duration.coerceAtLeast(1L)
    val position = if (dragging) dragValue.toLong() else state.positionMs
    val positionSec = position / 1000L
    val posText = remember(positionSec) { Formatters.duration(position) }
    val durationText = remember(duration) { Formatters.duration(duration) }

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
                    text = "$posText / $durationText",
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
            text = posText,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        BilibiliSeekBar(
            enabled = seekable,
            positionFractionProvider = { (position.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) },
            bufferedFractionProvider = { (state.bufferedMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) },
            durationFloat = safeDuration.toFloat(),
            dragging = dragging,
            onSeekPreview = {
                if (!dragging) {
                    dragging = true
                    onDraggingChanged(true)
                }
                dragValue = it
            },
            onSeekCommit = {
                dragging = false
                onDraggingChanged(false)
                onSeekCommit(it.toLong())
            },
            onSeekCancel = {
                if (dragging) {
                    dragging = false
                    onDraggingChanged(false)
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        val remainingText = remember(showRemaining, positionSec, duration) {
            if (showRemaining) {
                "-" + Formatters.duration((duration - position).coerceAtLeast(0L))
            } else {
                durationText
            }
        }
        Text(
            text = remainingText,
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

/** 哔哩哔哩风格纤细进度条：细圆轨 + 缓冲条 + 主题色已播轨 + 紧凑白点游标（触控对齐绘制区且在 Canvas 阶段读取进度） */
@Composable
private fun BilibiliSeekBar(
    enabled: Boolean,
    positionFractionProvider: () -> Float,
    bufferedFractionProvider: () -> Float,
    durationFloat: Float,
    dragging: Boolean,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    onSeekCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val latestTotal by rememberUpdatedState(durationFloat)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnPreview by rememberUpdatedState(onSeekPreview)
    val latestOnCommit by rememberUpdatedState(onSeekCommit)
    val latestOnCancel by rememberUpdatedState(onSeekCancel)
    val latestPosFraction by rememberUpdatedState(positionFractionProvider)
    val latestBufFraction by rememberUpdatedState(bufferedFractionProvider)

    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!latestEnabled) return@awaitEachGesture
                    val thumbMarginPx = 5.5.dp.toPx()
                    val usableWidth = (size.width - thumbMarginPx * 2f).coerceAtLeast(1f)
                    fun targetAt(x: Float): Float =
                        (((x - thumbMarginPx) / usableWidth).coerceIn(0f, 1f)) * latestTotal

                    var currentTarget = targetAt(down.position.x)
                    var completed = false
                    latestOnPreview(currentTarget)
                    down.consume()

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                completed = true
                                break
                            }
                            currentTarget = targetAt(change.position.x)
                            latestOnPreview(currentTarget)
                            change.consume()
                        }
                    } finally {
                        if (completed) {
                            latestOnCommit(currentTarget)
                        } else {
                            latestOnCancel()
                        }
                    }
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
            val fraction = latestPosFraction()
            val bufferedFraction = latestBufFraction()
            val playedX = startX + usableWidth * fraction
            val bufferedX = startX + usableWidth * bufferedFraction

            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(startX, y),
                end = Offset(endX, y),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            if (bufferedX > playedX) {
                drawLine(
                    color = Color.White.copy(alpha = 0.50f),
                    start = Offset(playedX, y),
                    end = Offset(bufferedX, y),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round,
                )
            }
            drawLine(
                color = activeColor,
                start = Offset(startX, y),
                end = Offset(playedX, y),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            if (enabled) {
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

private fun applyRotation(activity: Activity?, mode: RotationMode) {
    activity?.requestedOrientation = when (mode) {
        RotationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        RotationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        RotationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

private fun currentPlaybackUri(viewModel: PlayerViewModel): String? {
    val source = viewModel.controller.currentSource() ?: return null
    return when (source) {
        is LocalFileSource -> Uri.fromFile(File(source.path)).toString()
        is ContentUriSource -> source.uri
        is NetworkUrlSource -> source.url
    }
}

private fun startBackgroundAudio(
    context: Context,
    viewModel: PlayerViewModel,
    positionMs: Long,
): Intent? {
    val source = viewModel.controller.currentSource() ?: return null
    val uri = currentPlaybackUri(viewModel) ?: return null
    val speed = viewModel.state.value.speed
    val intent = PlaybackService.startIntent(
        context = context,
        uri = uri,
        title = source.displayName,
        positionMs = positionMs,
        speed = speed,
        headersJson = viewModel.activeHeadersJson,
    )
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
