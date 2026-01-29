package com.flow.youtube.di

import android.content.Context
import androidx.room.Room
import com.flow.youtube.data.local.AppDatabase
import com.flow.youtube.data.local.dao.NotificationDao
import com.flow.youtube.data.local.dao.PlaylistDao
import com.flow.youtube.data.local.dao.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "flow_database"
        ).fallbackToDestructiveMigration() // Reset on schema change for now
         .build()
    }

    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao {
        return database.videoDao()
    }

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideNotificationDao(database: AppDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    fun provideCacheDao(database: AppDatabase): com.flow.youtube.data.local.dao.CacheDao {
        return database.cacheDao()
    }
}
