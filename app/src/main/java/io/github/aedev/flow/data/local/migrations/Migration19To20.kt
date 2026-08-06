package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration19To20 : Migration(19, 20) {
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
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_history_trackId ON recognition_history(trackId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_history_recognizedAt ON recognition_history(recognizedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_history_title ON recognition_history(title)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_history_artist ON recognition_history(artist)")
    }
}
