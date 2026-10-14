package io.github.aedev.flow.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.android.EntryPointAccessors
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.R
import io.github.aedev.flow.data.update.UpdateEntryPoint
import io.github.aedev.flow.data.update.UpdateFailure
import io.github.aedev.flow.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val PROGRESS_NOTIFICATION_ID = 9998
private const val READY_NOTIFICATION_ID = 9999
private const val BUFFER_SIZE = 64 * 1024
private const val PROGRESS_STEP = 0.01f
private const val PROGRESS_INTERVAL_MS = 250L
private const val PERCENT = 100

/**
 * Downloads one release APK in the foreground, hashing it as it arrives. The file is kept only if
 * its SHA-256 matches the digest GitHub published and it is a Flow package; anything else is
 * deleted and reported as a failure.
 */
class UpdateDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val version = inputData.getString(KEY_VERSION).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val url = inputData.getString(KEY_URL) ?: return@withContext fail(UpdateFailure.NETWORK)
            val expectedSha = inputData.getString(KEY_SHA256)
            setForeground(foregroundInfo(null))
            val part = UpdateFiles.partFile(applicationContext, version)
            try {
                val actualSha = download(url, part)
                setProgress(workDataOf(KEY_PHASE to PHASE_VERIFY))
                if (expectedSha != null &&
                    !expectedSha.equals(actualSha, ignoreCase = true)
                ) {
                    return@withContext fail(UpdateFailure.CHECKSUM, part)
                }
                if (!isFlowPackage(part)) return@withContext fail(UpdateFailure.PACKAGE, part)
                if (!part.renameTo(UpdateFiles.apkFile(applicationContext, version))) return@withContext fail(UpdateFailure.STORAGE, part)
                notifyReady()
                Result.success()
            } catch (e: IOException) {
                fail(if (e.message?.contains("ENOSPC") == true) UpdateFailure.STORAGE else UpdateFailure.NETWORK, part)
            }
        }

    private suspend fun download(
        url: String,
        target: File,
    ): String {
        val client =
            EntryPointAccessors
                .fromApplication(applicationContext, UpdateEntryPoint::class.java)
                .okHttpClient()
                .newBuilder()
                .cache(null)
                .build()
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val total = response.body.contentLength().takeIf { it > 0 }
            var read = 0L
            var reported = -1f
            var reportedAt = 0L
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        read += count
                        val progress = total?.let { read.toFloat() / it }
                        val now = System.currentTimeMillis()
                        if (progress != null && progress - reported >= PROGRESS_STEP && now - reportedAt >= PROGRESS_INTERVAL_MS) {
                            reported = progress
                            reportedAt = now
                            setProgress(workDataOf(KEY_PHASE to PHASE_DOWNLOAD, KEY_PROGRESS to progress))
                            setForeground(foregroundInfo(progress))
                        }
                    }
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isFlowPackage(apk: File): Boolean =
        applicationContext.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName == applicationContext.packageName

    private fun fail(
        reason: UpdateFailure,
        part: File? = null,
    ): Result {
        part?.delete()
        return Result.failure(workDataOf(KEY_FAILURE to reason.name))
    }

    private fun foregroundInfo(progress: Float?): ForegroundInfo {
        val notification =
            NotificationCompat
                .Builder(applicationContext, NotificationHelper.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(applicationContext.getString(R.string.update_downloading_notification, version))
                .setProgress(PERCENT, ((progress ?: 0f) * PERCENT).toInt(), progress == null)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    0,
                    applicationContext.getString(R.string.cancel),
                    WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
                ).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun notifyReady() {
        val open =
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(NotificationHelper.EXTRA_OPEN_UPDATE, true)
        val notification =
            NotificationCompat
                .Builder(applicationContext, NotificationHelper.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(applicationContext.getString(R.string.update_ready_notification, version))
                .setContentText(applicationContext.getString(R.string.update_ready_notification_text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setAutoCancel(true)
                .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(READY_NOTIFICATION_ID, notification) }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_SHA256 = "sha256"
        const val KEY_VERSION = "version"
        const val KEY_PHASE = "phase"
        const val KEY_PROGRESS = "progress"
        const val KEY_FAILURE = "failure"
        const val PHASE_DOWNLOAD = "download"
        const val PHASE_VERIFY = "verify"
    }
}

/** Where downloaded updates live: one directory in the cache, one file per version. */
internal object UpdateFiles {
    private const val DIRECTORY = "updates"

    private fun directory(context: Context) = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    fun apkFile(
        context: Context,
        version: String,
    ) = File(directory(context), "flow-$version.apk")

    fun partFile(
        context: Context,
        version: String,
    ) = File(directory(context), "flow-$version.apk.part")

    /** Deletes every downloaded update except [keepVersion]'s. */
    fun clean(
        context: Context,
        keepVersion: String?,
    ) {
        val keep = keepVersion?.let { apkFile(context, it).name }
        directory(context).listFiles()?.filter { it.name != keep }?.forEach { it.delete() }
    }
}
