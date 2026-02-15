package com.flow.youtube.notification

import android.content.Context
import android.util.Log
import androidx.work.*
import com.flow.youtube.BuildConfig
import com.flow.youtube.data.local.LocalDataManager
import com.flow.youtube.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker that checks for application updates in the background.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "update_check_work"
        private const val TAG = "UpdateCheckWorker"
        private const val COOLDOWN_HOURS = 12L

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                12, TimeUnit.HOURS
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
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Scheduled update check every 12 hours")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG && !isForcedCheck()) {
            Log.d(TAG, "Skipping background update check in DEBUG mode")
            return@withContext Result.success()
        }

        try {
            val dataManager = LocalDataManager(applicationContext)
            val lastCheck = dataManager.lastUpdateCheck.first()
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastCheck < TimeUnit.HOURS.toMillis(COOLDOWN_HOURS) && !isForcedCheck()) {
                Log.d(TAG, "Skipping check due to cooldown")
                return@withContext Result.success()
            }

            Log.d(TAG, "Checking for updates...")
            val updateInfo = UpdateManager.checkForUpdate(BuildConfig.VERSION_NAME)

            if (updateInfo != null && updateInfo.isNewer) {
                Log.d(TAG, "New version found: ${updateInfo.version}")
                
                NotificationHelper.showUpdateNotification(
                    applicationContext,
                    updateInfo.version,
                    updateInfo.changelog,
                    updateInfo.downloadUrl
                )
            } else {
                Log.d(TAG, "No new updates found")
            }

            dataManager.setLastUpdateCheck(currentTime)
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
    
    private fun isForcedCheck(): Boolean {
        return inputData.getBoolean("force", false)
    }
}
