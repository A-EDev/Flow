package io.github.aedev.flow.notification

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.network.AppProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that checks for new videos from subscribed channels
 * using lightweight RSS feeds.
 */
class SubscriptionCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        const val WORK_NAME = "subscription_check_work_v2"
        private const val LEGACY_WORK_NAME = "subscription_check_work"
        private const val IMMEDIATE_WORK_NAME = "subscription_check_work_now"
        private const val TAG = "SubscriptionCheckWorker"

        // RSS Feed URL format
        private const val RSS_URL_FORMAT = "https://www.youtube.com/feeds/videos.xml?channel_id=%s"

        /**
         * Schedule periodic subscription checks
         * @param context Application context
         * @param intervalMinutes How often to check (default: 360 minutes / 6 hours)
         */
        suspend fun schedulePeriodicCheck(
            context: Context,
            intervalMinutes: Long = 360,
            reschedule: Boolean = false,
        ) {
            val notificationsEnabled = PlayerPreferences(context).notificationsEnabled.first()
            if (!notificationsEnabled) {
                cancelScheduledChecks(context)
                Log.d(TAG, "Skipping subscription check scheduling because notifications are disabled")
                return
            }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<SubscriptionCheckWorker>(
                    intervalMinutes,
                    TimeUnit.MINUTES,
                ).setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).build()

            WorkManager.getInstance(context).apply {
                cancelUniqueWork(LEGACY_WORK_NAME)
                enqueueUniquePeriodicWork(
                    WORK_NAME,
                    periodicWorkPolicy(reschedule),
                    workRequest,
                )
            }

            Log.d(TAG, "Scheduled periodic subscription check every $intervalMinutes minutes")
        }

        /**
         * Cancel scheduled subscription checks
         */
        fun cancelScheduledChecks(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(WORK_NAME)
                cancelUniqueWork(LEGACY_WORK_NAME)
            }
            Log.d(TAG, "Cancelled scheduled subscription checks")
        }

        /**
         * Run an immediate one-time check.
         */
        fun runImmediateCheck(context: Context) {
            val workRequest =
                OneTimeWorkRequestBuilder<SubscriptionCheckWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
            Log.d(TAG, "Started immediate subscription check")
        }
    }

    // Create a single OkHttpClient instance
    private val client =
        AppProxyManager
            .applyTo(OkHttpClient.Builder())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting subscription check via RSS...")

            if (!PlayerPreferences(applicationContext).notificationsEnabled.first()) {
                Log.d(TAG, "Notifications disabled, skipping subscription check")
                return@withContext Result.success()
            }

            try {
                val subscriptionRepository = SubscriptionRepository.getInstance(applicationContext)
                val allSubscriptions = subscriptionRepository.getAllSubscriptions().first()
                val subscriptions = allSubscriptions.filter { it.isNotificationEnabled }

                if (subscriptions.isEmpty()) {
                    Log.d(TAG, "No subscriptions with notifications enabled to check")
                    return@withContext Result.success()
                }

                Log.d(TAG, "Checking ${subscriptions.size} subscriptions")

                val newVideos = mutableListOf<NotificationHelper.NewVideoEntry>()

                // Process in parallel chunks to keep network efficient
                val chunkSize = 10
                subscriptions.chunked(chunkSize).forEach { chunk ->
                    coroutineScope {
                        chunk
                            .map { subscription ->
                                async {
                                    try {
                                        checkChannel(subscription, subscriptionRepository)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error checking channel ${subscription.channelName}", e)
                                        emptyList()
                                    }
                                }
                            }.awaitAll()
                            .flatten()
                            .let { newVideos.addAll(it) }
                    }
                }

                if (newVideos.isNotEmpty()) {
                    NotificationHelper.showSubscriptionUpdates(applicationContext, newVideos)
                }

                Log.d(TAG, "Subscription check complete. Found ${newVideos.size} new videos.")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error during subscription check", e)
                Result.retry()
            }
        }

    private suspend fun checkChannel(
        subscription: io.github.aedev.flow.data.local.ChannelSubscription,
        repository: SubscriptionRepository,
    ): List<NotificationHelper.NewVideoEntry> {
        val url = String.format(RSS_URL_FORMAT, subscription.channelId)
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return emptyList()
            }

            val xmlContent = response.body?.string()
            response.close()

            if (xmlContent.isNullOrEmpty()) return emptyList()

            val entries = SubscriptionRssParser.parse(xmlContent)
            val latestVideo = entries.firstOrNull() ?: return emptyList()

            if (subscription.lastVideoId == latestVideo.id) return emptyList()

            val newEntries = SubscriptionRssParser.newEntriesSince(entries, subscription.lastVideoId)

            // Update local DB
            repository.updateChannelLatestVideo(subscription.channelId, latestVideo.id)

            if (newEntries.isNotEmpty()) {
                Log.d(TAG, "${newEntries.size} new video(s) for ${subscription.channelName}")
            }

            return newEntries.map { video ->
                NotificationHelper.NewVideoEntry(
                    channelName = subscription.channelName,
                    videoTitle = video.title,
                    videoId = video.id,
                    thumbnailUrl = video.thumbnailUrl,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check RSS for ${subscription.channelName}: ${e.message}")
        }
        return emptyList()
    }
}
