package com.flow.youtube.data.recommendation

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that refreshes the Flow recommendation feed in the background.
 * Scheduled to run every 4-6 hours to keep recommendations fresh.
 */
class RecommendationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "RecommendationWorker"
        private const val WORK_NAME = "flow_recommendation_refresh"
        
        /**
         * Schedule periodic recommendation refresh.
         * Runs every 4 hours with flex interval of 2 hours.
         */
        fun schedulePeriodicRefresh(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<RecommendationWorker>(
                repeatInterval = 4,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 2,
                flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.MINUTES
                )
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            
            Log.d(TAG, "Scheduled periodic recommendation refresh")
        }
        
        /**
         * Request an immediate one-time refresh.
         * Use this when user pulls to refresh.
         */
        fun requestImmediateRefresh(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<RecommendationWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("${WORK_NAME}_immediate")
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            Log.d(TAG, "Requested immediate recommendation refresh")
        }
        
        /**
         * Cancel all scheduled work
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork("${WORK_NAME}_immediate")
        }
        
        /**
         * Get the status of the recommendation worker
         */
        fun getWorkStatus(context: Context) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_NAME)
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting recommendation refresh work...")
        
        return try {
            val repository = RecommendationRepository.getInstance(applicationContext)
            val feed = repository.refreshFeed()
            
            if (feed.isEmpty()) {
                Log.w(TAG, "Refresh returned empty feed")
                Result.retry()
            } else {
                Log.d(TAG, "Recommendation refresh complete: ${feed.size} videos")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recommendation refresh failed", e)
            
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
