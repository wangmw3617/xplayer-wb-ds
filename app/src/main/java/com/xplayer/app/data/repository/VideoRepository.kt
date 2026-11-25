package com.xplayer.app.data.repository

import android.content.Context
import com.xplayer.app.data.local.PlaybackHistoryDao
import com.xplayer.app.data.local.VideoDao
import com.xplayer.app.data.local.VideoEntity
import com.xplayer.app.data.local.VideoThumbRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(
    private val context: Context,
    private val dao: VideoDao,
    private val historyDao: PlaybackHistoryDao,
) {

    val videos: Flow<List<VideoEntity>> = dao.observeAll()

    val count: Flow<Int> = dao.observeCount()

    suspend fun all(): List<VideoEntity> = dao.all()

    suspend fun find(path: String): VideoEntity? = dao.findByPath(path)

    suspend fun upsertAll(items: List<VideoEntity>) = dao.upsertAll(items)

    suspend fun clear() = dao.clear()

    suspend fun videosInFolder(folder: String): List<VideoEntity> = withContext(Dispatchers.IO) {
        dao.videosInFolder(folder)
    }

    suspend fun allThumbnailPaths(): Set<String> = withContext(Dispatchers.IO) {
        dao.allThumbnailPaths().toHashSet()
    }

    suspend fun deleteMissing(paths: Set<String>) = withContext(Dispatchers.IO) {
        val missing = dao.allThumbRefs().filter { it.path !in paths }
        if (missing.isEmpty()) return@withContext
        missing.map { it.path }.chunked(SQL_BATCH_SIZE).forEach { batch ->
            dao.deleteByPaths(batch)
        }
        cleanupRemovedRefs(missing)
    }

    /** 屏蔽目录时立即把该目录下的视频从库中移除，避免残留条目 */
    suspend fun deleteInFolder(folder: String) = withContext(Dispatchers.IO) {
        val refs = dao.folderThumbRefs(folder)
        dao.deleteByFolder(folder)
        cleanupRemovedRefs(refs)
    }

    private suspend fun cleanupRemovedRefs(refs: List<VideoThumbRef>) {
        if (refs.isEmpty()) return
        refs.map { it.path }.chunked(SQL_BATCH_SIZE).forEach { batch ->
            historyDao.deleteByMediaIds(batch)
        }
        refs.forEach { ref ->
            ref.thumbnailPath?.let { runCatching { File(it).delete() } }
        }
    }

    /**
     * 重命名：必须是同目录下改名，避免跨分区移动失败。
     * 成功时同步更新播放记录中的 mediaId。
     */
    suspend fun rename(video: VideoEntity, newName: String): Result<String> = withContext(Dispatchers.IO) {
        val source = File(video.path)
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("empty name"))
        val extension = source.extension
        val targetName = if (extension.isNotEmpty() && !trimmed.endsWith(".$extension", ignoreCase = true)) {
            "$trimmed.$extension"
        } else {
            trimmed
        }
        val target = File(source.parentFile, targetName)
        if (target.exists()) return@withContext Result.failure(IllegalStateException("exists"))
        val moved = runCatching { source.renameTo(target) }.getOrDefault(false)
        if (!moved) return@withContext Result.failure(IllegalStateException("rename failed"))
        dao.rename(video.path, target.absolutePath, target.nameWithoutExtension)
        historyDao.findByMediaId(video.path)?.let { history ->
            historyDao.upsert(history.copy(mediaId = target.absolutePath, title = target.nameWithoutExtension))
        }
        Result.success(target.nameWithoutExtension)
    }

    suspend fun delete(video: VideoEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(video.path)
        if (file.exists() && !file.delete()) return@withContext Result.failure(IllegalStateException("delete failed"))
        dao.deleteByPath(video.path)
        historyDao.delete(video.path)
        video.thumbnailPath?.let { runCatching { File(it).delete() } }
        Result.success(Unit)
    }

    fun thumbnailFile(path: String): File {
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val name = Integer.toHexString(path.hashCode()) + ".jpg"
        return File(dir, name)
    }

    private companion object {
        const val SQL_BATCH_SIZE = 500
    }
}
