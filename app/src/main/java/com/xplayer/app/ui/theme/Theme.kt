package com.xplayer.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.xplayer.app.data.datastore.AnimationLevel
import com.xplayer.app.data.datastore.AppearancePrefs
import com.xplayer.app.data.datastore.ColorSeed
import com.xplayer.app.data.datastore.DarkSurfaceMode
import com.xplayer.app.data.datastore.ThemeMode

val LocalAnimationLevel = staticCompositionLocalOf { AnimationLevel.STANDARD }

fun AnimationLevel.scaleDuration(baseMs: Int): Int = when (this) {
    AnimationLevel.STANDARD -> baseMs
    AnimationLevel.SNAPPY -> (baseMs * 0.55f).toInt().coerceAtLeast(1)
    AnimationLevel.OFF -> 0
}

/**
 * 由种子色推导 Material 3 配色：用 HSL 近似 tonal palette，
 * 避免为 6 套主题各自手写 40 个色值。
 */
fun seedColorScheme(seed: Color, dark: Boolean, amoled: Boolean = false): ColorScheme {
    val hue = hueOf(seed)
    return if (dark) {
        darkColorScheme(
            primary = Color.hsl(hue, 0.62f, 0.80f),
            onPrimary = Color.hsl(hue, 0.60f, 0.18f),
            primaryContainer = Color.hsl(hue, 0.48f, 0.34f),
            onPrimaryContainer = Color.hsl(hue, 0.60f, 0.94f),
            secondary = Color.hsl(hue, 0.26f, 0.76f),
            onSecondary = Color.hsl(hue, 0.30f, 0.18f),
            secondaryContainer = Color.hsl(hue, 0.30f, 0.30f),
            onSecondaryContainer = Color.hsl(hue, 0.35f, 0.92f),
            tertiary = Color.hsl((hue + 48f) % 360f, 0.48f, 0.78f),
            onTertiary = Color.hsl((hue + 48f) % 360f, 0.50f, 0.18f),
            tertiaryContainer = Color.hsl((hue + 48f) % 360f, 0.35f, 0.32f),
            onTertiaryContainer = Color.hsl((hue + 48f) % 360f, 0.40f, 0.92f),
            background = if (amoled) Color.Black else Color(0xFF101216),
            onBackground = Color(0xFFEAECEF),
            surface = if (amoled) Color.Black else Color(0xFF101216),
            onSurface = Color(0xFFEAECEF),
            surfaceVariant = if (amoled) Color(0xFF18181C) else Color(0xFF232730),
            onSurfaceVariant = Color(0xFFA8AFB9),
            surfaceContainerLowest = if (amoled) Color.Black else Color(0xFF0C0D10),
            surfaceContainerLow = if (amoled) Color(0xFF0C0C0E) else Color(0xFF191C22),
            surfaceContainer = if (amoled) Color(0xFF131316) else Color(0xFF1E2128),
            surfaceContainerHigh = if (amoled) Color(0xFF1B1B1F) else Color(0xFF252932),
            surfaceContainerHighest = if (amoled) Color(0xFF242429) else Color(0xFF2E333E),
            outline = Color.hsl(hue, 0.08f, 0.50f),
            outlineVariant = if (amoled) Color(0xFF25252B) else Color(0xFF2D313B),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = Color.hsl(hue, 0.72f, 0.92f),
            onPrimaryContainer = Color.hsl(hue, 0.65f, 0.22f),
            secondary = Color.hsl(hue, 0.30f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = Color.hsl(hue, 0.48f, 0.92f),
            onSecondaryContainer = Color.hsl(hue, 0.45f, 0.22f),
            tertiary = Color.hsl((hue + 48f) % 360f, 0.45f, 0.42f),
            onTertiary = Color.White,
            tertiaryContainer = Color.hsl((hue + 48f) % 360f, 0.60f, 0.90f),
            onTertiaryContainer = Color.hsl((hue + 48f) % 360f, 0.45f, 0.18f),
            // 复刻 FlClash 浅灰画布 + 纯白圆角卡片的明快层级
            background = Color(0xFFF2F3F7),
            onBackground = Color(0xFF181A1F),
            surface = Color(0xFFF2F3F7),
            onSurface = Color(0xFF181A1F),
            surfaceVariant = Color(0xFFE7E9EF),
            onSurfaceVariant = Color(0xFF5D636F),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color(0xFFF7F8FA),
            surfaceContainerHigh = Color(0xFFE9EBF0),
            surfaceContainerHighest = Color(0xFFE0E3EA),
            outline = Color(0xFF8C929E),
            outlineVariant = Color(0xFFE1E4EC),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}

private fun hueOf(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}

@Composable
fun rememberAppColorScheme(appearance: AppearancePrefs): ColorScheme {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val pureBlack = dark && appearance.darkSurfaceMode == DarkSurfaceMode.PURE_BLACK
    val context = LocalContext.current

    return when {
        appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (pureBlack) {
                dynamic.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF0C0C0E),
                    surfaceContainer = Color(0xFF131316),
                    surfaceContainerHigh = Color(0xFF1B1B1F),
                    surfaceContainerHighest = Color(0xFF242429),
                )
            } else {
                dynamic
            }
        }
        else -> seedColorScheme(Color(appearance.seed.seed), dark = dark, amoled = pureBlack)
    }
}

@Composable
fun XPlayerTheme(appearance: AppearancePrefs, content: @Composable () -> Unit) {
    val colorScheme = rememberAppColorScheme(appearance)
    CompositionLocalProvider(LocalAnimationLevel provides appearance.animationLevel) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}

val SeedPreviewColors: List<Color>
    get() = ColorSeed.entries.map { Color(it.seed) }
