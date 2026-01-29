package com.flow.youtube.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.flow.youtube.data.local.dao.CacheDao
import com.flow.youtube.data.local.dao.NotificationDao
import com.flow.youtube.data.local.dao.PlaylistDao
import com.flow.youtube.data.local.dao.VideoDao
import com.flow.youtube.data.local.entity.MusicHomeCacheEntity
import com.flow.youtube.data.local.entity.NotificationEntity
import com.flow.youtube.data.local.entity.PlaylistEntity
import com.flow.youtube.data.local.entity.PlaylistVideoCrossRef
import com.flow.youtube.data.local.entity.SubscriptionFeedEntity
import com.flow.youtube.data.local.entity.VideoEntity

@Database(
    entities = [
        VideoEntity::class, 
        PlaylistEntity::class, 
        PlaylistVideoCrossRef::class, 
        NotificationEntity::class,
        SubscriptionFeedEntity::class,
        MusicHomeCacheEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun notificationDao(): NotificationDao
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flow_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
