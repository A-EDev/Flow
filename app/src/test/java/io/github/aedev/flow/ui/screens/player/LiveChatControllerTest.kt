package io.github.aedev.flow.ui.screens.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.LiveChatMessage
import io.github.aedev.flow.data.repository.LiveChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Pins the visibility gate on [LiveChatController]: the availability probe always runs (the chat
 * affordance is hidden until it answers), the poll loop only runs while a panel is showing it, and
 * closing that panel keeps the transcript so reopening costs no wait.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveChatControllerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: LiveChatRepository = mockk(relaxed = true)
    private val controllerScope = CoroutineScope(testDispatcher)
    private var pollCount = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.initialContinuation(any()) } returns SEED
        coEvery { repository.poll(any()) } answers { page(listOf(message("m1"), message("m2"))) }
    }

    @After
    fun tearDown() {
        controllerScope.cancel()
        Dispatchers.resetMain()
        unmockkAll()
    }

    /**
     * The drip loop never ends on its own, so every test hands its scope back before [runTest]
     * drains the scheduler — an endless virtual-time loop would otherwise hang the runner.
     */
    private fun liveChatTest(body: suspend TestScope.(LiveChatController) -> Unit) =
        runTest(testDispatcher) {
            val controller =
                LiveChatController(
                    repository = repository,
                    scope = controllerScope,
                    dispatcher = testDispatcher,
                )
            try {
                body(controller)
            } finally {
                controllerScope.cancel()
            }
        }

    @Test
    fun `a hidden panel probes availability but never polls`() =
        liveChatTest { controller ->

            controller.start("v1")
            advanceTimeBy(POLL_WINDOW_MS)
            runCurrent()

            coVerify(exactly = 1) { repository.initialContinuation("v1") }
            coVerify(exactly = 0) { repository.poll(any()) }
            assertThat(controller.isAvailable.value).isTrue()
            assertThat(controller.isLoading.value).isFalse()
            assertThat(controller.messages.value).isEmpty()
        }

    @Test
    fun `an unavailable chat resolves without polling`() =
        liveChatTest { controller ->
            coEvery { repository.initialContinuation("v1") } returns null

            controller.setPanelVisible(true)
            controller.start("v1")
            advanceTimeBy(POLL_WINDOW_MS)
            runCurrent()

            assertThat(controller.isAvailable.value).isFalse()
            assertThat(controller.isLoading.value).isFalse()
            coVerify(exactly = 0) { repository.poll(any()) }
        }

    @Test
    fun `showing the panel starts the drip`() =
        liveChatTest { controller ->

            controller.start("v1")
            advanceTimeBy(POLL_WINDOW_MS)
            runCurrent()
            controller.setPanelVisible(true)
            advanceTimeBy(POLL_WINDOW_MS)
            runCurrent()

            assertThat(controller.messages.value.map { it.id }).containsExactly("m1", "m2").inOrder()
        }

    @Test
    fun `hiding the panel stops the polling and keeps the transcript`() =
        liveChatTest { controller ->

            controller.setPanelVisible(true)
            controller.start("v1")
            advanceTimeBy(POLL_WINDOW_MS)
            runCurrent()
            val bufferedMessages = controller.messages.value
            assertThat(bufferedMessages).isNotEmpty()

            controller.setPanelVisible(false)
            val pollsBeforeHiding = pollCount
            advanceTimeBy(POLL_WINDOW_MS * 4)
            runCurrent()

            assertThat(pollCount).isEqualTo(pollsBeforeHiding)
            assertThat(controller.messages.value).isEqualTo(bufferedMessages)
        }

    @Test
    fun `reopening the panel resumes polling on the messages already buffered`() =
        liveChatTest { controller ->

            controller.setPanelVisible(true)
            controller.start("v1")
            advanceTimeBy(POLL_WINDOW_MS)
            runCurrent()
            controller.setPanelVisible(false)
            runCurrent()

            val buffered = controller.messages.value
            val pollsWhileHidden = pollCount

            coEvery { repository.poll(any()) } answers { page(listOf(message("m3"))) }
            controller.setPanelVisible(true)
            advanceTimeBy(POLL_WINDOW_MS)
            runCurrent()

            assertThat(pollCount).isGreaterThan(pollsWhileHidden)
            assertThat(controller.messages.value).containsAtLeastElementsIn(buffered).inOrder()
            assertThat(controller.messages.value.map { it.id }).contains("m3")
        }

    @Test
    fun `a new video clears the transcript`() =
        liveChatTest { controller ->

            controller.setPanelVisible(true)
            controller.start("v1")
            advanceTimeBy(POLL_WINDOW_MS)
            runCurrent()
            assertThat(controller.messages.value).isNotEmpty()

            controller.start("v2")

            assertThat(controller.messages.value).isEmpty()
            assertThat(controller.isLoading.value).isTrue()
            assertThat(controller.isAvailable.value).isFalse()
        }

    private fun page(messages: List<LiveChatMessage>): LiveChatRepository.LiveChatPage {
        pollCount++
        return LiveChatRepository.LiveChatPage(
            messages = messages,
            nextContinuation = SEED,
            timeoutMs = PAGE_TIMEOUT_MS,
        )
    }

    private companion object {
        const val SEED = "seed"
        const val PAGE_TIMEOUT_MS = 1_000L
        const val POLL_WINDOW_MS = 2_000L

        fun message(id: String) =
            LiveChatMessage(
                id = id,
                author = "author",
                authorPhotoUrl = null,
                message = "hello $id",
                timestamp = null,
            )
    }
}
