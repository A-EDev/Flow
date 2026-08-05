package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration14To15 : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS subscription_groups (
                name TEXT NOT NULL PRIMARY KEY,
                channelIds TEXT NOT NULL DEFAULT '',
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }
}
