package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Device Sync (FLOW-SYNC/1): idempotency ledger + known peers.
class Migration21To22 : Migration(21, 22) {
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
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_peers (
                deviceId     TEXT NOT NULL PRIMARY KEY,
                deviceName   TEXT NOT NULL,
                platform     TEXT NOT NULL,
                lastSyncedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
