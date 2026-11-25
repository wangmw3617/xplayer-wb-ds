package com.xplayer.app.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xplayer.app.R
import com.xplayer.app.data.datastore.GestureAction
import com.xplayer.app.data.datastore.GesturePrefs
import com.xplayer.app.data.datastore.VerticalGestureAction
import com.xplayer.app.data.datastore.ZoomMode
import com.xplayer.app.ui.icons.XPlayerIcons
import com.xplayer.app.util.Formatters
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class HorizontalZone { LEFT, CENTER, RIGHT }
private enum class DragMode { HORIZONTAL, VERTICAL }

private sealed interface GestureHud {
    data class Seek(val targetMs: Long, val deltaMs: Long) : GestureHud
    data class Level(val kind: VerticalGestureAction, val percent: Int) : GestureHud
    data class Speed(val value: Float) : GestureHud
    data class Zoom(val scalePercent: Int) : GestureHud
    data class Message(val textRes: Int) : GestureHud
}

/**
 * 播放页手势层：亮度/音量（从当前实时值起点调节）、横向快进退、双击分区、长按倍速（支持恢复原本倍速）、
 * 双指自由缩放平移与快捷缩放裁剪切换；使用 touchSlop 消抖并在取消时兜底清理状态。
 */
@Composable
fun PlayerGestureOverlay(
    prefs: GesturePrefs,
    locked: Boolean,
    zoomScale: Float,
    zoomPan: Offset,
    positionMsProvider: () -> Long,
    durationMsProvider: () -> Long,
    brightnessProvider: () -> Float,
    volumeProvider: () -> Float,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onSingleTap: () -> Unit,
    onDoubleTap: (GestureAction) -> Unit,
    onLongPressStart: (Float) -> Unit,
    onLongPressSpeed: (Float) -> Unit,
    onLongPressEnd: () -> Unit,
    onZoom: (Float, Offset) -> Unit,
    onAspectPinchToggle: (crop: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hud by remember { mutableStateOf<GestureHud?>(null) }
    val scope = rememberCoroutineScope()
    val edgePx = with(LocalDensity.current) { prefs.edgeDeadZone.dp.dp.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var lastTapAt by remember { mutableStateOf(0L) }
    var pendingTap by remember { mutableStateOf<Job?>(null) }

    val latestZoomScale by rememberUpdatedState(zoomScale)
    val latestZoomPan by rememberUpdatedState(zoomPan)
    val latestPositionMsProvider by rememberUpdatedState(positionMsProvider)
    val latestDurationMsProvider by rememberUpdatedState(durationMsProvider)
    val latestBrightnessProvider by rememberUpdatedState(brightnessProvider)
    val latestVolumeProvider by rememberUpdatedState(volumeProvider)
    val latestOnBrightness by rememberUpdatedState(onBrightness)
    val latestOnVolume by rememberUpdatedState(onVolume)
    val latestOnSeekCommit by rememberUpdatedState(onSeekCommit)
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestOnLongPressStart by rememberUpdatedState(onLongPressStart)
    val latestOnLongPressSpeed by rememberUpdatedState(onLongPressSpeed)
    val latestOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val latestOnZoom by rememberUpdatedState(onZoom)
    val latestOnAspectPinchToggle by rememberUpdatedState(onAspectPinchToggle)

    fun fireDoubleTap(action: GestureAction) {
        if (action != GestureAction.DISABLED) latestOnDoubleTap(action)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(prefs, locked) {
                val slopSq = touchSlop * touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pendingTap?.cancel()

                    if (locked) {
                        var movedDistSq = 0f
                        var totalDelta = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            totalDelta += change.positionChange()
                            movedDistSq = totalDelta.x * totalDelta.x + totalDelta.y * totalDelta.y
                            if (!change.pressed) break
                        }
                        if (movedDistSq <= slopSq) latestOnSingleTap()
                        return@awaitEachGesture
                    }

                    // 边缘死区：交给系统手势（返回、下拉通知栏）
                    if (down.position.x < edgePx ||
                        down.position.x > size.width - edgePx ||
                        down.position.y < edgePx ||
                        down.position.y > size.height - edgePx
                    ) {
                        return@awaitEachGesture
                    }

                    val zone = when {
                        down.position.x < size.width / 3f -> HorizontalZone.LEFT
                        down.position.x > size.width * 2f / 3f -> HorizontalZone.RIGHT
                        else -> HorizontalZone.CENTER
                    }

                    var moved = false
                    var multiTouch = false
                    var longPressActive = false
                    var dragMode: DragMode? = null
                    var accumulated = Offset.Zero
                    var zoomAccumulated = 1f
                    var aspectToggledInPinch = false
                    var currentZoom = latestZoomScale
                    var currentPan = latestZoomPan
                    val startPositionMs = latestPositionMsProvider()
                    val totalDurationMs = latestDurationMsProvider()
                    var seekTarget = startPositionMs
                    val baseBrightness = latestBrightnessProvider()
                    val baseVolume = latestVolumeProvider()
                    var baseLevel = 0f
                    var levelInitialized = false
                    var completedNormally = false

                    val longPressJob = scope.launch {
                        delay(prefs.longPressDelayMs)
                        if (prefs.longPressEnabled && !moved && !multiTouch) {
                            longPressActive = true
                            latestOnLongPressStart(prefs.longPressSpeed)
                            hud = GestureHud.Speed(prefs.longPressSpeed)
                        }
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.count { it.pressed }
                            if (pointers >= 2) {
                                multiTouch = true
                                longPressJob.cancel()
                                if (prefs.zoomMode != ZoomMode.DISABLED) {
                                    val zoomDelta = event.calculateZoom()
                                    val panDelta = event.calculatePan()
                                    when (prefs.zoomMode) {
                                        ZoomMode.FREE -> {
                                            currentZoom = (currentZoom * zoomDelta).coerceIn(1f, 4f)
                                            if (currentZoom <= 1.02f) {
                                                currentZoom = 1f
                                                currentPan = Offset.Zero
                                            } else {
                                                val maxX = size.width * (currentZoom - 1f) / 2f
                                                val maxY = size.height * (currentZoom - 1f) / 2f
                                                val nextPan = currentPan + panDelta
                                                currentPan = Offset(
                                                    x = nextPan.x.coerceIn(-maxX, maxX),
                                                    y = nextPan.y.coerceIn(-maxY, maxY),
                                                )
                                            }
                                            latestOnZoom(currentZoom, currentPan)
                                            hud = GestureHud.Zoom((currentZoom * 100f).roundToInt())
                                        }
                                        ZoomMode.TOGGLE_ASPECT -> {
                                            zoomAccumulated *= zoomDelta
                                            if (!aspectToggledInPinch) {
                                                if (zoomAccumulated > 1.18f) {
                                                    aspectToggledInPinch = true
                                                    latestOnAspectPinchToggle(true)
                                                    hud = GestureHud.Message(R.string.player_zoom_crop)
                                                } else if (zoomAccumulated < 0.85f) {
                                                    aspectToggledInPinch = true
                                                    latestOnAspectPinchToggle(false)
                                                    hud = GestureHud.Message(R.string.player_zoom_fit)
                                                }
                                            }
                                        }
                                        ZoomMode.DISABLED -> Unit
                                    }
                                }
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.positionChange()
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (multiTouch) {
                                change.consume()
                                continue
                            }

                            accumulated += delta
                            if (!moved && (accumulated.x * accumulated.x + accumulated.y * accumulated.y) <= slopSq) {
                                continue
                            }
                            moved = true
                            longPressJob.cancel()

                            if (longPressActive && prefs.longPressDragSpeed) {
                                val speed = (prefs.longPressSpeed + accumulated.x / size.width * 3f)
                                    .coerceIn(0.5f, 4f)
                                latestOnLongPressSpeed(speed)
                                hud = GestureHud.Speed(speed)
                                change.consume()
                                continue
                            }

                            if (dragMode == null && !longPressActive) {
                                val horizontalDominant = abs(accumulated.x) > abs(accumulated.y)
                                dragMode = when {
                                    horizontalDominant && prefs.horizontalSeekEnabled -> DragMode.HORIZONTAL
                                    !horizontalDominant -> DragMode.VERTICAL
                                    else -> {
                                        // 关闭了水平滑动快进时，忽略纯水平拖动手势避免误触亮度/音量
                                        change.consume()
                                        continue
                                    }
                                }
                            }

                            when (dragMode) {
                                DragMode.HORIZONTAL -> {
                                    if (totalDurationMs > 0L) {
                                        val span = prefs.seekSpan.resolveMillis(totalDurationMs)
                                        val deltaMs = (accumulated.x / size.width * span).toLong()
                                        seekTarget = (startPositionMs + deltaMs).coerceIn(0L, totalDurationMs)
                                        hud = GestureHud.Seek(seekTarget, seekTarget - startPositionMs)
                                    }
                                }
                                DragMode.VERTICAL -> {
                                    val action = when (zone) {
                                        HorizontalZone.RIGHT -> prefs.rightAction
                                        HorizontalZone.LEFT, HorizontalZone.CENTER -> prefs.leftAction
                                    }
                                    if (action == VerticalGestureAction.DISABLED) {
                                        change.consume()
                                        continue
                                    }
                                    val fraction = -accumulated.y / size.height * prefs.swipeSensitivity
                                    when (action) {
                                        VerticalGestureAction.BRIGHTNESS -> {
                                            if (!levelInitialized) {
                                                baseLevel = baseBrightness
                                                levelInitialized = true
                                            }
                                            val value = (baseLevel + fraction).coerceIn(0.02f, 1f)
                                            latestOnBrightness(value)
                                            hud = GestureHud.Level(action, (value * 100f).roundToInt())
                                        }
                                        VerticalGestureAction.VOLUME -> {
                                            if (!levelInitialized) {
                                                baseLevel = baseVolume
                                                levelInitialized = true
                                            }
                                            val max = if (prefs.volumeBoost) 2f else 1f
                                            val value = (baseLevel + fraction).coerceIn(0f, max)
                                            latestOnVolume(value)
                                            hud = GestureHud.Level(action, (value * 100f).roundToInt())
                                        }
                                        VerticalGestureAction.DISABLED -> Unit
                                    }
                                }
                                null -> Unit
                            }
                            change.consume()
                        }
                        completedNormally = true
                    } finally {
                        longPressJob.cancel()
                        if (longPressActive) {
                            latestOnLongPressEnd()
                        }
                        if (completedNormally) {
                            if (dragMode == DragMode.HORIZONTAL && moved && totalDurationMs > 0L) {
                                latestOnSeekCommit(seekTarget)
                            } else if (!moved && !multiTouch && !longPressActive) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapAt < 300L) {
                                    pendingTap?.cancel()
                                    lastTapAt = 0L
                                    when (zone) {
                                        HorizontalZone.LEFT -> fireDoubleTap(prefs.doubleLeftAction)
                                        HorizontalZone.CENTER -> fireDoubleTap(prefs.doubleCenterAction)
                                        HorizontalZone.RIGHT -> fireDoubleTap(prefs.doubleRightAction)
                                    }
                                } else {
                                    lastTapAt = now
                                    pendingTap = scope.launch {
                                        delay(300L)
                                        latestOnSingleTap()
                                    }
                                }
                            }
                        }
                        hud = null
                    }
                }
            },
    ) {
        hud?.let { current ->
            GestureHudView(current = current, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun GestureHudView(current: GestureHud, modifier: Modifier = Modifier) {
    when (current) {
        is GestureHud.Seek -> {
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = Formatters.duration(current.targetMs),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = (if (current.deltaMs >= 0) "+" else "-") +
                            Formatters.duration(abs(current.deltaMs)),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
        is GestureHud.Level -> {
            Surface(
                modifier = modifier.size(96.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.7f),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (current.kind == VerticalGestureAction.BRIGHTNESS) {
                            XPlayerIcons.Brightness
                        } else {
                            XPlayerIcons.Volume
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            if (current.kind == VerticalGestureAction.BRIGHTNESS) {
                                R.string.player_brightness
                            } else {
                                R.string.player_volume
                            },
                            current.percent,
                        ),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        is GestureHud.Speed -> {
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = XPlayerIcons.Bolt,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_long_speed, Formatters.speed(current.value)),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        is GestureHud.Zoom -> {
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.65f),
            ) {
                Text(
                    text = stringResource(R.string.player_zoom, current.scalePercent),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        is GestureHud.Message -> {
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Text(
                    text = stringResource(current.textRes),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
