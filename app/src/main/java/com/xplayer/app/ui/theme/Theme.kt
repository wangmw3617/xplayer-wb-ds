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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.xplayer.app.data.datastore.AppearancePrefs
import com.xplayer.app.data.datastore.ColorSeed
import com.xplayer.app.data.datastore.ThemeMode

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
            background = if (amoled) Color.Black else Color.hsl(hue, 0.12f, 0.055f),
            onBackground = Color.hsl(hue, 0.10f, 0.93f),
            surface = if (amoled) Color.Black else Color.hsl(hue, 0.12f, 0.065f),
            onSurface = Color.hsl(hue, 0.10f, 0.93f),
            surfaceVariant = if (amoled) Color(0xFF161616) else Color.hsl(hue, 0.10f, 0.18f),
            onSurfaceVariant = Color.hsl(hue, 0.08f, 0.78f),
            surfaceContainerLowest = if (amoled) Color.Black else Color.hsl(hue, 0.12f, 0.04f),
            surfaceContainerLow = if (amoled) Color(0xFF0B0B0B) else Color.hsl(hue, 0.11f, 0.09f),
            surfaceContainer = if (amoled) Color(0xFF121212) else Color.hsl(hue, 0.11f, 0.115f),
            surfaceContainerHigh = if (amoled) Color(0xFF1A1A1A) else Color.hsl(hue, 0.10f, 0.15f),
            surfaceContainerHighest = if (amoled) Color(0xFF232323) else Color.hsl(hue, 0.10f, 0.19f),
            outline = Color.hsl(hue, 0.08f, 0.50f),
            outlineVariant = Color.hsl(hue, 0.08f, 0.28f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = Color.hsl(hue, 0.58f, 0.42f),
            onPrimary = Color.White,
            primaryContainer = Color.hsl(hue, 0.70f, 0.90f),
            onPrimaryContainer = Color.hsl(hue, 0.60f, 0.18f),
            secondary = Color.hsl(hue, 0.30f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = Color.hsl(hue, 0.45f, 0.90f),
            onSecondaryContainer = Color.hsl(hue, 0.35f, 0.16f),
            tertiary = Color.hsl((hue + 48f) % 360f, 0.45f, 0.42f),
            onTertiary = Color.White,
            tertiaryContainer = Color.hsl((hue + 48f) % 360f, 0.60f, 0.90f),
            onTertiaryContainer = Color.hsl((hue + 48f) % 360f, 0.45f, 0.18f),
            background = Color.hsl(hue, 0.35f, 0.985f),
            onBackground = Color.hsl(hue, 0.20f, 0.11f),
            surface = Color.hsl(hue, 0.35f, 0.985f),
            onSurface = Color.hsl(hue, 0.20f, 0.11f),
            surfaceVariant = Color.hsl(hue, 0.20f, 0.92f),
            onSurfaceVariant = Color.hsl(hue, 0.12f, 0.36f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.hsl(hue, 0.30f, 0.97f),
            surfaceContainer = Color.hsl(hue, 0.26f, 0.94f),
            surfaceContainerHigh = Color.hsl(hue, 0.22f, 0.91f),
            surfaceContainerHighest = Color.hsl(hue, 0.20f, 0.88f),
            outline = Color.hsl(hue, 0.10f, 0.50f),
            outlineVariant = Color.hsl(hue, 0.14f, 0.80f),
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
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val amoled = appearance.themeMode == ThemeMode.AMOLED
    val context = LocalContext.current

    return when {
        amoled -> seedColorScheme(Color(appearance.seed.seed), dark = true, amoled = true)
        appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> seedColorScheme(Color(appearance.seed.seed), dark = dark)
    }
}

@Composable
fun XPlayerTheme(appearance: AppearancePrefs, content: @Composable () -> Unit) {
    val colorScheme = rememberAppColorScheme(appearance)
    androidx.compose.material3.MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}

val SeedPreviewColors: List<Color>
    get() = ColorSeed.entries.map { Color(it.seed) }
