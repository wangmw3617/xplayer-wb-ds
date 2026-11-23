package com.xplayer.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        VideoEntity::class,
        PlaybackHistoryEntity::class,
        FolderBlacklistEntity::class,
        StreamUrlEntity::class,
        MpvShaderEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class XPlayerDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao

    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    abstract fun folderBlacklistDao(): FolderBlacklistDao

    abstract fun streamUrlDao(): StreamUrlDao

    abstract fun mpvShaderDao(): MpvShaderDao

    companion object {
        @Volatile
        private var instance: XPlayerDatabase? = null

        fun get(context: Context): XPlayerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                XPlayerDatabase::class.java,
                "xplayer.db",
            ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
        }
    }
}
