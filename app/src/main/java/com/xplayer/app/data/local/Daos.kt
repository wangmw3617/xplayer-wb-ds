package com.xplayer.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class VideoThumbRef(
    val path: String,
    val thumbnailPath: String?,
)

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos ORDER BY dateModified DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): VideoEntity?

    @Query("SELECT path FROM videos")
    suspend fun allPaths(): List<String>

    @Query("SELECT path, thumbnailPath FROM videos")
    suspend fun allThumbRefs(): List<VideoThumbRef>

    @Query("SELECT path, thumbnailPath FROM videos WHERE folderPath = :folder")
    suspend fun folderThumbRefs(folder: String): List<VideoThumbRef>

    @Query("SELECT thumbnailPath FROM videos WHERE thumbnailPath IS NOT NULL")
    suspend fun allThumbnailPaths(): List<String>

    @Query("SELECT * FROM videos")
    suspend fun all(): List<VideoEntity>

    @Query("SELECT COUNT(*) FROM videos")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(videos: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM videos WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query("DELETE FROM videos WHERE folderPath = :folder")
    suspend fun deleteByFolder(folder: String)

    @Query("DELETE FROM videos")
    suspend fun clear()

    @Query("UPDATE videos SET title = :title, path = :newPath WHERE path = :oldPath")
    suspend fun rename(oldPath: String, newPath: String, title: String)

    @Query("SELECT * FROM videos WHERE folderPath = :folder ORDER BY title ASC")
    suspend fun videosInFolder(folder: String): List<VideoEntity>
}

@Dao
interface PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC")
    fun observeAll(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId AND sourceType = :type LIMIT 1")
    suspend fun find(mediaId: String, type: SourceType): PlaybackHistoryEntity?

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId LIMIT 1")
    suspend fun findByMediaId(mediaId: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlaybackHistoryEntity): Long

    @Query("DELETE FROM playback_history WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)

    @Query("DELETE FROM playback_history WHERE mediaId IN (:mediaIds)")
    suspend fun deleteByMediaIds(mediaIds: List<String>)

    @Query("DELETE FROM playback_history")
    suspend fun clear()

    @Query("DELETE FROM playback_history WHERE sourceType = 'LOCAL' AND mediaId NOT IN (SELECT path FROM videos)")
    suspend fun pruneMissing()
}

@Dao
interface FolderBlacklistDao {

    @Query("SELECT * FROM folder_blacklist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FolderBlacklistEntity>>

    @Query("SELECT folderPath FROM folder_blacklist")
    suspend fun allPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FolderBlacklistEntity)

    @Query("DELETE FROM folder_blacklist WHERE folderPath = :path")
    suspend fun delete(path: String)
}

@Dao
interface StreamUrlDao {

    @Query("SELECT * FROM stream_urls ORDER BY lastPlayedAt DESC")
    fun observeAll(): Flow<List<StreamUrlEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StreamUrlEntity): Long

    @Query("UPDATE stream_urls SET lastPlayedAt = :time WHERE url = :url")
    suspend fun touch(url: String, time: Long)

    @Query("DELETE FROM stream_urls WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM stream_urls WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): StreamUrlEntity?
}

@Dao
interface MpvShaderDao {

    @Query("SELECT * FROM mpv_shaders ORDER BY orderIndex ASC")
    fun observeAll(): Flow<List<MpvShaderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MpvShaderEntity): Long

    @Query("UPDATE mpv_shaders SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE mpv_shaders SET orderIndex = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Query("DELETE FROM mpv_shaders WHERE id = :id")
    suspend fun delete(id: Long)
}
