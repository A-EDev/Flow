package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Device Sync (FLOW-SYNC/1): stable cross-device playlist identity.
class Migration20To21 : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN syncId TEXT")
        db.execSQL("UPDATE playlists SET syncId = lower(hex(randomblob(16))) WHERE syncId IS NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_syncId ON playlists(syncId)")
    }
}
