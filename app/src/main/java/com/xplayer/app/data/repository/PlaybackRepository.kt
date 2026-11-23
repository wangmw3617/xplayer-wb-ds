package com.xplayer.app.data.repository

import com.xplayer.app.data.local.PlaybackHistoryDao
import com.xplayer.app.data.local.PlaybackHistoryEntity
import com.xplayer.app.data.local.SourceType
import kotlinx.coroutines.flow.Flow

class PlaybackRepository(private val dao: PlaybackHistoryDao) {

    val history: Flow<List<PlaybackHistoryEntity>> = dao.observeAll()

    suspend fun find(mediaId: String, type: SourceType): PlaybackHistoryEntity? = dao.find(mediaId, type)

    suspend fun find(mediaId: String): PlaybackHistoryEntity? = dao.findByMediaId(mediaId)

    /**
     * 写入播放进度。命中同源记录时复用主键，避免 REPLACE 造成 id 抖动。
     */
    suspend fun save(
        mediaId: String,
        sourceType: SourceType,
        title: String,
        positionMs: Long,
        durationMs: Long,
        preferredEngine: String? = null,
        subtitleDelayMs: Long = 0,
        audioTrackId: String? = null,
        subtitleTrackId: String? = null,
        thumbnailPath: String? = null,
        folderPath: String? = null,
        customHeaders: String? = null,
    ) {
        val existing = dao.find(mediaId, sourceType)
        val completed = durationMs > 0 && positionMs >= (durationMs * 0.95f).toLong()
        dao.upsert(
            PlaybackHistoryEntity(
                id = existing?.id ?: 0L,
                mediaId = mediaId,
                sourceType = sourceType,
                title = title,
                lastPositionMs = positionMs,
                durationMs = if (durationMs > 0) durationMs else existing?.durationMs ?: 0L,
                lastPlayedAt = System.currentTimeMillis(),
                preferredEngine = preferredEngine ?: existing?.preferredEngine,
                subtitleDelayMs = subtitleDelayMs,
                audioTrackId = audioTrackId,
                subtitleTrackId = subtitleTrackId,
                thumbnailPath = thumbnailPath ?: existing?.thumbnailPath,
                folderPath = folderPath ?: existing?.folderPath,
                completed = completed,
                customHeaders = customHeaders ?: existing?.customHeaders,
            ),
        )
    }

    suspend fun remove(mediaId: String) = dao.delete(mediaId)

    suspend fun clear() = dao.clear()

    suspend fun pruneMissing() = dao.pruneMissing()
}
