package com.xplayer.app.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import androidx.room.withTransaction
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.SourceType
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.local.VideoThumbRef
import com.xplayer.app.data.local.XPlayerDatabase
import com.xplayer.app.data.scanner.SubtitleFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(
    private val context: Context,
    private val database: XPlayerDatabase,
) {

    private val dao = database.videoDao()
    private val historyDao = database.playbackHistoryDao()

    val videos: Flow<List<VideoEntity>> = dao.observeAll()

    val count: Flow<Int> = dao.observeCount()

    suspend fun all(): List<VideoEntity> = withContext(Dispatchers.IO) { dao.all() }

    suspend fun find(path: String): VideoEntity? = withContext(Dispatchers.IO) { dao.findByPath(path) }

    suspend fun upsertAll(items: List<VideoEntity>) = withContext(Dispatchers.IO) { dao.upsertAll(items) }

    suspend fun videosInFolder(folder: String): List<VideoEntity> = withContext(Dispatchers.IO) {
        dao.videosInFolder(folder)
    }

    suspend fun allThumbnailPaths(): Set<String> = withContext(Dispatchers.IO) {
        dao.allThumbnailPaths().toHashSet()
    }

    suspend fun deleteMissing(paths: Set<String>) = withContext(Dispatchers.IO) {
        val missing = dao.allThumbRefs().filter { it.path !in paths }
        if (missing.isEmpty()) return@withContext
        database.withTransaction {
            missing.map { it.path }.chunked(SQL_BATCH_SIZE).forEach { batch ->
                dao.deleteByPaths(batch)
                historyDao.deleteByMediaIds(SourceType.LOCAL, batch)
            }
        }
        deleteThumbFiles(missing)
    }

    /** 屏蔽目录时立即把该目录及其子目录下的视频从库中移除，避免残留条目 */
    suspend fun deleteInFolder(folder: String) = withContext(Dispatchers.IO) {
        val normalized = FolderRepository.normalizePath(folder)
        val prefix = escapeLikePrefix(normalized)
        val refs = dao.folderTreeThumbRefs(normalized, prefix)
        database.withTransaction {
            dao.deleteByFolderTree(normalized, prefix)
            if (refs.isNotEmpty()) {
                refs.map { it.path }.chunked(SQL_BATCH_SIZE).forEach { batch ->
                    historyDao.deleteByMediaIds(SourceType.LOCAL, batch)
                }
            }
        }
        deleteThumbFiles(refs)
    }

    /**
     * 同目录安全重命名：禁止路径穿越/非法字符，兼容大小写重命名，
     * 并同步更新数据库、本地播放历史、同目录外挂字幕与 MediaStore 索引。
     */
    suspend fun rename(video: VideoEntity, newName: String): Result<String> = withContext(Dispatchers.IO) {
        val source = File(video.path)
        val parent = source.parentFile ?: return@withContext Result.failure(IllegalStateException("no parent"))
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == ".." || INVALID_NAME_REGEX.containsMatchIn(trimmed)) {
            return@withContext Result.failure(IllegalArgumentException("invalid name"))
        }

        val extension = source.extension
        val targetName = if (extension.isNotEmpty() && !trimmed.endsWith(".$extension", ignoreCase = true)) {
            "$trimmed.$extension"
        } else {
            trimmed
        }
        val target = File(parent, targetName)
        val sourceCanonical = runCatching { source.canonicalPath }.getOrDefault(source.absolutePath)
        val targetCanonical = runCatching { target.canonicalPath }.getOrDefault(target.absolutePath)
        if (targetCanonical == sourceCanonical && target.name == source.name) {
            return@withContext Result.success(target.nameWithoutExtension)
        }

        val caseOnlyChange = target.name.equals(source.name, ignoreCase = true) && target.name != source.name
        if (target.exists() && !caseOnlyChange) {
            return@withContext Result.failure(IllegalStateException("exists"))
        }

        val moved = if (caseOnlyChange) {
            // Android FUSE 等大小写不敏感存储上直接改大小写可能被忽略，走临时名两步中转保证成功
            val tmp = File(parent, ".rename_${System.nanoTime()}_${source.name}")
            if (!runCatching { source.renameTo(tmp) }.getOrDefault(false)) {
                false
            } else {
                val ok = runCatching { tmp.renameTo(target) }.getOrDefault(false)
                if (!ok) runCatching { tmp.renameTo(source) }
                ok
            }
        } else {
            runCatching { source.renameTo(target) }.getOrDefault(false)
        }
        if (!moved) return@withContext Result.failure(IllegalStateException("rename failed"))

        val finalPath = runCatching { target.canonicalPath }.getOrDefault(target.absolutePath)
        val finalFolder = runCatching { parent.canonicalPath }.getOrDefault(parent.absolutePath)
        val finalTitle = target.nameWithoutExtension.ifBlank { target.name }

        database.withTransaction {
            dao.rename(video.path, finalPath, finalTitle, finalFolder)
            val history: PlaybackHistoryEntity? = historyDao.find(video.path, SourceType.LOCAL)
            if (history != null) {
                historyDao.upsert(
                    history.copy(
                        mediaId = finalPath,
                        title = finalTitle,
                        folderPath = finalFolder,
                    ),
                )
            }
        }
        SubtitleFinder.renameSidecars(video.path, finalTitle)
        MediaScannerConnection.scanFile(context, arrayOf(video.path, finalPath), null, null)
        Result.success(finalTitle)
    }

    suspend fun delete(video: VideoEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(video.path)
        if (file.exists() && !file.delete()) {
            return@withContext Result.failure(IllegalStateException("delete failed"))
        }
        database.withTransaction {
            dao.deleteByPath(video.path)
            historyDao.delete(video.path, SourceType.LOCAL)
        }
        video.thumbnailPath?.let { runCatching { File(it).delete() } }
        MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)
        Result.success(Unit)
    }

    private fun deleteThumbFiles(refs: List<VideoThumbRef>) {
        refs.forEach { ref ->
            ref.thumbnailPath?.let { runCatching { File(it).delete() } }
        }
    }

    private fun escapeLikePrefix(folder: String): String {
        val escaped = folder
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "$escaped/%"
    }

    private companion object {
        const val SQL_BATCH_SIZE = 500
        val INVALID_NAME_REGEX = Regex("""[\\/:*?"<>|]""")
    }
}
