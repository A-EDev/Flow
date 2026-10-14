package io.github.aedev.flow.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import io.github.aedev.flow.data.localmedia.MediaStoreThumbnails
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Session artwork for files on the device. A local video's artwork is its own `content://` URI,
 * which the image decoder can't read as a picture, so the notification had none; this loads a
 * frame or cover through [MediaStoreThumbnails] and leaves every other URI to [delegate].
 */
@OptIn(UnstableApi::class)
internal class MediaStoreArtworkBitmapLoader(
    private val context: Context,
    private val delegate: BitmapLoader,
    private val sizePx: Int,
) : BitmapLoader by delegate {
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (!MediaStoreThumbnails.isMediaStoreUri(uri)) return delegate.loadBitmap(uri)
        return executor.submit<Bitmap> { MediaStoreThumbnails.load(context, uri, sizePx) ?: throw IOException("No artwork for $uri") }
    }
}
