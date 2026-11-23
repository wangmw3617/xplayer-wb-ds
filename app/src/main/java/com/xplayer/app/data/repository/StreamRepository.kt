package com.xplayer.app.data.repository

import com.xplayer.app.data.local.StreamUrlDao
import com.xplayer.app.data.local.StreamUrlEntity
import kotlinx.coroutines.flow.Flow

class StreamRepository(private val dao: StreamUrlDao) {

    val items: Flow<List<StreamUrlEntity>> = dao.observeAll()

    suspend fun remember(url: String, title: String?, headersJson: String?) {
        val existing = dao.findByUrl(url)
        dao.upsert(
            StreamUrlEntity(
                id = existing?.id ?: 0L,
                url = url,
                customTitle = title?.takeIf { it.isNotBlank() } ?: existing?.customTitle,
                headersJson = headersJson ?: existing?.headersJson,
                lastPlayedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun touch(url: String) = dao.touch(url, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun find(url: String): StreamUrlEntity? = dao.findByUrl(url)
}
