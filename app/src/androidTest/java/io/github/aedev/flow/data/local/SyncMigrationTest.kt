package io.github.aedev.flow.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncMigrationTest {

    /** A bare in-memory DB at schema v20 with just the pre-migration `playlists` table. */
    private fun openV20(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration
            .builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE playlists (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL,
                            thumbnailUrl TEXT NOT NULL,
                            isPrivate INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            videoCount INTEGER NOT NULL DEFAULT 0,
                            isMusic INTEGER NOT NULL DEFAULT 0,
                            isUserCreated INTEGER NOT NULL DEFAULT 1
                        )
                        """.trimIndent(),
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migrates_20_to_22() {
        val db = openV20()
        db.execSQL(
            "INSERT INTO playlists (id,name,description,thumbnailUrl,isPrivate,createdAt) " +
                "VALUES ('pl1','My Playlist','','',0,123456789)",
        )

        AppDatabase.MIGRATION_20_21.migrate(db)
        AppDatabase.MIGRATION_21_22.migrate(db)

        // syncId added + backfilled (lower(hex(randomblob(16))) → 32 hex chars).
        db.query("SELECT syncId FROM playlists WHERE id='pl1'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            val syncId = c.getString(0)
            assertNotNull("syncId must be backfilled", syncId)
            assertEquals("16 random bytes => 32 hex chars", 32, syncId.length)
        }

        // Index exists.
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_playlists_syncId'").use { c ->
            assertEquals(1, c.count)
        }

        // Sync tables exist.
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('sync_log','sync_peers')").use { c ->
            assertEquals(2, c.count)
        }

        // Idempotency-ledger composite PK rejects a duplicate (peer, collection, hash).
        db.execSQL("INSERT INTO sync_log VALUES ('peerA','playlists','hashX',1,'1:0:n')")
        var rejected = false
        try {
            db.execSQL("INSERT INTO sync_log VALUES ('peerA','playlists','hashX',2,'2:0:n')")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            rejected = true
        }
        assertEquals("duplicate (peer,collection,hash) must violate the PK", true, rejected)
        db.close()
    }
}
