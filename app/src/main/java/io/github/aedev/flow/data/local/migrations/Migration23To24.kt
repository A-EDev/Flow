package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Per-playlist "added at" timestamp so owned playlists can show when a video was added
// instead of stale cached view counts / upload dates.
class Migration23To24 : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlist_video_cross_ref ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
        // Freshly-added rows historically stored -System.currentTimeMillis() in `position`
        // (before manual reordering overwrote it) — recover that as the add time.
        db.execSQL("UPDATE playlist_video_cross_ref SET addedAt = -position WHERE position < 0")
    }
}
