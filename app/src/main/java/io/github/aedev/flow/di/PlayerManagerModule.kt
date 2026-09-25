package io.github.aedev.flow.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.LocalCopySource
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NetworkIoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Transitional providers around the player-path singletons so consumers can inject them instead of
 * reaching for the static accessors. Every provider delegates to the existing singleton, so object
 * identity and first-creation timing are unchanged (see CLAUDE.md, DI rule 10).
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerManagerModule {
    @Provides
    fun provideEnhancedPlayerManager(videoDownloadManager: VideoDownloadManager): EnhancedPlayerManager =
        EnhancedPlayerManager.getInstance().also {
            it.localCopySource =
                LocalCopySource { videoId ->
                    LocalMediaIds.videoUri(videoId)?.toString() ?: videoDownloadManager.localCopyPath(videoId)
                }
        }

    @Provides
    @NetworkIoDispatcher
    fun provideNetworkIoDispatcher(): CoroutineDispatcher = PerformanceDispatcher.networkIO

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = PerformanceDispatcher.diskIO
}
