package com.flow.youtube.notification

import android.content.Context
import android.util.Log
import androidx.work.*
import com.flow.youtube.data.local.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that checks for new videos from subscribed channels
 * using lightweight RSS feeds.
 */
class SubscriptionCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "subscription_check_work"
        private const val TAG = "SubscriptionCheckWorker"
        
        // RSS Feed URL format
        private const val RSS_URL_FORMAT = "https://www.youtube.com/feeds/videos.xml?channel_id=%s"
        
        /**
         * Schedule periodic subscription checks
         * @param context Application context
         * @param intervalMinutes How often to check (default: 15 minutes)
         */
        fun schedulePeriodicCheck(context: Context, intervalMinutes: Long = 15) {
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
    
    // Create a single OkHttpClient instance
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting subscription check via RSS...")
        
        try {
            val subscriptionRepository = SubscriptionRepository.getInstance(applicationContext)
            val subscriptions = subscriptionRepository.getAllSubscriptions().first()
            
            if (subscriptions.isEmpty()) {
                Log.d(TAG, "No subscriptions to check")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "Checking ${subscriptions.size} subscriptions")
            
            var newVideoCount = 0
            
            // Process subscriptions in parallel chunks to avoid overwhelming the network/CPU
            // but still be faster than sequential
            val chunkSize = 10
            subscriptions.chunked(chunkSize).forEach { chunk ->
                coroutineScope {
                    chunk.map { subscription ->
                        async {
                            try {
                                checkChannel(subscription, subscriptionRepository)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error checking channel ${subscription.channelName}", e)
                                false
                            }
                        }
                    }.awaitAll().forEach { if (it) newVideoCount++ }
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

    private suspend fun checkChannel(
        subscription: com.flow.youtube.data.local.ChannelSubscription,
        repository: SubscriptionRepository
    ): Boolean {
        val url = String.format(RSS_URL_FORMAT, subscription.channelId)
        val request = Request.Builder().url(url).build()
        
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return false
            }
            
            val xmlContent = response.body?.string()
            response.close()
            
            if (xmlContent.isNullOrEmpty()) return false
            
            val latestVideo = parseRssFeed(xmlContent) ?: return false
            
            // Check if it's a new video
            if (subscription.lastVideoId != latestVideo.id) {
                // Only notify if we had a previous video ID (not first sync)
                // OR if you want to notify on first sync, remove the check.
                // Usually, for "new video" notifications, we only want *new* since we started tracking.
                // However, if lastVideoId is null, it means we just subscribed or migrated.
                // Let's assume if lastVideoId is null, we just update it without notifying to avoid spam.
                
                val shouldNotify = subscription.lastVideoId != null
                
                // Update local DB
                repository.updateChannelLatestVideo(subscription.channelId, latestVideo.id)
                
                if (shouldNotify) {
                    Log.d(TAG, "New video found for ${subscription.channelName}: ${latestVideo.title}")
                    NotificationHelper.showNewVideoNotification(
                        context = applicationContext,
                        channelName = subscription.channelName,
                        videoTitle = latestVideo.title,
                        videoId = latestVideo.id,
                        thumbnailUrl = latestVideo.thumbnailUrl,
                        channelId = subscription.channelId
                    )
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check RSS for ${subscription.channelName}: ${e.message}")
        }
        return false
    }

    private data class RssVideo(val id: String, val title: String, val thumbnailUrl: String?)

    private fun parseRssFeed(xml: String): RssVideo? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var insideEntry = false
            
            var videoId: String? = null
            var title: String? = null
            var thumbnail: String? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("entry", ignoreCase = true)) {
                            insideEntry = true
                        } else if (insideEntry) {
                            when {
                                tagName.equals("videoId", ignoreCase = true) -> {
                                    videoId = parser.nextText()
                                }
                                tagName.equals("title", ignoreCase = true) -> {
                                    title = parser.nextText()
                                }
                                tagName.equals("thumbnail", ignoreCase = true) -> {
                                    thumbnail = parser.getAttributeValue(null, "url")
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("entry", ignoreCase = true)) {
                            // We only need the first entry (latest video)
                            if (!videoId.isNullOrEmpty() && !title.isNullOrEmpty()) {
                                return RssVideo(videoId, title, thumbnail)
                            }
                            return null // If first entry didn't have data, stop anyway? Or continue? 
                            // RSS feeds are ordered by date, so first entry is latest.
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RSS XML", e)
        }
        return null
    }
}
