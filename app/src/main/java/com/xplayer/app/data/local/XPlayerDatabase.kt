package com.xplayer.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VideoEntity::class,
        PlaybackHistoryEntity::class,
        FolderBlacklistEntity::class,
        StreamUrlEntity::class,
        MpvShaderEntity::class,
    ],
    version = 2,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建 url 唯一索引前先清理历史版本中可能存在的重复直链条目
                db.execSQL(
                    "DELETE FROM stream_urls WHERE id NOT IN " +
                        "(SELECT MAX(id) FROM stream_urls GROUP BY url)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_videos_dateModified ON videos(dateModified)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_history_lastPlayedAt ON playback_history(lastPlayedAt)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stream_urls_url ON stream_urls(url)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stream_urls_lastPlayedAt ON stream_urls(lastPlayedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mpv_shaders_orderIndex ON mpv_shaders(orderIndex)")
            }
        }

        fun get(context: Context): XPlayerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                XPlayerDatabase::class.java,
                "xplayer.db",
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
