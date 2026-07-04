package io.github.aedev.flow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import io.github.aedev.flow.data.local.entity.NotificationEntity
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.local.entity.PlaylistVideoCrossRef
import io.github.aedev.flow.data.local.entity.RecognitionHistoryEntity
import io.github.aedev.flow.data.local.entity.MusicHomeChipEntity
import io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import io.github.aedev.flow.data.local.entity.SyncLogEntity
import io.github.aedev.flow.data.local.entity.SyncPeerEntity
import io.github.aedev.flow.data.local.entity.VideoEntity
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity

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
        SyncPeerEntity::class
    ],
    version = 24,
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
    abstract fun homeFeedCacheDao(): HomeFeedCacheDao
    abstract fun subscriptionGroupDao(): SubscriptionGroupDao
    abstract fun recognitionHistoryDao(): RecognitionHistoryDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun syncPeerDao(): SyncPeerDao

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

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN isUserCreated INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN sponsorBlockSegmentsJson TEXT")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subscription_groups (
                        name TEXT NOT NULL PRIMARY KEY,
                        channelIds TEXT NOT NULL DEFAULT '',
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscription_feed_cache ADD COLUMN isLive INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN isShort INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isShort ON watch_history(isShort)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscription_feed_cache ADD COLUMN isUpcoming INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN isLocal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isLocal ON watch_history(isLocal)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recognition_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        trackId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT,
                        coverArtUrl TEXT,
                        coverArtHqUrl TEXT,
                        genre TEXT,
                        releaseDate TEXT,
                        label TEXT,
                        shazamUrl TEXT,
                        appleMusicUrl TEXT,
                        spotifyUrl TEXT,
                        isrc TEXT,
                        youtubeVideoId TEXT,
                        recognizedAt INTEGER NOT NULL,
                        liked INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_history_trackId ON recognition_history(trackId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_history_recognizedAt ON recognition_history(recognizedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_history_title ON recognition_history(title)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_history_artist ON recognition_history(artist)")
            }
        }

        // Device Sync (FLOW-SYNC/1): stable cross-device playlist identity.
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN syncId TEXT")
                db.execSQL("UPDATE playlists SET syncId = lower(hex(randomblob(16))) WHERE syncId IS NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_syncId ON playlists(syncId)")
            }
        }

        // Device Sync (FLOW-SYNC/1): idempotency ledger + known peers.
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_log (
                        peerDeviceId TEXT NOT NULL,
                        collection   TEXT NOT NULL,
                        payloadHash  TEXT NOT NULL,
                        appliedAt    INTEGER NOT NULL,
                        hwmHlc       TEXT NOT NULL,
                        PRIMARY KEY(peerDeviceId, collection, payloadHash)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_peers (
                        deviceId     TEXT NOT NULL PRIMARY KEY,
                        deviceName   TEXT NOT NULL,
                        platform     TEXT NOT NULL,
                        lastSyncedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS home_feed_cache (
                        cacheKey            TEXT    NOT NULL PRIMARY KEY,
                        bucket              TEXT    NOT NULL,
                        videoId             TEXT    NOT NULL,
                        title               TEXT    NOT NULL,
                        channelName         TEXT    NOT NULL,
                        channelId           TEXT    NOT NULL,
                        thumbnailUrl        TEXT    NOT NULL,
                        duration            INTEGER NOT NULL,
                        viewCount           INTEGER NOT NULL,
                        likeCount           INTEGER NOT NULL,
                        uploadDate          TEXT    NOT NULL,
                        timestamp           INTEGER NOT NULL,
                        description         TEXT    NOT NULL,
                        channelThumbnailUrl TEXT    NOT NULL,
                        tagsJson            TEXT    NOT NULL,
                        isMusic             INTEGER NOT NULL,
                        isLive              INTEGER NOT NULL,
                        isShort             INTEGER NOT NULL,
                        isUpcoming          INTEGER NOT NULL,
                        commentCountText    TEXT    NOT NULL,
                        source              TEXT    NOT NULL,
                        relatedSeedId       TEXT,
                        cachedAt            INTEGER NOT NULL,
                        expiresAt           INTEGER NOT NULL,
                        orderIndex          INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_bucket_expiresAt ON home_feed_cache(bucket, expiresAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_bucket_source ON home_feed_cache(bucket, source)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_relatedSeedId ON home_feed_cache(relatedSeedId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_videoId ON home_feed_cache(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_channelId ON home_feed_cache(channelId)")
            }
        }

        // Per-playlist "added at" timestamp so owned playlists can show when a video was added
        // instead of stale cached view counts / upload dates.
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlist_video_cross_ref ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                // Freshly-added rows historically stored -System.currentTimeMillis() in `position`
                // (before manual reordering overwrote it) — recover that as the add time.
                db.execSQL("UPDATE playlist_video_cross_ref SET addedAt = -position WHERE position < 0")
            }
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flow_database"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
