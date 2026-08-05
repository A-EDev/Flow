package io.github.aedev.flow.data.local

import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import io.github.aedev.flow.data.local.dao.CacheDao
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.dao.DownloadedSongDao
import io.github.aedev.flow.data.local.dao.HomeFeedCacheDao
import io.github.aedev.flow.data.local.dao.NotificationDao
import io.github.aedev.flow.data.local.dao.PlaylistDao
import io.github.aedev.flow.data.local.dao.RecognitionHistoryDao
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.local.dao.SyncLogDao
import io.github.aedev.flow.data.local.dao.SyncPeerDao
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadedSongEntity
import io.github.aedev.flow.data.local.entity.HomeFeedCacheEntity
import io.github.aedev.flow.data.local.entity.MusicHomeCacheEntity
import io.github.aedev.flow.data.local.entity.MusicHomeChipEntity
import io.github.aedev.flow.data.local.entity.NotificationEntity
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.local.entity.PlaylistVideoCrossRef
import io.github.aedev.flow.data.local.entity.RecognitionHistoryEntity
import io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import io.github.aedev.flow.data.local.entity.SyncLogEntity
import io.github.aedev.flow.data.local.entity.SyncPeerEntity
import io.github.aedev.flow.data.local.entity.VideoEntity
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity
import io.github.aedev.flow.data.local.migrations.MIGRATIONS

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
        WatchHistoryEntity::class,
        HomeFeedCacheEntity::class,
        SubscriptionGroupEntity::class,
        RecognitionHistoryEntity::class,
        SyncLogEntity::class,
        SyncPeerEntity::class,
    ],
    version = 24,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun notificationDao(): NotificationDao

    abstract fun cacheDao(): CacheDao

    abstract fun downloadedSongDao(): DownloadedSongDao

    abstract fun downloadDao(): DownloadDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun homeFeedCacheDao(): HomeFeedCacheDao

    abstract fun subscriptionGroupDao(): SubscriptionGroupDao

    abstract fun recognitionHistoryDao(): RecognitionHistoryDao

    abstract fun syncLogDao(): SyncLogDao

    abstract fun syncPeerDao(): SyncPeerDao

    companion object {
        @Volatile
        @Suppress("ktlint:standard:property-naming")
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val instance =
                    databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "flow_database",
                    ).addMigrations(*MIGRATIONS)
                        .fallbackToDestructiveMigration(false)
                        .build()
                INSTANCE = instance
                instance
            }
    }
}
