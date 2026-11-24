package com.xplayer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.ui.media.MediaInfoScreen
import com.xplayer.app.ui.theme.XPlayerTheme
import java.io.File
import java.util.Locale

/**
 * 文件管理器「打开方式」里的独立入口：只看信息，不起播。
 *
 * 和 MediaInfo 那类分析工具一样，选中视频/音频后直接摊开容器与逐轨参数；
 * 想播的话页面底部有「用 XPlayer 播放」，把同一个 Uri 交给主界面。
 */
class MediaInfoActivity : ComponentActivity() {

    private var mediaUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        mediaUri = mediaUriOf(intent)
        if (mediaUri == null) {
            // 没有数据就没有可分析的对象（例如被别处空启动）
            finish()
            return
        }

        setContent {
            val container = (application as XPlayerApp).container
            val settings by container.settingsRepository.settings
                .collectAsState(initial = AppSettings())

            XPlayerTheme(settings.appearance) {
                val view = LocalView.current
                val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkSurface
                        isAppearanceLightNavigationBars = !darkSurface
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    val uri = mediaUri
                    if (uri != null) {
                        val path = filePathOf(uri)
                        MediaInfoScreen(
                            sourceType = if (path != null) "local" else "uri",
                            key = path ?: uri.toString(),
                            title = path?.let { File(it).name }.orEmpty(),
                            onBack = { finish() },
                            onPlay = { play(uri) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mediaUriOf(intent)?.let { mediaUri = it }
    }

    /** file:// 直接当本地文件读，省一层 ContentResolver */
    private fun filePathOf(uri: Uri): String? =
        if (uri.scheme?.lowercase(Locale.US) == "file") uri.path else null

    /**
     * 交给主界面起播。这里不 finish：Uri 权限随本 Activity 存活，
     * 提前结束时部分 provider 会在播放器刚起来时把权限收回。
     */
    private fun play(uri: Uri) {
        val target = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, runCatching { contentResolver.getType(uri) }.getOrNull())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(target) }
    }

    private fun mediaUriOf(intent: Intent?): Uri? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
}
