package com.flow.youtube.player.seekbarpreview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import android.util.SparseArray
import android.view.View
import androidx.collection.SparseArrayCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.TimeBar
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamSegment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Helper class for seekbar preview thumbnails.
 * Based on NewPipe's SeekbarPreviewThumbnailHelper.
 */
class SeekbarPreviewThumbnailHelper(
    private val context: Context,
    private val player: Player,
    private val timeBar: TimeBar? = null
) {

    private val disposables = CompositeDisposable()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var streamInfo: StreamInfo? = null
    private var previewHolder: SeekbarPreviewThumbnailHolder? = null
    private var frameset: Frameset? = null
    private val thumbnailCache = SparseArrayCompat<Bitmap>()
    private var qualityPreference = SeekbarPreviewThumbnailQuality.HIGH_QUALITY

    /**
     * Initializes the seekbar preview functionality.
     */
    fun setupSeekbarPreview(streamInfo: StreamInfo) {
        this.streamInfo = streamInfo

        // Extract frameset from stream info if available
        frameset = extractFramesetFromStreamInfo(streamInfo)

        // Create preview holder if timeBar is available
        timeBar?.let { tb ->
            previewHolder = SeekbarPreviewThumbnailHolder().apply {
                setup(context, tb)
            }
        }

        // Setup thumbnail loading observable if timeBar is available
        timeBar?.let { setupThumbnailLoading() }
    }

    /**
     * Extracts Frameset data from StreamInfo.
     * Based on NewPipe's implementation that extracts storyboard data.
     */
    private fun extractFramesetFromStreamInfo(streamInfo: StreamInfo): Frameset? {
        return try {
            // Get preview frames from stream info
            val previewFrames = streamInfo.previewFrames
            // Return the first frameset if available
            previewFrames.firstOrNull()
        } catch (e: Exception) {
            Log.w("SeekbarPreviewThumbnailHelper", "Could not extract frameset from stream info", e)
            null
        }
    }

    /**
     * Sets up reactive thumbnail loading.
     */
    private fun setupThumbnailLoading() {
        val tb = timeBar ?: return

        // Create observable for seek position changes
        val seekPositionObservable = Observable.create<Long> { emitter ->
            val listener = object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) {
                    emitter.onNext(position)
                }

                override fun onScrubMove(timeBar: TimeBar, position: Long) {
                    emitter.onNext(position)
                }

                override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                    // Complete on scrub stop
                }
            }

            tb.addListener(listener)

            emitter.setCancellable {
                tb.removeListener(listener)
            }
        }

        // Process seek positions and load thumbnails
        val disposable = seekPositionObservable
            .debounce(100, TimeUnit.MILLISECONDS)
            .observeOn(Schedulers.io())
            .map { position -> loadThumbnailForPosition(position) }
            .subscribe(
                { bitmap -> previewHolder?.setImageBitmap(bitmap) },
                { error -> previewHolder?.setImageBitmap(null) }
            )

        disposables.add(disposable)
    }

    /**
     * Loads thumbnail for a specific position in the video.
     * Public method for external access.
     */
    fun loadThumbnailForPosition(positionMs: Long): Bitmap {
        return try {
            // Check cache first
            val cachedBitmap = thumbnailCache.get(positionMs.toInt())
            if (cachedBitmap != null) {
                return cachedBitmap
            }

            // Try to load from frameset if available
            frameset?.let { fs ->
                return loadThumbnailFromFrameset(fs, positionMs)
            }

            // Fallback to segment-based thumbnails
            val segment = findSegmentForPosition(positionMs)
            segment?.let { loadSegmentThumbnail(it) } ?: createPlaceholderThumbnail(null)
        } catch (e: Exception) {
            createPlaceholderThumbnail(null)
        }
    }

    /**
     * Loads thumbnail from Frameset storyboard.
     */
    private fun loadThumbnailFromFrameset(frameset: Frameset, positionMs: Long): Bitmap {
        // Get frame bounds for the position
        val frameBounds = frameset.getFrameBoundsAt(positionMs)

        // frameBounds[0] = storyboard index
        // frameBounds[1] = left bound
        // frameBounds[2] = top bound
        // frameBounds[3] = right bound
        // frameBounds[4] = bottom bound

        val storyboardIndex = frameBounds[0]
        val left = frameBounds[1]
        val top = frameBounds[2]
        val right = frameBounds[3]
        val bottom = frameBounds[4]

        // Get the storyboard URL
        val urls = frameset.urls
        if (storyboardIndex >= urls.size) {
            return createPlaceholderThumbnail(null)
        }

        val storyboardUrl = urls[storyboardIndex]

        // Check cache first using position as key
        val cacheKey = positionMs.toInt()
        val cachedBitmap = thumbnailCache.get(cacheKey)
        if (cachedBitmap != null) {
            return cachedBitmap
        }

        // Load storyboard image and extract frame
        return try {
            val storyboardBitmap = loadStoryboardImage(storyboardUrl)
            val frameBitmap = extractFrameFromStoryboard(
                storyboardBitmap,
                left, top, right, bottom
            )

            // Cache the result
            thumbnailCache.put(cacheKey, frameBitmap)

            frameBitmap
        } catch (e: Exception) {
            createPlaceholderThumbnail(null)
        }
    }

    /**
     * Loads storyboard image using Picasso with timeout.
     * Based on NewPipe's implementation with timeout handling.
     */
    private fun loadStoryboardImage(url: String): Bitmap {
        return try {
            Picasso.get()
                .load(url)
                .get() // Synchronous loading with default timeout
        } catch (e: Exception) {
            Log.w("SeekbarPreviewThumbnailHelper", "Failed to load storyboard image: $url", e)
            throw RuntimeException("Failed to load storyboard image", e)
        }
    }

    /**
     * Extracts a specific frame from the storyboard image using bounds.
     */
    private fun extractFrameFromStoryboard(
        storyboard: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Bitmap {
        val frameWidth = right - left
        val frameHeight = bottom - top

        // Create frame bitmap
        val frameBitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)

        // Extract the frame from storyboard
        val srcRect = Rect(left, top, right, bottom)
        val dstRect = Rect(0, 0, frameWidth, frameHeight)
        canvas.drawBitmap(storyboard, srcRect, dstRect, null)

        return frameBitmap
    }

    /**
     * Finds the stream segment that contains the given position.
     */
    private fun findSegmentForPosition(positionMs: Long): StreamSegment? {
        val segments = streamInfo?.streamSegments ?: return null

        return (segments as List<StreamSegment>).find { segment ->
            val duration = 10L // Default 10 seconds per segment
            positionMs >= segment.startTimeSeconds * 1000 &&
            positionMs < (segment.startTimeSeconds + duration) * 1000
        }
    }

    /**
     * Loads thumbnail for a specific segment.
     */
    private fun loadSegmentThumbnail(segment: StreamSegment): Bitmap {
        // In a real implementation, this would:
        // 1. Check if thumbnail is cached
        // 2. Load from network if not cached
        // 3. Generate preview from video frame if no thumbnail available

        // For now, return a placeholder
        return createPlaceholderThumbnail(segment)
    }

    /**
     * Creates a placeholder thumbnail for development.
     */
    private fun createPlaceholderThumbnail(segment: StreamSegment?): Bitmap {
        return try {
            val width = 160
            val height = 90
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Draw a simple colored rectangle as placeholder
            val color = if (segment != null) {
                when ((segment.startTimeSeconds % 3).toInt()) {
                    0 -> android.graphics.Color.RED
                    1 -> android.graphics.Color.GREEN
                    else -> android.graphics.Color.BLUE
                }
            } else {
                android.graphics.Color.GRAY
            }

            canvas.drawColor(color)

            bitmap
        } catch (e: Exception) {
            // Return a simple gray bitmap on error
            Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).drawColor(android.graphics.Color.GRAY)
            }
        }
    }

    /**
     * Gets the player instance.
     */
    fun getPlayer(): Player = player

    /**
     * Cleans up resources.
     */
    fun destroy() {
        disposables.dispose()
        executor.shutdown()
        thumbnailCache.clear()
        previewHolder?.cleanup()
        previewHolder = null
    }
}

/**
 * Quality preferences for seekbar preview thumbnails.
 */
enum class SeekbarPreviewThumbnailQuality {
    HIGH_QUALITY,
    LOW_QUALITY,
    NONE
}

/**
 * Holder for seekbar preview thumbnails.
 * Manages the display of preview images during seeking.
 */
class SeekbarPreviewThumbnailHolder {

    private var previewView: View? = null
    private var timeBar: TimeBar? = null

    fun setup(context: Context, timeBar: TimeBar) {
        this.timeBar = timeBar
        // For now, it's a placeholder
    }

    fun setImageBitmap(bitmap: Bitmap?) {
        // Update the preview view with the new bitmap
        // Implementation would show/hide the preview and set the image
    }

    fun cleanup() {
        previewView = null
        timeBar = null
    }
}