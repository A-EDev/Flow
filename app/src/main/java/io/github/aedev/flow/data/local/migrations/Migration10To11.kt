package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration10To11 : Migration(10, 11) {
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
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_watch_history_videoId ON watch_history(videoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_timestamp ON watch_history(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isMusic ON watch_history(isMusic)")
    }
}
