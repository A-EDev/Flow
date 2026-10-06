package io.github.aedev.flow.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.BackupRepository
import io.github.aedev.flow.notification.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BackupCoordinatorTest {
    private val context: Context = mockk(relaxed = true)
    private val repository: BackupRepository = mockk(relaxed = true)
    private val first: Uri = mockk()
    private val second: Uri = mockk()

    private fun coordinator() = BackupCoordinator(context, repository, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

    @Before
    fun setUp() {
        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.deleteDocument(any(), any()) } returns true
        mockkObject(NotificationHelper)
        every { NotificationHelper.cancelImportNotification(any()) } returns Unit
        coEvery { NotificationHelper.showImportProgress(any(), any(), any(), any()) } returns Unit
        coEvery { NotificationHelper.showImportComplete(any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `an export started while another runs deletes the file the save dialog created`() {
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { repository.exportData(first) } coAnswers { gate.await() }
        val coordinator = coordinator()

        assertThat(coordinator.exportAppData(first)).isTrue()
        assertThat(coordinator.exportNewPipeSubscriptions(second)).isFalse()

        verify(timeout = 2_000) { DocumentsContract.deleteDocument(any(), second) }
        verify(exactly = 0) { DocumentsContract.deleteDocument(any(), first) }
        gate.cancel()
    }

    @Test
    fun `a failed export deletes its empty file and reports the failure`() =
        runBlocking {
            coEvery { repository.exportSubscriptionsAsNewPipe(first) } returns Result.failure(IllegalStateException())
            val coordinator = coordinator()

            assertThat(coordinator.exportNewPipeSubscriptions(first)).isTrue()

            val outcome = withTimeout(2_000) { coordinator.operation.first { it !is BackupOperation.Running } }
            assertThat(outcome).isInstanceOf(BackupOperation.Failed::class.java)
            verify { DocumentsContract.deleteDocument(any(), first) }
        }

    @Test
    fun `a successful export keeps its file`() =
        runBlocking {
            coEvery { repository.exportSubscriptionsAsNewPipe(first) } returns Result.success(NewPipeSubscriptionExport("{}", skipped = 0))
            val coordinator = coordinator()

            coordinator.exportNewPipeSubscriptions(first)

            val outcome = withTimeout(2_000) { coordinator.operation.first { it !is BackupOperation.Running } }
            assertThat(outcome).isInstanceOf(BackupOperation.Succeeded::class.java)
            verify(exactly = 0) { DocumentsContract.deleteDocument(any(), any()) }
        }

    @Test
    fun `notification preference waits do not block import work or completion`() =
        runBlocking {
            val notificationStarted = CompletableDeferred<Unit>()
            val notificationCancelled = CompletableDeferred<Unit>()
            val workStarted = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Result<Int>>()
            coEvery { NotificationHelper.showImportProgress(any(), any(), any(), any()) } coAnswers {
                notificationStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    notificationCancelled.complete(Unit)
                }
            }
            coEvery { repository.importNewPipe(first, any()) } coAnswers {
                workStarted.complete(Unit)
                finish.await()
            }
            val coordinator = coordinator()
            assertThat(coordinator.importNewPipe(first)).isTrue()
            withTimeout(2_000) {
                workStarted.await()
                notificationStarted.await()
            }
            assertThat(coordinator.importNewPipe(second)).isFalse()
            finish.complete(Result.success(1))
            val outcome = withTimeout(2_000) { coordinator.operation.first { it !is BackupOperation.Running } }
            assertThat(outcome).isInstanceOf(BackupOperation.Succeeded::class.java)
            assertThat(notificationCancelled.isCompleted).isTrue()
            coVerify(timeout = 2_000, exactly = 1) { NotificationHelper.showImportComplete(any(), any(), any(), any()) }
        }

    @Test
    fun `rapid import progress is conflated while a notification is suspended`() =
        runBlocking {
            val notificationStarted = CompletableDeferred<Unit>()
            val emitted = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Result<Int>>()
            val posts = AtomicInteger()
            coEvery { NotificationHelper.showImportProgress(any(), any(), any(), any()) } coAnswers {
                posts.incrementAndGet()
                notificationStarted.complete(Unit)
                awaitCancellation()
            }
            coEvery { repository.importNewPipe(first, any()) } coAnswers {
                notificationStarted.await()
                val progress = secondArg<((Int, Int) -> Unit)?>()!!
                repeat(1_000) { progress(it + 1, 1_000) }
                emitted.complete(Unit)
                finish.await()
            }
            val coordinator = coordinator()
            coordinator.importNewPipe(first)
            withTimeout(2_000) { emitted.await() }
            assertThat((coordinator.operation.value as BackupOperation.Running).current).isEqualTo(1_000)
            assertThat(posts.get()).isEqualTo(1)
            finish.complete(Result.success(1_000))
            withTimeout(2_000) { coordinator.operation.first { it !is BackupOperation.Running } }
            Unit
        }

    @Test
    fun `notification failures do not replace a successful import outcome`() =
        runBlocking {
            val attempted = CompletableDeferred<Unit>()
            coEvery { NotificationHelper.showImportProgress(any(), any(), any(), any()) } coAnswers {
                attempted.complete(Unit)
                throw SecurityException("permission revoked")
            }
            every { NotificationHelper.cancelImportNotification(any()) } throws SecurityException("permission revoked")
            coEvery { repository.importNewPipe(first, any()) } coAnswers {
                attempted.await()
                Result.success(1)
            }
            val coordinator = coordinator()
            coordinator.importNewPipe(first)
            val outcome = withTimeout(2_000) { coordinator.operation.first { it !is BackupOperation.Running } }
            assertThat(outcome).isInstanceOf(BackupOperation.Succeeded::class.java)
            coVerify(timeout = 2_000) { NotificationHelper.showImportComplete(any(), any(), any(), any()) }
        }
}
