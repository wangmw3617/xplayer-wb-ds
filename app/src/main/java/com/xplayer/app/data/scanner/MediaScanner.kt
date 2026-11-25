package com.xplayer.app.data.scanner

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.repository.FolderRepository
import com.xplayer.app.data.repository.VideoRepository
import com.xplayer.app.ui.permission.StorageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class ScanState(
    val scanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val finishedAt: Long = 0L,
    val error: String? = null,
)

/**
 * 本地媒体扫描器：
 * - 优先使用全盘权限广度遍历（跳过隐藏目录、.nomedia、根/Android 与屏蔽目录）；
 * - Android 13+ 全盘未授但已授予 READ_MEDIA_VIDEO 时，退化为 MediaStore 查询，兼容细分权限；
 * - 增量提取时保留既有主键 id 与 dateAdded，避免重扫刷新引起列表跳动；
 * - 无存储权限时绝不清空既有媒体库。
 */
class MediaScanner(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val videoRepository: VideoRepository,
    private val folderRepository: FolderRepository,
) {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val activePlaybackCount = AtomicInteger(0)
    private val mutex = Mutex()
    private val extractor = MetadataExtractor(File(context.cacheDir, "thumbs"))

    /** 引用计数追踪活跃播放器数量，避免重叠替换期间提前解除扫描限速 */
    fun onPlaybackStarted() {
        activePlaybackCount.incrementAndGet()
    }

    fun onPlaybackStopped() {
        activePlaybackCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
    }

    fun triggerScan() {
        if (_state.value.scanning) return
        appScope.launch { scan() }
    }

    suspend fun scan(): ScanState {
        if (_state.value.scanning) return _state.value
        return mutex.withLock {
            if (_state.value.scanning) return@withLock _state.value
            withContext(Dispatchers.IO) { runScan() }
        }
    }

    private suspend fun runScan(): ScanState {
        if (!StorageAccess.isGranted(context)) {
            return _state.value.copy(scanning = false, error = ERROR_NO_PERMISSION).also {
                _state.value = it
            }
        }

        _state.value = ScanState(scanning = true)
        return try {
            val blacklist = folderRepository.paths()
            val existing = videoRepository.all().associateBy { it.path }
            val candidates = if (StorageAccess.hasFullStorageAccess(context)) {
                collectVideoFiles(blacklist)
            } else {
                collectViaMediaStore(blacklist)
            }
            _state.value = _state.value.copy(total = candidates.size)

            val pending = ArrayList<VideoEntity>(32)
            val seen = HashSet<String>(candidates.size)
            val assNamesByFolder = HashMap<String, List<String>>()

            for ((index, file) in candidates.withIndex()) {
                if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
                val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                seen += path

                val old = existing[path]
                val folderPath = runCatching { file.parentFile?.canonicalPath }.getOrNull() ?: file.parent.orEmpty()
                val folderAssNames = assNamesByFolder.getOrPut(folderPath) {
                    if (folderPath.isEmpty()) emptyList() else SubtitleFinder.listAssNamesInFolder(folderPath)
                }
                val hasExternalAss = SubtitleFinder.hasMatchingAss(file, folderAssNames)
                val thumbMissing = old?.thumbnailPath != null && !File(old.thumbnailPath).isFile
                val fileUnchanged = old != null &&
                    old.sizeBytes == file.length() &&
                    old.dateModified == file.lastModified()

                if (!fileUnchanged || thumbMissing) {
                    while (activePlaybackCount.get() > 0 && kotlinx.coroutines.currentCoroutineContext().isActive) {
                        delay(300L)
                    }
                    runCatching {
                        extractor.extract(
                            file = file,
                            externalAssHint = hasExternalAss,
                            existingId = old?.id ?: 0L,
                            existingDateAdded = old?.dateAdded ?: 0L,
                        )
                    }.onFailure { Log.w(TAG, "extract failed: ${file.name}", it) }
                        .getOrNull()
                        ?.let { pending += it }
                } else if (hasExternalAss && !old.hasAssSubtitle) {
                    pending += old.copy(hasAssSubtitle = true)
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
            pruneCaches(videoRepository.allThumbnailPaths())

            _state.value.copy(
                scanning = false,
                finishedAt = System.currentTimeMillis(),
                error = null,
            ).also { _state.value = it }
        } catch (error: Throwable) {
            Log.e(TAG, "scan failed", error)
            _state.value.copy(scanning = false, error = error.message ?: "SCAN_FAILED")
                .also { _state.value = it }
        } finally {
            if (_state.value.scanning) {
                _state.value = _state.value.copy(scanning = false)
            }
        }
    }

    private fun collectVideoFiles(blacklist: Set<String>): List<File> {
        val result = ArrayList<File>()
        val visited = HashSet<String>()
        val stack = ArrayDeque<Pair<File, Boolean>>()
        val roots = storageRoots()
        roots.forEach { root ->
            val rootPath = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
            if (!isBlacklisted(rootPath, blacklist)) {
                stack.addLast(root to true)
            }
        }

        while (stack.isNotEmpty()) {
            val (dir, isRoot) = stack.removeLast()
            val dirPath = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (!visited.add(dirPath)) continue
            if (isBlacklisted(dirPath, blacklist)) continue
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            if (children.any { it.name == ".nomedia" && it.isFile }) continue

            for (child in children) {
                if (child.isDirectory) {
                    if (child.name.startsWith(".")) continue
                    if (isRoot && child.name.equals("Android", ignoreCase = true)) continue
                    val childPath = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
                    // 检查真实路径以防符号链接跳进隐藏目录或屏蔽目录
                    if (File(childPath).name.startsWith(".")) continue
                    if (isBlacklisted(childPath, blacklist)) continue
                    stack.addLast(child to false)
                } else if (isVideoFile(child.name) && child.isFile && child.canRead() && child.length() > 0L) {
                    result += child
                }
            }
        }
        return result
    }

    private fun collectViaMediaStore(blacklist: Set<String>): List<File> {
        val result = ArrayList<File>()
        val seen = HashSet<String>()
        runCatching {
            @Suppress("DEPRECATION")
            val projection = arrayOf(MediaStore.Video.Media.DATA)
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                @Suppress("DEPRECATION")
                val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (idx < 0) return@use
                while (cursor.moveToNext()) {
                    val rawPath = cursor.getString(idx) ?: continue
                    val file = File(rawPath)
                    if (!file.isFile || !file.canRead() || file.length() <= 0L) continue
                    val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                    if (isBlacklisted(canonical, blacklist)) continue
                    if (seen.add(canonical)) result += file
                }
            }
        }.onFailure { Log.w(TAG, "mediastore scan failed: ${it.message}") }
        return result
    }

    private fun isBlacklisted(path: String, blacklist: Set<String>): Boolean =
        blacklist.any { path == it || path.startsWith("$it/") }

    private fun storageRoots(): List<File> {
        val roots = LinkedHashSet<String>()
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory()?.let { dir ->
            runCatching { roots += dir.canonicalPath }
        }
        context.getExternalFilesDirs(null).forEach { dir ->
            if (dir == null) return@forEach
            val root = dir.absolutePath.substringBefore("/Android/", missingDelimiterValue = "")
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

    /** 清理孤立缩略图与超期导入的临时外挂字幕缓存，防止 cacheDir 持续膨胀 */
    private fun pruneCaches(activeThumbnailPaths: Set<String>) {
        val activeThumbNames = activeThumbnailPaths.mapTo(HashSet(activeThumbnailPaths.size)) { File(it).name }
        File(context.cacheDir, "thumbs").listFiles()?.forEach { file ->
            if (file.isFile && file.name !in activeThumbNames) {
                runCatching { file.delete() }
            }
        }
        if (activePlaybackCount.get() == 0) {
            val cutoff = System.currentTimeMillis() - STALE_SUBTITLE_AGE_MS
            File(context.cacheDir, "subtitles").listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MediaScanner"
        private const val BATCH = 20
        private const val STALE_SUBTITLE_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val ERROR_NO_PERMISSION = "NO_STORAGE_PERMISSION"

        val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "mov", "avi", "webm", "flv", "ts", "m2ts",
            "rmvb", "wmv", "3gp", "m4v", "mpg", "mpeg", "mts", "ogv", "rm", "vob",
        )

        fun isVideoFile(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            return ext in VIDEO_EXTENSIONS
        }
    }
}
