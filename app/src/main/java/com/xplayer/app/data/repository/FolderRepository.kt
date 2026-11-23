package com.xplayer.app.data.repository

import com.xplayer.app.data.local.FolderBlacklistDao
import com.xplayer.app.data.local.FolderBlacklistEntity
import kotlinx.coroutines.flow.Flow

class FolderRepository(private val dao: FolderBlacklistDao) {

    val blacklisted: Flow<List<FolderBlacklistEntity>> = dao.observeAll()

    suspend fun paths(): Set<String> = dao.allPaths().toSet()

    suspend fun hide(path: String) = dao.insert(
        FolderBlacklistEntity(folderPath = path, addedAt = System.currentTimeMillis()),
    )

    suspend fun unhide(path: String) = dao.delete(path)
}
