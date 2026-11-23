package com.xplayer.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xplayer.app.AppContainer
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.datastore.EnginePrefs
import com.xplayer.app.data.local.FolderBlacklistEntity
import com.xplayer.app.data.local.MpvShaderEntity
import com.xplayer.app.data.scanner.ScanState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val scanState: StateFlow<ScanState> = container.mediaScanner.state

    val videoCount: StateFlow<Int> = container.videoRepository.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val shaders: StateFlow<List<MpvShaderEntity>> = container.shaderRepository.shaders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hiddenFolders: StateFlow<List<FolderBlacklistEntity>> = container.folderRepository.blacklisted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val versionName: String = runCatching {
        container.application.packageManager
            .getPackageInfo(container.application.packageName, 0)
            .versionName
    }.getOrNull() ?: "1.0.0"

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun resetGestures() {
        viewModelScope.launch {
            container.settingsRepository.resetGestures()
            _messages.emit(com.xplayer.app.R.string.gesture_reset_done)
        }
    }

    fun scan() {
        viewModelScope.launch { container.mediaScanner.scan() }
    }

    fun saveMpvConf(text: String) {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(engine = it.engine.copy(mpvConf = text, customMpvConfEnabled = true))
            }
            _messages.emit(com.xplayer.app.R.string.mpv_conf_saved)
        }
    }

    fun resetMpvConf() {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(
                    engine = it.engine.copy(
                        mpvConf = EnginePrefs.DEFAULT_MPV_CONF,
                        customMpvConfEnabled = false,
                    ),
                )
            }
            _messages.emit(com.xplayer.app.R.string.mpv_conf_reset_done)
        }
    }

    fun importShader(uri: Uri) {
        viewModelScope.launch {
            val result = container.shaderRepository.import(uri)
            _messages.emit(
                if (result.isSuccess) {
                    com.xplayer.app.R.string.mpv_shader_imported
                } else {
                    com.xplayer.app.R.string.videos_operation_failed
                },
            )
        }
    }

    fun setShaderEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { container.shaderRepository.setEnabled(id, enabled) }
    }

    fun moveShader(id: Long, delta: Int) {
        viewModelScope.launch { container.shaderRepository.move(id, delta) }
    }

    fun deleteShader(entity: MpvShaderEntity) {
        viewModelScope.launch {
            container.shaderRepository.delete(entity)
            _messages.emit(com.xplayer.app.R.string.mpv_shader_deleted)
        }
    }

    fun unhideFolder(path: String) {
        viewModelScope.launch {
            container.folderRepository.unhide(path)
            container.mediaScanner.scan()
        }
    }
}
