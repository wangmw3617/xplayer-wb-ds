package com.xplayer.app.data.repository

import com.xplayer.app.data.local.FolderBlacklistDao
import com.xplayer.app.data.local.FolderBlacklistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class FolderRepository(private val dao: FolderBlacklistDao) {

    val blacklisted: Flow<List<FolderBlacklistEntity>> = dao.observeAll()

    suspend fun paths(): Set<String> = withContext(Dispatchers.IO) {
        dao.allPaths().mapTo(HashSet()) { normalizePath(it) }
    }

    suspend fun hide(path: String) = withContext(Dispatchers.IO) {
        dao.insert(
            FolderBlacklistEntity(
                folderPath = normalizePath(path),
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun unhide(path: String) = withContext(Dispatchers.IO) {
        val normalized = normalizePath(path)
        dao.delete(path)
        if (normalized != path) dao.delete(normalized)
    }

    companion object {
        fun normalizePath(raw: String): String {
            val trimmed = raw.trimEnd('/', '\\').ifEmpty { "/" }
            return runCatching { File(trimmed).canonicalPath }.getOrDefault(trimmed)
        }
    }
}
