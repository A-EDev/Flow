package io.github.aedev.flow.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.cache.SharedPlayerCacheProvider
import io.github.aedev.flow.player.config.PlayerConfig
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider {
        return StandaloneDatabaseProvider(context)
    }

    @Provides
    @Singleton
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider
    ): SimpleCache {
        val downloadContentDirectory = File(context.getExternalFilesDir(null), "downloads")
        return SimpleCache(downloadContentDirectory, NoOpCacheEvictor(), databaseProvider)
    }

    @Provides
    @Singleton
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider
    ): SimpleCache {
        val cacheSizeMb = runBlocking { PlayerPreferences(context).mediaCacheSizeMb.first() }
        val cacheSizeBytes = PlayerConfig.cacheSizeMbToBytes(cacheSizeMb)
        return SharedPlayerCacheProvider.getOrCreate(
            context,
            databaseProvider = databaseProvider,
            maxCacheSizeBytes = if (cacheSizeBytes <= 0) PlayerConfig.CACHE_SIZE_BYTES else cacheSizeBytes
        )
    }
}
