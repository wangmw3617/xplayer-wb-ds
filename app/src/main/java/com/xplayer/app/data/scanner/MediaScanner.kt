package com.xplayer.app.data.scanner

import android.content.Context
import android.os.Environment
import android.util.Log
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.datastore.SettingsRepository
import com.xplayer.app.data.repository.FolderRepository
import com.xplayer.app.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.util.Locale

data class ScanState(
    val scanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val finishedAt: Long = 0L,
    val error: String? = null,
)

/**
 * 本地媒体扫描器：广度遍历内置/外置存储，跳过隐藏目录、Android/data 与屏蔽目录，
 * 只对新增或已变更的文件重新提取元数据，保证二次扫描接近瞬时完成。
 */
class MediaScanner(
    private val context: Context,
    private val videoRepository: VideoRepository,
    private val folderRepository: FolderRepository,
    @Suppress("unused") private val settingsRepository: SettingsRepository,
) {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val extractor = MetadataExtractor(File(context.cacheDir, "thumbs"))

    suspend fun scan(): ScanState = mutex.withLock {
        if (_state.value.scanning) return@withLock _state.value
        withContext(Dispatchers.IO) { runScan() }
    }

    private suspend fun runScan(): ScanState {
        _state.value = ScanState(scanning = true)
        return try {
            val blacklist = folderRepository.paths()
            val existing = videoRepository.all().associateBy { it.path }
            val candidates = collectVideoFiles(blacklist)
            _state.value = _state.value.copy(total = candidates.size)

            val pending = ArrayList<VideoEntity>(32)
            val seen = HashSet<String>(candidates.size)
            candidates.forEachIndexed { index, file ->
                val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                seen += path
                val old = existing[path]
                val unchanged = old != null &&
                    old.sizeBytes == file.length() &&
                    old.dateModified == file.lastModified()
                if (!unchanged) {
                    runCatching { extractor.extract(file) }
                        .onFailure { Log.w(TAG, "extract failed: ${file.name}", it) }
                        .getOrNull()
                        ?.let { pending += it }
                }
                if (pending.size >= BATCH) {
                    videoRepository.upsertAll(pending.toList())
                    pending.clear()
                }
                if (index % 8 == 0 || index == candidates.lastIndex) {
                    _state.value = _state.value.copy(scanned = index + 1)
                }
                yield()
            }
            if (pending.isNotEmpty()) {
                videoRepository.upsertAll(pending.toList())
                pending.clear()
            }
            videoRepository.deleteMissing(seen)

            _state.value.copy(scanning = false, finishedAt = System.currentTimeMillis())
                .also { _state.value = it }
        } catch (error: Throwable) {
            Log.e(TAG, "scan failed", error)
            _state.value.copy(scanning = false, error = error.message).also { _state.value = it }
        }
    }

    private fun collectVideoFiles(blacklist: Set<String>): List<File> {
        val result = ArrayList<File>()
        val visited = HashSet<String>()
        val stack = ArrayDeque<File>()
        storageRoots().forEach { stack.addLast(it) }

        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val dirPath = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (!visited.add(dirPath)) continue
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    val name = child.name
                    if (name.startsWith(".")) continue
                    if (name.equals("Android", ignoreCase = true)) continue
                    val childPath = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
                    if (blacklist.any { childPath == it || childPath.startsWith("$it/") }) continue
                    stack.addLast(child)
                } else if (isVideoFile(child.name)) {
                    result += child
                }
            }
        }
        return result
    }

    private fun storageRoots(): List<File> {
        val roots = LinkedHashSet<String>()
        Environment.getExternalStorageDirectory()?.let { dir ->
            runCatching { roots += dir.canonicalPath }
        }
        context.getExternalFilesDirs(null).forEach { dir ->
            if (dir == null) return@forEach
            val root = dir.absolutePath.substringBefore("/Android/")
            if (root.isNotEmpty()) roots += root
        }
        File("/storage").listFiles()?.forEach { dir ->
            val name = dir.name
            if (name == "self" || name == "emulated") return@forEach
            if (dir.isDirectory && dir.canRead()) {
                runCatching { roots += dir.canonicalPath }
            }
        }
        return roots.map { File(it) }.filter { it.exists() && it.canRead() }
    }

    companion object {
        private const val TAG = "MediaScanner"
        private const val BATCH = 20

        val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "mov", "avi", "webm", "flv", "ts", "m2ts",
            "rmvb", "wmv", "3gp", "m4v", "mpg", "mpeg", "mts", "ogv", "rm",
        )

        fun isVideoFile(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            return ext in VIDEO_EXTENSIONS
        }
    }
}
