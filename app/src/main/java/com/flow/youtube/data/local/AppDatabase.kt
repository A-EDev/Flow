package com.flow.youtube.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flow.youtube.data.local.dao.CacheDao
import com.flow.youtube.data.local.dao.DownloadDao
import com.flow.youtube.data.local.dao.DownloadedSongDao
import com.flow.youtube.data.local.dao.NotificationDao
import com.flow.youtube.data.local.dao.PlaylistDao
import com.flow.youtube.data.local.dao.VideoDao
import com.flow.youtube.data.local.dao.WatchHistoryDao
import com.flow.youtube.data.local.entity.DownloadEntity
import com.flow.youtube.data.local.entity.DownloadItemEntity
import com.flow.youtube.data.local.entity.DownloadedSongEntity
import com.flow.youtube.data.local.entity.MusicHomeCacheEntity
import com.flow.youtube.data.local.entity.NotificationEntity
import com.flow.youtube.data.local.entity.PlaylistEntity
import com.flow.youtube.data.local.entity.PlaylistVideoCrossRef
import com.flow.youtube.data.local.entity.MusicHomeChipEntity
import com.flow.youtube.data.local.entity.SubscriptionFeedEntity
import com.flow.youtube.data.local.entity.VideoEntity
import com.flow.youtube.data.local.entity.WatchHistoryEntity

@Database(
    entities = [
        VideoEntity::class,
        PlaylistEntity::class,
        PlaylistVideoCrossRef::class,
        NotificationEntity::class,
        SubscriptionFeedEntity::class,
        MusicHomeCacheEntity::class,
        MusicHomeChipEntity::class,
        DownloadedSongEntity::class,
        DownloadEntity::class,
        DownloadItemEntity::class,
        WatchHistoryEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun notificationDao(): NotificationDao
    abstract fun cacheDao(): CacheDao
    abstract fun downloadedSongDao(): DownloadedSongDao
    abstract fun downloadDao(): DownloadDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watch_history (
                        videoId      TEXT    NOT NULL PRIMARY KEY,
                        position     INTEGER NOT NULL,
                        duration     INTEGER NOT NULL,
                        timestamp    INTEGER NOT NULL,
                        title        TEXT    NOT NULL,
                        thumbnailUrl TEXT    NOT NULL,
                        channelName  TEXT    NOT NULL,
                        channelId    TEXT    NOT NULL,
                        isMusic      INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_watch_history_videoId ON watch_history(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_timestamp ON watch_history(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isMusic ON watch_history(isMusic)")
            }
        }

        // Devices that installed the buggy 10→11 migration (missing the unique
        // videoId index) need this patch migration to add it.
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_watch_history_videoId ON watch_history(videoId)")
            }
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flow_database"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
