package io.github.aedev.flow.di

import android.content.Context
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.repository.YouTubeRepository
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
    fun provideYouTubeRepository(playerPreferences: PlayerPreferences): YouTubeRepository {
        return YouTubeRepository.getInstance(playerPreferences)
    }

    @Provides
    @Singleton
    fun provideSubscriptionRepository(@ApplicationContext context: Context): io.github.aedev.flow.data.local.SubscriptionRepository {
        return io.github.aedev.flow.data.local.SubscriptionRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideLikedVideosRepository(@ApplicationContext context: Context): io.github.aedev.flow.data.local.LikedVideosRepository {
        return io.github.aedev.flow.data.local.LikedVideosRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideViewHistory(@ApplicationContext context: Context): io.github.aedev.flow.data.local.ViewHistory {
        return io.github.aedev.flow.data.local.ViewHistory.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideInterestProfile(@ApplicationContext context: Context): io.github.aedev.flow.data.recommendation.InterestProfile {
        return io.github.aedev.flow.data.recommendation.InterestProfile.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMusicPlaylistRepository(@ApplicationContext context: Context): io.github.aedev.flow.data.music.PlaylistRepository {
        return io.github.aedev.flow.data.music.PlaylistRepository(context)
    }


    // VideoDownloadManager is now @Singleton @Inject — Hilt provides it automatically
    @Provides
    @Singleton
    fun providePlayerPreferences(@ApplicationContext context: Context): io.github.aedev.flow.data.local.PlayerPreferences {
        return io.github.aedev.flow.data.local.PlayerPreferences(context)
    }

    @Provides
    @Singleton
    fun provideShortsRepository(@ApplicationContext context: Context): io.github.aedev.flow.data.shorts.ShortsRepository {
        return io.github.aedev.flow.data.shorts.ShortsRepository.getInstance(context)
    }
}
