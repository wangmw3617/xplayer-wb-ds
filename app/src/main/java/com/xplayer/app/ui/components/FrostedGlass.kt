package com.xplayer.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * 磨砂玻璃背景采集状态：在底层滚动容器上通过 [frostedBackdropSource] 录制帧内容，
 * 供上层悬浮控件实时采样并做背景模糊。
 */
@Stable
class FrostedBackdropState internal constructor(
    internal val layer: GraphicsLayer,
) {
    internal var sourceOriginInRoot by mutableStateOf(Offset.Zero)
}

/** 全局底栏等跨页悬浮控件共享的背景采样源 */
val LocalAppBackdropState = staticCompositionLocalOf<FrostedBackdropState?> { null }

@Composable
fun rememberFrostedBackdropState(): FrostedBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { FrostedBackdropState(layer) }
}

/**
 * 标记为磨砂玻璃的背景采样源；Android 12+ 才会录制离屏图层，低版本保持零开销直通。
 */
fun Modifier.frostedBackdropSource(state: FrostedBackdropState): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this
            .onGloballyPositioned { coords ->
                state.sourceOriginInRoot = coords.positionInRoot()
            }
            .drawWithContent {
                state.layer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(state.layer)
            }
    } else {
        this
    }

/**
 * 圆形 / 全圆角磨砂玻璃控件容器：
 * - Android 12+（API 31+）对底层采样图层做外扩防黑边高斯模糊，再叠加冷白/深灰半透材质与高光描边；
 * - Android 10–11 自动回退为高质感半透玻璃材质 + 顶部高光描边。
 */
@Composable
fun FrostedGlassSurface(
    backdropState: FrostedBackdropState,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backdropColor = MaterialTheme.colorScheme.background
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceHighest = MaterialTheme.colorScheme.surfaceContainerHighest
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val fillBrush = remember(isDark, blurSupported, surfaceHigh, surfaceHighest) {
        if (isDark) {
            Brush.verticalGradient(
                0f to surfaceHighest.copy(alpha = if (blurSupported) 0.56f else 0.86f),
                1f to surfaceHigh.copy(alpha = if (blurSupported) 0.38f else 0.76f),
            )
        } else {
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (blurSupported) 0.72f else 0.90f),
                1f to surfaceHigh.copy(alpha = if (blurSupported) 0.48f else 0.80f),
            )
        }
    }

    val borderBrush = remember(isDark, outlineVariant) {
        if (isDark) {
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.28f),
                0.5f to Color.White.copy(alpha = 0.12f),
                1f to Color.White.copy(alpha = 0.06f),
            )
        } else {
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.92f),
                0.5f to outlineVariant.copy(alpha = 0.35f),
                1f to outlineVariant.copy(alpha = 0.25f),
            )
        }
    }

    var originInRoot by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val blurRadiusPx = remember(density) { with(density) { 18.dp.toPx() } }
    val bleedPx = remember(density) { with(density) { 20.dp.roundToPx() } }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords -> originInRoot = coords.positionInRoot() }
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = contentAlignment,
    ) {
        if (blurSupported) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
                        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
                        // 先外扩采样区域再由父级 clip 裁回目标形状，避免高斯模糊在边缘产生暗角
                        val expandedW = (w + bleedPx * 2).coerceAtLeast(0)
                        val expandedH = (h + bleedPx * 2).coerceAtLeast(0)
                        val placeable = measurable.measure(Constraints.fixed(expandedW, expandedH))
                        layout(w, h) {
                            placeable.placeRelative(-bleedPx, -bleedPx)
                        }
                    }
                    .graphicsLayer {
                        renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                    }
                    .drawBehind {
                        drawRect(backdropColor)
                        if (backdropState.layer.size.width > 0 && backdropState.layer.size.height > 0) {
                            val offset = originInRoot - backdropState.sourceOriginInRoot
                            translate(
                                left = -offset.x + bleedPx,
                                top = -offset.y + bleedPx,
                            ) {
                                drawLayer(backdropState.layer)
                            }
                        }
                    },
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(fillBrush)
                .border(width = 1.dp, brush = borderBrush, shape = shape),
        )

        content()
    }
}

/** 悬浮顶栏后的柔和渐变遮罩，保留状态栏可读性的同时让滚动内容能透出磨砂玻璃质感 */
@Composable
fun FrostedHeaderScrim(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to background.copy(alpha = 0.78f),
                0.50f to background.copy(alpha = 0.36f),
                1f to Color.Transparent,
            ),
        ),
    )
}
