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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.ui.media.MediaInfoScreen
import com.xplayer.app.ui.theme.XPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 文件管理器「打开方式」里的独立入口：只看信息，不起播。
 *
 * 选中视频/音频后直接摊开容器与逐轨参数；
 * 想播的话页面底部有「用 XPlayer 播放」，把同一个 Uri 交给主界面。
 */
class MediaInfoActivity : ComponentActivity() {

    private var mediaUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        mediaUri = mediaUriOf(intent)
        if (mediaUri == null) {
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
                LaunchedEffect(darkSurface) {
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
                        val path = remember(uri) { filePathOf(uri) }
                        val title = remember(uri, path) { path?.let { File(it).name }.orEmpty() }
                        MediaInfoScreen(
                            sourceType = if (path != null) "local" else "uri",
                            key = path ?: uri.toString(),
                            title = title,
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
     * 交给主界面起播。content:// 保留本 Activity 以防部分网盘 provider 提前回收临时授权；
     * file:// 无跨进程 Uri 授权负担，可直接 finish。
     */
    private fun play(uri: Uri) {
        lifecycleScope.launch {
            val mime = withContext(Dispatchers.IO) {
                runCatching { contentResolver.getType(uri) }.getOrNull()
            }
            val target = Intent(this@MediaInfoActivity, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivity(target) }
            if (uri.scheme.equals("file", ignoreCase = true)) {
                finish()
            }
        }
    }

    private fun mediaUriOf(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        return uri.takeIf { scheme == "content" || scheme == "file" }
    }
}
