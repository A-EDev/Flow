package com.flow.youtube.player.resolver

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

object MediaSourceBuilder {
    
    fun buildDashSource(
        dataSourceFactory: DataSource.Factory,
        manifestString: String,
        baseUri: Uri
    ): MediaSource {
        val parser = DashManifestParser()
        val manifest = parser.parse(
            baseUri,
            ByteArrayInputStream(manifestString.toByteArray(StandardCharsets.UTF_8))
        )
        
        return DashMediaSource.Factory(dataSourceFactory)
            .createMediaSource(manifest, MediaItem.fromUri(baseUri))
    }

    fun buildProgressiveSource(
        dataSourceFactory: DataSource.Factory,
        uri: Uri
    ): MediaSource {
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))
    }

    fun buildHlsSource(
        dataSourceFactory: DataSource.Factory,
        uri: Uri
    ): MediaSource {
        return HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))
    }
}
