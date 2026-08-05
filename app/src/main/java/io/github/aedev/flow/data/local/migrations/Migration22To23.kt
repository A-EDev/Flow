package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration22To23 : Migration(22, 23) {
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
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_bucket_expiresAt ON home_feed_cache(bucket, expiresAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_bucket_source ON home_feed_cache(bucket, source)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_relatedSeedId ON home_feed_cache(relatedSeedId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_videoId ON home_feed_cache(videoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_home_feed_cache_channelId ON home_feed_cache(channelId)")
    }
}
