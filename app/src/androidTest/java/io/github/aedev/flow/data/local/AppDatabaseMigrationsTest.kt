package io.github.aedev.flow.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.room.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationsTest {
    private val testDb = "migration-test"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            instrumentation = instrumentation,
            databaseClass = AppDatabase::class,
            driver = AndroidSQLiteDriver(),
            file = instrumentation.targetContext.getDatabasePath(testDb),
        )

    @Test
    fun migrateAll() =
        runTest {
            // Create earliest version of the database, for which the schema exist in
            // "app/schemas" dir.
            val connection = helper.createDatabase(24)
            connection.close()

            // Create latest version of the database.
            val db =
                Room
                    .databaseBuilder<AppDatabase>(instrumentation.targetContext, testDb)
                    .setDriver(AndroidSQLiteDriver())
                    .build()

            // Open the database, Room validates the schema once all migrations
            // execute.
            db.useReaderConnection { _ ->
                // Open the db for the migrations to take place.
                // Perform additional validation to validate if data survived the migrations.
            }

            db.close()
        }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
