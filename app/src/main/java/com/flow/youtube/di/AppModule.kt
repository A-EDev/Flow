package com.flow.youtube.di

import android.content.Context
import com.flow.youtube.FlowApplication
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideYouTube(): com.flow.youtube.innertube.YouTube {
        return com.flow.youtube.innertube.YouTube
    }
}
