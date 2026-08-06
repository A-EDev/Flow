package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Devices that installed the buggy 10→11 migration (missing the unique
// videoId index) need this patch migration to add it.
class Migration11To12 : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_watch_history_videoId ON watch_history(videoId)")
    }
}
