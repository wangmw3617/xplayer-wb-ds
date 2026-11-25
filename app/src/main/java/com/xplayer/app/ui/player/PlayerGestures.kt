package com.xplayer.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xplayer.app.R
import com.xplayer.app.data.datastore.EdgeDeadZone
import com.xplayer.app.data.datastore.GestureAction
import com.xplayer.app.data.datastore.GesturePrefs
import com.xplayer.app.data.datastore.SingleTapAction
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

private sealed interface GestureHud {
    data class Seek(val targetMs: Long, val deltaMs: Long) : GestureHud
    data class Level(val kind: VerticalGestureAction, val percent: Int) : GestureHud
    data class Speed(val value: Float) : GestureHud
    data class Zoom(val scale: Float) : GestureHud
}

/**
 * 播放页手势层：亮度/音量、横向快进退、双击分区、长按倍速（可滑动无级调速）、
 * 双指缩放平移，全部按 GesturePrefs 里的用户配置执行。
 */
@Composable
fun PlayerGestureOverlay(
    prefs: GesturePrefs,
    locked: Boolean,
    positionMsProvider: () -> Long,
    durationMsProvider: () -> Long,
    brightness: Float,
    volume: Float,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onSingleTap: () -> Unit,
    onDoubleTap: (GestureAction) -> Unit,
    onLongPressSpeed: (Float?) -> Unit,
    onZoom: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hud by remember { mutableStateOf<GestureHud?>(null) }
    val scope = rememberCoroutineScope()
    val edgePx = with(LocalDensity.current) { prefs.edgeDeadZone.dp.dp.toPx() }
    var lastTapAt by remember { mutableStateOf(0L) }
    var pendingTap by remember { mutableStateOf<Job?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomPan by remember { mutableStateOf(Offset.Zero) }

    // 通过无参回调读取最新进度与时长，避免 positionMs 每 300-400ms 刷新引发手势层无效重组
    val latestPositionMsProvider by rememberUpdatedState(positionMsProvider)
    val latestDurationMsProvider by rememberUpdatedState(durationMsProvider)
    val latestBrightness by rememberUpdatedState(brightness)
    val latestVolume by rememberUpdatedState(volume)
    val latestOnBrightness by rememberUpdatedState(onBrightness)
    val latestOnVolume by rememberUpdatedState(onVolume)
    val latestOnSeekPreview by rememberUpdatedState(onSeekPreview)
    val latestOnSeekCommit by rememberUpdatedState(onSeekCommit)
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestOnLongPressSpeed by rememberUpdatedState(onLongPressSpeed)
    val latestOnZoom by rememberUpdatedState(onZoom)

    fun fireDoubleTap(action: GestureAction) {
        if (action != GestureAction.DISABLED) latestOnDoubleTap(action)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(prefs, locked) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (locked) {
                        // 锁屏状态只响应单击（用于唤出解锁按钮）
                        var anyMove = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.positionChange() != Offset.Zero) anyMove = true
                            if (!change.pressed) break
                        }
                        if (!anyMove) latestOnSingleTap()
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
                    val startPositionMs = latestPositionMsProvider()
                    val totalDurationMs = latestDurationMsProvider()
                    var seekTarget = startPositionMs
                    val baseBrightness = latestBrightness
                    val baseVolume = latestVolume
                    var baseLevel = 0f
                    var levelInitialized = false

                    val longPressJob = scope.launch {
                        delay(prefs.longPressDelayMs)
                        if (prefs.longPressEnabled && !moved) {
                            longPressActive = true
                            zoomScale = 1f
                            latestOnLongPressSpeed(prefs.longPressSpeed)
                            hud = GestureHud.Speed(prefs.longPressSpeed)
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.count { it.pressed }
                        if (pointers >= 2) {
                            multiTouch = true
                            longPressJob.cancel()
                            if (prefs.zoomMode != ZoomMode.DISABLED) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                zoomScale = (zoomScale * zoom).coerceIn(0.5f, 4f)
                                zoomPan += pan
                                if (prefs.zoomMode == ZoomMode.FREE) {
                                    latestOnZoom(zoomScale, zoomPan)
                                    hud = GestureHud.Zoom((zoomScale * 100).roundToInt().toFloat())
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
                        if (delta != Offset.Zero) {
                            moved = true
                            longPressJob.cancel()
                            accumulated += delta

                            if (longPressActive && prefs.longPressDragSpeed) {
                                val speed = (prefs.longPressSpeed + accumulated.x / size.width * 3f)
                                    .coerceIn(0.5f, 4f)
                                latestOnLongPressSpeed(speed)
                                hud = GestureHud.Speed(speed)
                                change.consume()
                                continue
                            }

                            if (dragMode == null && !longPressActive) {
                                dragMode = if (abs(accumulated.x) > abs(accumulated.y)) DragMode.HORIZONTAL else DragMode.VERTICAL
                                if (dragMode == DragMode.HORIZONTAL && !prefs.horizontalSeekEnabled) {
                                    dragMode = DragMode.VERTICAL
                                }
                            }

                            when (dragMode) {
                                DragMode.HORIZONTAL -> {
                                    val span = prefs.seekSpan.resolveMillis(totalDurationMs)
                                    val deltaMs = (accumulated.x / size.width * span).toLong()
                                    seekTarget = (startPositionMs + deltaMs).coerceIn(0L, totalDurationMs.coerceAtLeast(0L))
                                    hud = GestureHud.Seek(seekTarget, seekTarget - startPositionMs)
                                    latestOnSeekPreview(seekTarget)
                                }
                                DragMode.VERTICAL -> {
                                    val action = if (zone == HorizontalZone.RIGHT) prefs.rightAction else prefs.leftAction
                                    if (action == VerticalGestureAction.DISABLED) {
                                        dragMode = null
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
                                            hud = GestureHud.Level(action, (value * 100).toInt())
                                        }
                                        VerticalGestureAction.VOLUME -> {
                                            if (!levelInitialized) {
                                                baseLevel = baseVolume
                                                levelInitialized = true
                                            }
                                            val max = if (prefs.volumeBoost) 2f else 1f
                                            val value = (baseLevel + fraction).coerceIn(0f, max)
                                            latestOnVolume(value)
                                            hud = GestureHud.Level(action, (value * 100).toInt())
                                        }
                                        VerticalGestureAction.DISABLED -> Unit
                                    }
                                }
                                null -> Unit
                            }
                            change.consume()
                        }
                    }

                    longPressJob.cancel()
                    if (longPressActive) {
                        latestOnLongPressSpeed(null)
                        hud = null
                        zoomScale = 1f
                    } else if (moved && dragMode == DragMode.HORIZONTAL) {
                        latestOnSeekCommit(seekTarget)
                        hud = null
                    } else if (moved) {
                        hud = null
                    } else if (!multiTouch) {
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
                            pendingTap?.cancel()
                            pendingTap = scope.launch {
                                delay(300)
                                latestOnSingleTap()
                            }
                        }
                    }
                }
            },
    ) {
        hud?.let { current ->
            GestureHudView(current = current, modifier = Modifier.align(Alignment.Center))
        }
    }
}

private enum class DragMode { HORIZONTAL, VERTICAL }

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
                        text = "${current.percent}%",
                        color = Color.White,
                        fontSize = 15.sp,
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
                        text = "${Formatters.speed(current.value)}×",
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
                    text = stringResource(R.string.player_zoom, current.scale),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
