package com.flow.youtube.player.surface

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.PlaceholderSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@UnstableApi
class SurfaceManager(
    private val appContext: Context?
) {
    companion object {
        private const val TAG = "SurfaceManager"
    }
    
    private var surfaceHolder: SurfaceHolder? = null
    private var placeholderSurface: PlaceholderSurface? = null
    
    var isSurfaceReady: Boolean = false
        private set
    
    private val _surfaceReadyFlow = MutableStateFlow(false)
    
    /**
     * Get the current surface holder.
     */
    fun getSurfaceHolder(): SurfaceHolder? = surfaceHolder
    
    /**
     * Attach a video surface to the player.
     * Uses getSurface() approach like NewPipe for better compatibility.
     */
    fun attachVideoSurface(holder: SurfaceHolder?, player: ExoPlayer?): Boolean {
        if (holder == null) {
            Log.w(TAG, "attachVideoSurface called with null holder")
            surfaceHolder = null
            return false
        }

        surfaceHolder = holder
        Log.d(TAG, "attachVideoSurface: stored holder. Player instance is ${if(player==null) "null" else "not null"}")

        // A real surface is back, drop any placeholder we were using
        placeholderSurface?.let { placeholder ->
            runCatching { placeholder.release() }
            placeholderSurface = null
        }

        if (player == null) {
            Log.d(TAG, "Player not initialized yet; surface will be attached later")
            return false
        }

        return runCatching {
            val surface = holder.surface
            if (surface != null && surface.isValid) {
                Log.d(TAG, "Attempting to attach surface ${surface.hashCode()} to player")
                player.setVideoSurface(surface)
                Log.d(TAG, "Surface attached to player via getSurface() (NewPipe approach)")
                _surfaceReadyFlow.value = true
                setSurfaceReady(true)
                true
            } else {
                Log.w(TAG, "Surface holder not yet valid; awaiting callback")
                false
            }
        }.getOrElse { error ->
            Log.e(TAG, "Failed to bind surface to player", error)
            false
        }
    }
    
    /**
     * Detach the video surface from the player.
     */
    fun detachVideoSurface(holder: SurfaceHolder?, player: ExoPlayer?, context: Context?) {
        // If specific holder provided, check if it matches current
        if (holder != null && holder != surfaceHolder) {
            Log.d(TAG, "detachVideoSurface ignored: holder mismatch (stale surface)")
            return
        }

        Log.d(TAG, "detachVideoSurface called")
        // NOTE: We do NOT clear surfaceHolder here to support reuse scenarios
        
        try {
            if (player != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context != null) {
                    // Try to reuse the placeholder surface if valid
                    if (placeholderSurface == null || placeholderSurface?.isValid == false) {
                        try {
                            runCatching { placeholderSurface?.release() }
                            placeholderSurface = PlaceholderSurface.newInstance(context, false)
                        } catch(e: Exception) {
                            Log.w(TAG, "Failed to create placeholder surface", e)
                        }
                    }
                    
                    placeholderSurface?.let {
                        player.setVideoSurface(it)
                        Log.d(TAG, "Attached placeholder surface (surface detached temporarily)")
                    } ?: run {
                        player.clearVideoSurface()
                    }
                } else {
                    player.clearVideoSurface()
                }
            } else {
                player?.clearVideoSurface()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach placeholder surface", e)
        }

        _surfaceReadyFlow.value = false
    }
    
    /**
     * Explicitly clear surface holder reference and placeholder.
     * Called when the screen is actually disposed.
     */
    fun clearSurface(player: ExoPlayer?) {
        Log.d(TAG, "clearSurface() called")
        surfaceHolder = null
        placeholderSurface?.let { surface ->
            runCatching { surface.release() }
        }
        placeholderSurface = null
        isSurfaceReady = false
        _surfaceReadyFlow.value = false
        player?.clearVideoSurface()
    }
    
    /**
     * Reattach surface to player if holder is still valid.
     * Used after player initialization or recreation.
     */
    fun reattachSurfaceIfValid(player: ExoPlayer?): Boolean {
        surfaceHolder?.let { holder ->
            return runCatching {
                val surface = holder.surface
                if (surface != null && surface.isValid) {
                    player?.setVideoSurface(surface)
                    Log.d(TAG, "Reattached preserved surface ${surface.hashCode()}")
                    setSurfaceReady(true)
                    true
                } else {
                    Log.w(TAG, "Surface holder present but surface invalid or null")
                    false
                }
            }.getOrElse { e ->
                Log.e(TAG, "Failed to reattach surface: ${e.message}", e)
                false
            }
        }
        return false
    }
    
    /**
     * Set surface readiness state.
     */
    fun setSurfaceReady(ready: Boolean) {
        isSurfaceReady = ready
        _surfaceReadyFlow.value = ready
        Log.d(TAG, "Surface ready: $ready")
    }
    
    /**
     * Suspend function that waits for a real surface to be attached.
     * Returns true if surface became ready within timeout.
     */
    suspend fun awaitSurfaceReady(timeoutMillis: Long = 1000): Boolean {
        // Check if surfaceHolder is valid
        if (surfaceHolder != null) {
            val validSurface = runCatching { surfaceHolder?.surface?.isValid == true }.getOrDefault(false)
            if (validSurface) {
                Log.d(TAG, "Surface already ready, proceeding immediately")
                _surfaceReadyFlow.value = true
                return true
            } else {
                Log.d(TAG, "Surface holder exists but surface invalid - waiting for callback")
            }
        }
        
        Log.d(TAG, "Waiting for surface to be ready (timeout: ${timeoutMillis}ms)...")
        
        val result = withTimeoutOrNull(timeoutMillis) {
            _surfaceReadyFlow.first { it }
            true
        }
        
        return if (result == true) {
            Log.d(TAG, "Surface became ready!")
            true
        } else {
            Log.w(TAG, "Timeout waiting for surface after ${timeoutMillis}ms")
            val nowValid = runCatching { surfaceHolder?.surface?.isValid == true }.getOrDefault(false)
            if (nowValid) {
                Log.d(TAG, "Surface valid now despite timeout - proceeding")
                _surfaceReadyFlow.value = true
                return true
            }
            false
        }
    }
    
    /**
     * Check if the current surface is valid and ready for rendering.
     */
    fun isSurfaceValid(): Boolean {
        return runCatching { surfaceHolder?.surface?.isValid == true }.getOrDefault(false)
    }
    
    /**
     * Release all surface resources.
     */
    fun release(player: ExoPlayer?) {
        try {
            player?.clearVideoSurface()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear surface during release", e)
        }

        placeholderSurface?.let { surface ->
            runCatching { surface.release() }
        }
        placeholderSurface = null
        
        isSurfaceReady = false
        _surfaceReadyFlow.value = false
        surfaceHolder = null
    }
}
