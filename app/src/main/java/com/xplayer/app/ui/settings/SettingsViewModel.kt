package com.xplayer.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xplayer.app.AppContainer
import com.xplayer.app.R
import com.xplayer.app.data.datastore.AppSettings
import com.xplayer.app.data.datastore.EnginePrefs
import com.xplayer.app.data.local.FolderBlacklistEntity
import com.xplayer.app.data.local.MpvShaderEntity
import com.xplayer.app.data.scanner.ScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    private val _versionName = MutableStateFlow("1.0.0")
    val versionName: StateFlow<String> = _versionName.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val ver = runCatching {
                container.application.packageManager
                    .getPackageInfo(container.application.packageName, 0)
                    .versionName
            }.getOrNull()
            if (!ver.isNullOrBlank()) _versionName.value = ver
            _cacheSizeBytes.value = dirSize(container.application.cacheDir)
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun resetGestures() {
        viewModelScope.launch {
            container.settingsRepository.resetGestures()
            _messages.emit(R.string.gesture_reset_done)
        }
    }

    fun scan() {
        container.mediaScanner.triggerScan()
    }

    fun clearCache() {
        viewModelScope.launch {
            val remaining = withContext(Dispatchers.IO) {
                container.application.cacheDir.listFiles()?.forEach { child ->
                    child.deleteRecursively()
                }
                dirSize(container.application.cacheDir)
            }
            _cacheSizeBytes.value = remaining
            _messages.emit(R.string.settings_cache_cleared)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.playbackRepository.clear()
            _messages.emit(R.string.settings_history_cleared)
        }
    }

    fun saveMpvConf(text: String) {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(engine = it.engine.copy(mpvConf = text, customMpvConfEnabled = true))
            }
            _messages.emit(R.string.mpv_conf_saved)
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
            _messages.emit(R.string.mpv_conf_reset_done)
        }
    }

    fun importShader(uri: Uri) {
        viewModelScope.launch {
            val result = container.shaderRepository.import(uri)
            val msg = when {
                result.isSuccess -> R.string.mpv_shader_imported
                result.exceptionOrNull()?.message in SHADER_VALIDATION_ERRORS -> R.string.mpv_shader_invalid
                else -> R.string.videos_operation_failed
            }
            _messages.emit(msg)
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
            _messages.emit(R.string.mpv_shader_deleted)
        }
    }

    fun unhideFolder(path: String) {
        viewModelScope.launch {
            container.folderRepository.unhide(path)
            container.mediaScanner.triggerScan()
        }
    }

    private fun dirSize(root: File?): Long {
        if (root == null || !root.exists()) return 0L
        if (root.isFile) return root.length()
        return root.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }

    private companion object {
        val SHADER_VALIDATION_ERRORS = setOf("INVALID_SHADER_EXT", "INVALID_SHADER_SIZE", "EMPTY_SHADER")
    }
}
