package com.flow.youtube.di

import android.content.Context
import com.flow.youtube.data.repository.YouTubeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideYouTubeRepository(): YouTubeRepository {
        return YouTubeRepository.getInstance()
    }

    @Provides
    @Singleton
    fun provideSubscriptionRepository(@ApplicationContext context: Context): com.flow.youtube.data.local.SubscriptionRepository {
        return com.flow.youtube.data.local.SubscriptionRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideLikedVideosRepository(@ApplicationContext context: Context): com.flow.youtube.data.local.LikedVideosRepository {
        return com.flow.youtube.data.local.LikedVideosRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideViewHistory(@ApplicationContext context: Context): com.flow.youtube.data.local.ViewHistory {
        return com.flow.youtube.data.local.ViewHistory.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideInterestProfile(@ApplicationContext context: Context): com.flow.youtube.data.recommendation.InterestProfile {
        return com.flow.youtube.data.recommendation.InterestProfile.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMusicPlaylistRepository(@ApplicationContext context: Context): com.flow.youtube.data.music.PlaylistRepository {
        return com.flow.youtube.data.music.PlaylistRepository(context)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(@ApplicationContext context: Context): com.flow.youtube.data.music.DownloadManager {
        return com.flow.youtube.data.music.DownloadManager(context)
    }

    @Provides
    @Singleton
    fun provideVideoDownloadManager(@ApplicationContext context: Context): com.flow.youtube.data.video.VideoDownloadManager {
        return com.flow.youtube.data.video.VideoDownloadManager.getInstance(context)
    }
    @Provides
    @Singleton
    fun providePlayerPreferences(@ApplicationContext context: Context): com.flow.youtube.data.local.PlayerPreferences {
        return com.flow.youtube.data.local.PlayerPreferences(context)
    }

    @Provides
    @Singleton
    fun provideShortsRepository(@ApplicationContext context: Context): com.flow.youtube.data.shorts.ShortsRepository {
        return com.flow.youtube.data.shorts.ShortsRepository.getInstance(context)
    }
}
