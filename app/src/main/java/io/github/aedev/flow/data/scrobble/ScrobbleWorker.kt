package io.github.aedev.flow.data.scrobble

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** Sends the queued listens once there is a connection, retrying with backoff while a service is down. */
class ScrobbleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun scrobbler(): Scrobbler
    }

    override suspend fun doWork(): Result {
        val scrobbler = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).scrobbler()
        return if (scrobbler.flush()) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "flow_scrobble_flush"
        private const val BACKOFF_MINUTES = 5L

        fun enqueue(context: Context) {
            val work =
                OneTimeWorkRequestBuilder<ScrobbleWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        }
    }
}
