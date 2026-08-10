package io.github.aedev.flow.data.local.migrations

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec

@DeleteTable.Entries(
    DeleteTable(
        tableName = "downloaded_songs",
    ),
)
class Migration24To25 : AutoMigrationSpec
