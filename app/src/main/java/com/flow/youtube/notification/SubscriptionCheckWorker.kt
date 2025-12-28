package com.flow.youtube.notification

import android.content.Context
import android.util.Log
import androidx.work.*
import com.flow.youtube.data.local.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that checks for new videos from subscribed channels
 * and sends notifications when new content is found
 */
class SubscriptionCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "subscription_check_work"
        private const val TAG = "SubscriptionCheckWorker"
        private const val PREFS_NAME = "subscription_check_prefs"
        private const val KEY_LAST_CHECK_PREFIX = "last_check_"
        
        /**
         * Schedule periodic subscription checks
         * @param context Application context
         * @param intervalMinutes How often to check (default: 30 minutes)
         */
        fun schedulePeriodicCheck(context: Context, intervalMinutes: Long = 30) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<SubscriptionCheckWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            
            Log.d(TAG, "Scheduled periodic subscription check every $intervalMinutes minutes")
        }
        
        /**
         * Cancel scheduled subscription checks
         */
        fun cancelScheduledChecks(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled scheduled subscription checks")
        }
        
        /**
         * Run an immediate one-time check
         */
        fun runImmediateCheck(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<SubscriptionCheckWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Started immediate subscription check")
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting subscription check...")
        
        try {
            val subscriptionRepository = SubscriptionRepository.getInstance(applicationContext)
            val subscriptions = subscriptionRepository.getAllSubscriptions().first()
            
            if (subscriptions.isEmpty()) {
                Log.d(TAG, "No subscriptions to check")
                return@withContext Result.success()
            }
            
            var newVideoCount = 0
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            for (subscription in subscriptions.take(10)) { // Limit to 10 channels per check to save battery
                try {
                    val channelUrl = "https://www.youtube.com/channel/${subscription.channelId}"
                    val channelInfo = ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)
                    
                    // Try to get videos from channel tabs
                    val videoTab = channelInfo.tabs.find { 
                        it.contentFilters.contains(ChannelTabs.VIDEOS) 
                    }
                    
                    if (videoTab != null) {
                        try {
                            val tabExtractor = ServiceList.YouTube.getChannelTabExtractor(videoTab)
                            tabExtractor.fetchPage()
                            val items = tabExtractor.initialPage?.items ?: emptyList()
                            
                            // Get the latest video
                            val latestVideo = items.filterIsInstance<StreamInfoItem>().firstOrNull()
                            
                            if (latestVideo != null) {
                                val lastCheckKey = KEY_LAST_CHECK_PREFIX + subscription.channelId
                                val lastCheckedVideoId = prefs.getString(lastCheckKey, null)
                                
                                // Robust video ID extraction
                                val currentVideoId = extractVideoId(latestVideo.url)
                                
                                if (currentVideoId != null && lastCheckedVideoId != currentVideoId) {
                                    // New video found!
                                    Log.d(TAG, "New video from ${subscription.channelName}: ${latestVideo.name}")
                                    
                                    NotificationHelper.showNewVideoNotification(
                                        context = applicationContext,
                                        channelName = subscription.channelName,
                                        videoTitle = latestVideo.name,
                                        videoId = currentVideoId,
                                        thumbnailUrl = latestVideo.thumbnails.maxByOrNull { it.height }?.url,
                                        channelId = subscription.channelId
                                    )
                                    
                                    // Update last checked video
                                    prefs.edit().putString(lastCheckKey, currentVideoId).apply()
                                    newVideoCount++
                                }
                            }
                        } catch (tabError: Exception) {
                            Log.w(TAG, "Could not fetch videos tab for ${subscription.channelName}, trying RSS fallback", tabError)
                            // Fallback to RSS if extractor fails
                            val rssVideo = checkRssFallback(subscription.channelId)
                            if (rssVideo != null) {
                                val lastCheckKey = KEY_LAST_CHECK_PREFIX + subscription.channelId
                                val lastCheckedVideoId = prefs.getString(lastCheckKey, null)
                                
                                if (lastCheckedVideoId != rssVideo.id) {
                                    Log.d(TAG, "New video (via RSS) from ${subscription.channelName}: ${rssVideo.title}")
                                    NotificationHelper.showNewVideoNotification(
                                        context = applicationContext,
                                        channelName = subscription.channelName,
                                        videoTitle = rssVideo.title,
                                        videoId = rssVideo.id,
                                        thumbnailUrl = rssVideo.thumbnailUrl,
                                        channelId = subscription.channelId
                                    )
                                    prefs.edit().putString(lastCheckKey, rssVideo.id).apply()
                                    newVideoCount++
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking channel ${subscription.channelName}", e)
                    // Continue with other channels even if one fails
                }
            }
            
            // Show summary notification if multiple new videos
            if (newVideoCount > 1) {
                NotificationHelper.showNewVideosSummary(applicationContext, newVideoCount)
            }
            
            Log.d(TAG, "Subscription check complete. Found $newVideoCount new videos.")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during subscription check", e)
            Result.retry()
        }
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("v=([^&]+)"),
            Regex("shorts/([^/?]+)"),
            Regex("youtu.be/([^/?]+)"),
            Regex("embed/([^/?]+)"),
            Regex("v/([^/?]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { null }
    }

    private data class SimpleVideo(val id: String, val title: String, val thumbnailUrl: String?)

    private fun checkRssFallback(channelId: String): SimpleVideo? {
        return try {
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
            val connection = java.net.URL(rssUrl).openConnection() as java.net.HttpURLConnection
            connection.readTimeout = 10000
            connection.connectTimeout = 10000
            
            val inputStream = connection.inputStream
            val content = inputStream.bufferedReader().use { it.readText() }
            
            // Very simple XML parsing for RSS
            val entryStart = content.indexOf("<entry>")
            if (entryStart == -1) return null
            
            val videoId = content.substringAfter("<yt:videoId>", "").substringBefore("</yt:videoId>")
            val title = content.substringAfter("<title>", "").substringBefore("</title>")
            val thumbnail = content.substringAfter("<media:thumbnail url=\"", "").substringBefore("\"")
            
            if (videoId.isNotEmpty()) {
                SimpleVideo(videoId, title, thumbnail.ifEmpty { null })
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "RSS fallback failed for $channelId", e)
            null
        }
    }
}
