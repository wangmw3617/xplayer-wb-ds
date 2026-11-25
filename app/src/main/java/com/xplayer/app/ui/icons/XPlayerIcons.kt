package com.xplayer.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 内置矢量图标集。
 * 刻意不引入 material-icons-extended（约 40MB dex），只保留本应用真正用到的图形，
 * 让 arm64 单包体积尽量贴近 PRD 目标。
 */
object XPlayerIcons {

    val Pause: ImageVector by lazy {
        vector("Pause", Vec("M7,5 h3 v14 h-3 z", filled = true), Vec("M14,5 h3 v14 h-3 z", filled = true))
    }

    val SkipNext: ImageVector by lazy {
        vector(
            "SkipNext",
            Vec("M6,5 L15,12 L6,19 Z", filled = true),
            Vec("M17,5 h2 v14 h-2 z", filled = true),
        )
    }

    val SkipPrevious: ImageVector by lazy {
        vector(
            "SkipPrevious",
            Vec("M18,5 L9,12 L18,19 Z", filled = true),
            Vec("M5,5 h2 v14 h-2 z", filled = true),
        )
    }

    val Folder: ImageVector by lazy {
        vector(
            "Folder",
            Vec("M3,7 a1.5,1.5 0 0 1 1.5,-1.5 h4 L11,8 h8.5 A1.5,1.5 0 0 1 21,9.5 v8 A1.5,1.5 0 0 1 19.5,19 h-15 A1.5,1.5 0 0 1 3,17.5 z", filled = true),
        )
    }

    val Video: ImageVector by lazy {
        vector(
            "Video",
            Vec("M4,6.5 h16 v11 h-16 z"),
            Vec("M10,9.5 L16,12 L10,14.5 Z", filled = true),
        )
    }

    val Subtitles: ImageVector by lazy {
        vector(
            "Subtitles",
            Vec("M3,6 h18 v12 h-18 z"),
            Vec("M6,13.5 h7 v1.8 h-7 z", filled = true),
            Vec("M15,13.5 h3 v1.8 h-3 z", filled = true),
            Vec("M6,10.2 h5 v1.6 h-5 z", filled = true),
        )
    }

    val Speed: ImageVector by lazy {
        vector(
            "Speed",
            Vec("M12,4 a8,8 0 1,0 0,16 a8,8 0 1,0 0,-16"),
            Vec("M12,12 L16.2,8.2"),
            Vec("M12,11 a1.2,1.2 0 1,0 0,2.4 a1.2,1.2 0 1,0 0,-2.4", filled = true),
        )
    }

    val Camera: ImageVector by lazy {
        vector(
            "Camera",
            Vec("M4,7.5 h4 L9.5,5.5 h5 L16,7.5 h4 v12 h-16 z"),
            Vec("M12,9.6 a3.4,3.4 0 1,0 0,6.8 a3.4,3.4 0 1,0 0,-6.8"),
        )
    }

    val PictureInPicture: ImageVector by lazy {
        vector(
            "PictureInPicture",
            Vec("M3,5.5 h18 v13 h-18 z"),
            Vec("M12,12.5 h7 v4.5 h-7 z", filled = true),
        )
    }

    val Fullscreen: ImageVector by lazy {
        vector(
            "Fullscreen",
            Vec("M4,9.5 V4 h5.5"),
            Vec("M14.5,4 H20 v5.5"),
            Vec("M20,14.5 V20 h-5.5"),
            Vec("M9.5,20 H4 v-5.5"),
        )
    }

    val AspectRatio: ImageVector by lazy {
        vector(
            "AspectRatio",
            Vec("M3,6.5 h18 v11 h-18 z"),
            Vec("M6.5,10 h4 v1.6 h-4 z", filled = true),
            Vec("M13.5,13.4 h4 v1.6 h-4 z", filled = true),
        )
    }

    val Volume: ImageVector by lazy {
        vector(
            "Volume",
            Vec("M4,10 h3 l4,-3.5 v11 L7,14 H4 z", filled = true),
            Vec("M14.5,9.2 a5,5 0 0,1 0,5.6"),
            Vec("M17,6.6 a8.5,8.5 0 0,1 0,10.8"),
        )
    }

    val VolumeOff: ImageVector by lazy {
        vector(
            "VolumeOff",
            Vec("M4,10 h3 l4,-3.5 v11 L7,14 H4 z", filled = true),
            Vec("M15,9.5 L20,14.5"),
            Vec("M20,9.5 L15,14.5"),
        )
    }

    val Brightness: ImageVector by lazy {
        vector(
            "Brightness",
            Vec("M12,7 a5,5 0 1,0 0,10 a5,5 0 1,0 0,-10"),
            Vec("M12,2.8 V5"),
            Vec("M12,19 V21.2"),
            Vec("M2.8,12 H5"),
            Vec("M19,12 H21.2"),
            Vec("M5.4,5.4 L7,7"),
            Vec("M17,17 L18.6,18.6"),
            Vec("M18.6,5.4 L17,7"),
            Vec("M7,17 L5.4,18.6"),
        )
    }

    val Headphones: ImageVector by lazy {
        vector(
            "Headphones",
            Vec("M4,14.5 v-2.5 a8,8 0 0,1 16,0 v2.5"),
            Vec("M4,13.5 h3.2 v7 H4 z", filled = true),
            Vec("M16.8,13.5 H20 v7 h-3.2 z", filled = true),
        )
    }

    val Tune: ImageVector by lazy {
        vector(
            "Tune",
            Vec("M3,7 h10.5"),
            Vec("M18.5,7 H21"),
            Vec("M3,12 h5"),
            Vec("M13,12 h8"),
            Vec("M3,17 h10.5"),
            Vec("M18.5,17 H21"),
            Vec("M16.5,5.4 a1.6,1.6 0 1,0 0,3.2 a1.6,1.6 0 1,0 0,-3.2", filled = true),
            Vec("M11,10.4 a1.6,1.6 0 1,0 0,3.2 a1.6,1.6 0 1,0 0,-3.2", filled = true),
            Vec("M16.5,15.4 a1.6,1.6 0 1,0 0,3.2 a1.6,1.6 0 1,0 0,-3.2", filled = true),
        )
    }

    val Grid: ImageVector by lazy {
        vector(
            "Grid",
            Vec("M4,4 h6.5 v6.5 H4 z", filled = true),
            Vec("M13.5,4 H20 v6.5 h-6.5 z", filled = true),
            Vec("M4,13.5 h6.5 V20 H4 z", filled = true),
            Vec("M13.5,13.5 H20 V20 h-6.5 z", filled = true),
        )
    }

    val DragHandle: ImageVector by lazy {
        vector(
            "DragHandle",
            Vec("M8,9 h8 v1.9 H8 z", filled = true),
            Vec("M8,13.1 h8 v1.9 H8 z", filled = true),
        )
    }

    val Bolt: ImageVector by lazy {
        vector("Bolt", Vec("M13.2,2 L5.4,13.2 h4.9 L9.6,22 l7.9,-11.4 h-5.1 z", filled = true))
    }

    val Memory: ImageVector by lazy {
        vector(
            "Memory",
            Vec("M6,7 h12 v10 h-12 z"),
            Vec("M9,4 v3"),
            Vec("M15,4 v3"),
            Vec("M9,17 v3"),
            Vec("M15,17 v3"),
            Vec("M3,10 h3"),
            Vec("M18,10 h3"),
            Vec("M3,14 h3"),
            Vec("M18,14 h3"),
        )
    }

    val Palette: ImageVector by lazy {
        vector(
            "Palette",
            Vec("M12,3.5 a8.5,8.5 0 1,0 0,17 a2,2 0 0,0 1.6,-3.2 a2,2 0 0,1 1.6,-3.2 h1.4 a3.9,3.9 0 0,0 3.9,-3.9 A8.9,8.9 0 0,0 12,3.5"),
            Vec("M8.2,9.6 a1.4,1.4 0 1,0 0,2.8 a1.4,1.4 0 1,0 0,-2.8", filled = true),
            Vec("M12.4,7.2 a1.4,1.4 0 1,0 0,2.8 a1.4,1.4 0 1,0 0,-2.8", filled = true),
            Vec("M16.2,9.6 a1.4,1.4 0 1,0 0,2.8 a1.4,1.4 0 1,0 0,-2.8", filled = true),
        )
    }

    val Storage: ImageVector by lazy {
        vector(
            "Storage",
            Vec("M3.5,5.5 h17 v5 h-17 z"),
            Vec("M3.5,13.5 h17 v5 h-17 z"),
            Vec("M6.5,7.4 h1.8 v1.2 H6.5 z", filled = true),
            Vec("M6.5,15.4 h1.8 v1.2 H6.5 z", filled = true),
        )
    }

    val Zoom: ImageVector by lazy {
        vector(
            "Zoom",
            Vec("M10.5,4 a6.5,6.5 0 1,0 0,13 a6.5,6.5 0 1,0 0,-13"),
            Vec("M15.4,15.4 L20.5,20.5"),
            Vec("M7.8,10.5 h5.4"),
            Vec("M10.5,7.8 v5.4"),
        )
    }

    /** 快退/快进 10 秒用的回环箭头（forward 时由调用方水平镜像） */
    val Replay: ImageVector by lazy {
        vector(
            "Replay",
            Vec(
                "m12,5 v-4 l-5,5 5,5 v-4 c3.3,0 6,2.7 6,6 s-2.7,6 -6,6 -6,-2.7 -6,-6 h-2 " +
                    "c0,4.4 3.6,8 8,8 s8,-3.6 8,-8 -3.6,-8 -8,-8 z",
                filled = true,
            ),
        )
    }

    /** 屏幕旋转 */
    val RotateScreen: ImageVector by lazy {
        vector(
            "RotateScreen",
            Vec("M8.4,3.6 h5.2 a1.5,1.5 0 0 1 1.5,1.5 v13.8 a1.5,1.5 0 0 1 -1.5,1.5 h-5.2 a1.5,1.5 0 0 1 -1.5,-1.5 v-13.8 a1.5,1.5 0 0 1 1.5,-1.5"),
            Vec("M17.2,8 a6.4,6.4 0 0 1 0,8"),
            Vec("M19.2,6.2 L17.2,8 L16.7,5.5"),
        )
    }

    /** 上下双箭头（复刻 FlClash 行内选择指示） */
    val UnfoldMore: ImageVector by lazy {
        vector(
            "UnfoldMore",
            Vec("M8.5,9.5 L12,6 L15.5,9.5"),
            Vec("M8.5,14.5 L12,18 L15.5,14.5"),
        )
    }
}

private class Vec(val data: String, val filled: Boolean = false, val width: Float = 1.7f)

private fun vector(name: String, vararg paths: Vec): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path.data),
                fill = if (path.filled) SolidColor(Color.Black) else null,
                stroke = if (path.filled) null else SolidColor(Color.Black),
                strokeLineWidth = path.width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
