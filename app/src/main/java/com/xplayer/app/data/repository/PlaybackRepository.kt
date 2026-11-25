package com.xplayer.app.data.repository

import com.xplayer.app.data.local.PlaybackHistoryDao
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PlaybackRepository(private val dao: PlaybackHistoryDao) {

    val history: Flow<List<PlaybackHistoryEntity>> = dao.observeAll()

    private val saveMutex = Mutex()

    suspend fun find(mediaId: String, type: SourceType): PlaybackHistoryEntity? =
        withContext(Dispatchers.IO) { dao.find(mediaId, type) }

    suspend fun find(mediaId: String): PlaybackHistoryEntity? =
        withContext(Dispatchers.IO) { dao.find(mediaId, SourceType.LOCAL) }

    /**
     * 写入播放进度：用互斥锁串行化“读旧值+合并+写入”，防止定期存盘与退出瞬时存盘乱序覆盖。
     * tracksLoaded 为 false 时保留既有音轨/字幕/延迟选择，避免开播未就绪时把旧偏好清空。
     */
    suspend fun save(
        mediaId: String,
        sourceType: SourceType,
        title: String,
        positionMs: Long,
        durationMs: Long,
        preferredEngine: String? = null,
        subtitleDelayMs: Long = 0L,
        audioTrackId: String? = null,
        subtitleTrackId: String? = null,
        tracksLoaded: Boolean = false,
        thumbnailPath: String? = null,
        folderPath: String? = null,
        customHeaders: String? = null,
    ) = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val existing = dao.find(mediaId, sourceType)
            val resolvedDuration = if (durationMs > 0L) durationMs else existing?.durationMs ?: 0L
            val completed = resolvedDuration > 0L && positionMs >= (resolvedDuration * COMPLETION_THRESHOLD).toLong()
            dao.upsert(
                PlaybackHistoryEntity(
                    id = existing?.id ?: 0L,
                    mediaId = mediaId,
                    sourceType = sourceType,
                    title = title,
                    lastPositionMs = positionMs.coerceAtLeast(0L),
                    durationMs = resolvedDuration,
                    lastPlayedAt = System.currentTimeMillis(),
                    preferredEngine = preferredEngine ?: existing?.preferredEngine,
                    subtitleDelayMs = if (tracksLoaded) subtitleDelayMs else existing?.subtitleDelayMs ?: subtitleDelayMs,
                    audioTrackId = if (tracksLoaded) audioTrackId else existing?.audioTrackId,
                    subtitleTrackId = if (tracksLoaded) subtitleTrackId else existing?.subtitleTrackId,
                    thumbnailPath = thumbnailPath ?: existing?.thumbnailPath,
                    folderPath = folderPath ?: existing?.folderPath,
                    completed = completed,
                    customHeaders = customHeaders ?: existing?.customHeaders,
                ),
            )
        }
    }

    suspend fun remove(mediaId: String, type: SourceType = SourceType.LOCAL) = withContext(Dispatchers.IO) {
        dao.delete(mediaId, type)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
    }

    suspend fun pruneMissing() = withContext(Dispatchers.IO) {
        dao.pruneMissing()
    }

    companion object {
        const val COMPLETION_THRESHOLD = 0.95
    }
}
