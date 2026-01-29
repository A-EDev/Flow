package com.flow.youtube.player.config

object PlayerConfig {
    
    const val TAG = "EnhancedPlayerManager"
    
    // ===== Cache Configuration =====
    /** Maximum cache size in bytes (500 MB) */
    const val CACHE_SIZE_BYTES = 500L * 1024L * 1024L
    
    /** Cache directory name */
    const val CACHE_DIR_NAME = "exoplayer"
    
    // ===== Buffer Configuration =====
    /** Allocator buffer size (64 KB optimal for DASH segments) */
    const val ALLOCATOR_BUFFER_SIZE = 64 * 1024
    
    /** Back buffer duration in milliseconds (15 seconds for instant rewind) */
    const val BACK_BUFFER_DURATION_MS = 15_000
    
    // ===== Bandwidth Thresholds =====
    /** Initial bandwidth estimate in bits per second (5 Mbps) */
    const val INITIAL_BANDWIDTH_ESTIMATE = 5_000_000L
    
    /** Bandwidth threshold for 4K quality (15 Mbps) */
    const val BANDWIDTH_4K = 15_000_000L
    
    /** Bandwidth threshold for 1440p quality (10 Mbps) */
    const val BANDWIDTH_1440P = 10_000_000L
    
    /** Bandwidth threshold for 1080p quality (6 Mbps) */
    const val BANDWIDTH_1080P = 6_000_000L
    
    /** Bandwidth threshold for 720p quality (3 Mbps) */
    const val BANDWIDTH_720P = 3_000_000L
    
    /** Bandwidth threshold for 480p quality (1.5 Mbps) */
    const val BANDWIDTH_480P = 1_500_000L
    
    /** Bandwidth threshold for 360p quality (800 Kbps) */
    const val BANDWIDTH_360P = 800_000L
    
    // ===== Quality Adaptation =====
    /** Interval for bandwidth checks during playback (5 seconds) */
    const val BANDWIDTH_CHECK_INTERVAL_MS = 5000L
    
    /** Upgrade threshold - need 30% more bandwidth to upgrade */
    const val QUALITY_UPGRADE_THRESHOLD = 1.3f
    
    /** Downgrade threshold - downgrade if bandwidth drops to 70% */
    const val QUALITY_DOWNGRADE_THRESHOLD = 0.7f
    
    /** Maximum stream errors before quality downgrade */
    const val MAX_STREAM_ERRORS = 2
    
    /** Buffering count threshold before quality downgrade */
    const val BUFFERING_COUNT_THRESHOLD = 3
    
    // ===== Network Configuration =====
    /** Maximum concurrent requests per host for adaptive streaming */
    const val MAX_REQUESTS_PER_HOST = 20
    
    /** Maximum total concurrent requests */
    const val MAX_REQUESTS = 40
    
    /** Connection pool size */
    const val CONNECTION_POOL_SIZE = 15
    
    /** Connection pool keep-alive duration in minutes */
    const val CONNECTION_POOL_KEEP_ALIVE_MINUTES = 5L
    
    /** Connect timeout in seconds */
    const val CONNECT_TIMEOUT_SECONDS = 15L
    
    /** Read timeout in seconds */
    const val READ_TIMEOUT_SECONDS = 30L
    
    /** Write timeout in seconds */
    const val WRITE_TIMEOUT_SECONDS = 15L
    
    // ===== Position Tracking =====
    /** Position tracker polling interval in milliseconds */
    const val POSITION_TRACKER_INTERVAL_MS = 500L
    
    /** Auto-save interval in milliseconds (30 seconds) */
    const val AUTO_SAVE_INTERVAL_MS = 30_000L
    
    /** Stuck detection threshold (number of checks with no position change) */
    const val STUCK_DETECTION_THRESHOLD = 2
    
    // ===== Surface Configuration =====
    /** Default surface ready timeout in milliseconds */
    const val DEFAULT_SURFACE_TIMEOUT_MS = 1500L
    
    // ===== Error Recovery =====
    /** Delay before retry after error in milliseconds */
    const val ERROR_RETRY_DELAY_MS = 1000L
    
    // ===== Video Size Constraints =====
    /** Maximum video width for track selection */
    const val MAX_VIDEO_WIDTH = 1920
    
    /** Maximum video height for track selection */
    const val MAX_VIDEO_HEIGHT = 1080
    
    /**
     * Calculate target quality height based on bandwidth in bits per second.
     */
    fun calculateTargetQualityForBandwidth(bandwidthBps: Long): Int {
        return when {
            bandwidthBps > BANDWIDTH_4K -> 2160
            bandwidthBps > BANDWIDTH_1440P -> 1440
            bandwidthBps > BANDWIDTH_1080P -> 1080
            bandwidthBps > BANDWIDTH_720P -> 720
            bandwidthBps > BANDWIDTH_480P -> 480
            bandwidthBps > BANDWIDTH_360P -> 360
            else -> 240
        }
    }
    
    /**
     * Calculate initial quality target based on estimated bandwidth.
     */
    fun calculateInitialQualityTarget(estimatedBandwidth: Long): Int {
        return when {
            estimatedBandwidth > 10_000_000 -> 1080 // 10+ Mbps = 1080p
            estimatedBandwidth > 5_000_000 -> 720   // 5+ Mbps = 720p  
            estimatedBandwidth > 2_000_000 -> 480   // 2+ Mbps = 480p
            estimatedBandwidth > 1_000_000 -> 360   // 1+ Mbps = 360p
            else -> 240                              // Low bandwidth = 240p
        }
    }
}
