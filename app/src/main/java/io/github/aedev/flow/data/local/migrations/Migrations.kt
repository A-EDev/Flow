package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration

val MIGRATIONS: Array<Migration> =
    arrayOf(
        Migration10To11(),
        Migration11To12(),
        Migration12To13(),
        Migration13To14(),
        Migration14To15(),
        Migration15To16(),
        Migration16To17(),
        Migration17To18(),
        Migration18To19(),
        Migration19To20(),
        Migration20To21(),
        Migration21To22(),
        Migration22To23(),
        Migration23To24(),
    )
