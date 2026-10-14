package io.github.aedev.flow.data.video.downloader.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.local.PlayerPreferences
import java.time.Duration

/**
 * Runs [DownloadRetagger] once per install, in the background, on any network (it reads each
 * video's watch page and cover) and never while the battery or storage is low. Videos whose watch
 * page did not load are tried again later; the last attempt tags them with what the row has.
 */
class DownloadRetagWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun downloadRetagger(): DownloadRetagger

        fun playerPreferences(): PlayerPreferences
    }

    private val dependencies by lazy { EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java) }

    override suspend fun doWork(): Result {
        val lastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
        val result =
            dependencies.downloadRetagger().run(acceptPartial = lastAttempt) { done, total ->
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            }
        if (result.deferred > 0 && !lastAttempt) return Result.retry()
        dependencies.playerPreferences().setDownloadRetagResult(result)
        return Result.success(workDataOf(KEY_TAGGED to result.tagged, KEY_SKIPPED to result.skipped))
    }

    companion object {
        const val UNIQUE_NAME = "flow_download_retag"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_TAGGED = "tagged"
        const val KEY_SKIPPED = "skipped"
        private const val MAX_ATTEMPTS = 4

        // Clear of the burst of requests the app makes as it starts.
        private val START_DELAY: Duration = Duration.ofMinutes(1)

        /** Queues the pass unless it already ran to the end on this install. */
        suspend fun scheduleOnce(
            context: Context,
            preferences: PlayerPreferences,
        ) {
            if (preferences.hasDownloadRetagResult()) return
            val work =
                OneTimeWorkRequestBuilder<DownloadRetagWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .setRequiresStorageNotLow(true)
                            .build(),
                    ).setInitialDelay(START_DELAY)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, work)
        }
    }
}

/** Where the pass over older downloads stands, for Settings › Downloads. */
sealed interface RetagStatus {
    data object Waiting : RetagStatus

    data class Running(
        val done: Int,
        val total: Int,
    ) : RetagStatus

    data class Finished(
        val result: RetagResult,
    ) : RetagStatus

    companion object {
        /** A result saved by a finished pass wins; otherwise the work's own state, read through its progress. */
        fun of(
            work: WorkInfo?,
            saved: RetagResult?,
        ): RetagStatus {
            if (saved != null) return Finished(saved)
            if (work?.state != WorkInfo.State.RUNNING) return Waiting
            val total = work.progress.getInt(DownloadRetagWorker.KEY_TOTAL, 0)
            return if (total > 0) Running(work.progress.getInt(DownloadRetagWorker.KEY_DONE, 0), total) else Waiting
        }
    }
}
