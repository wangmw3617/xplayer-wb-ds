package com.xplayer.app

import android.app.Application
import com.xplayer.app.data.local.XPlayerDatabase
import com.xplayer.app.data.repository.FolderRepository
import com.xplayer.app.data.repository.PlaybackRepository
import com.xplayer.app.data.repository.ShaderRepository
import com.xplayer.app.data.repository.StreamRepository
import com.xplayer.app.data.repository.VideoRepository
import com.xplayer.app.data.scanner.MediaScanner
import com.xplayer.app.data.datastore.SettingsRepository

class XPlayerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * 极简手工依赖容器：单 app 模块下比 Hilt 更轻，且不会给 CI 引入注解处理器风险。
 */
class AppContainer(private val app: Application) {

    val application: Application get() = app

    val database: XPlayerDatabase by lazy { XPlayerDatabase.get(app) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(app) }

    val videoRepository: VideoRepository by lazy {
        VideoRepository(app, database.videoDao(), database.playbackHistoryDao())
    }

    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository(database.playbackHistoryDao()) }

    val folderRepository: FolderRepository by lazy { FolderRepository(database.folderBlacklistDao()) }

    val streamRepository: StreamRepository by lazy { StreamRepository(database.streamUrlDao()) }

    val shaderRepository: ShaderRepository by lazy { ShaderRepository(app, database.mpvShaderDao()) }

    val mediaScanner: MediaScanner by lazy {
        MediaScanner(app, videoRepository, folderRepository, settingsRepository)
    }
}

val Application.xplayerContainer: AppContainer
    get() = (this as XPlayerApp).container
