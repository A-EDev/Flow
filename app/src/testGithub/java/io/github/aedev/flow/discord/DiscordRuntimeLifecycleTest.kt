package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscordRuntimeLifecycleTest {
    @Test
    fun `account link is preserved while login activity backgrounds Flow`() {
        val action = discordRuntimeAction(
            enabled = true,
            appForeground = false,
            accountLinkInProgress = true,
            backgroundPlaybackActive = false,
        )

        assertThat(action).isEqualTo(DiscordRuntimeAction.KEEP_CONNECTION)
    }

    @Test
    fun `disabled preference unlinks even while account link is pending`() {
        val action = discordRuntimeAction(
            enabled = false,
            appForeground = false,
            accountLinkInProgress = true,
            backgroundPlaybackActive = true,
        )

        assertThat(action).isEqualTo(DiscordRuntimeAction.UNLINK)
    }

    @Test
    fun `background lifecycle disconnects when no account link is pending`() {
        val action = discordRuntimeAction(
            enabled = true,
            appForeground = false,
            accountLinkInProgress = false,
            backgroundPlaybackActive = false,
        )

        assertThat(action).isEqualTo(DiscordRuntimeAction.DISCONNECT)
    }

    @Test
    fun `foreground lifecycle keeps connection while enabled`() {
        val action = discordRuntimeAction(
            enabled = true,
            appForeground = true,
            accountLinkInProgress = false,
            backgroundPlaybackActive = false,
        )

        assertThat(action).isEqualTo(DiscordRuntimeAction.KEEP_CONNECTION)
    }

    @Test
    fun `screen off keeps connection while background playback is active`() {
        val action = discordRuntimeAction(
            enabled = true,
            appForeground = false,
            accountLinkInProgress = false,
            backgroundPlaybackActive = true,
        )

        assertThat(action).isEqualTo(DiscordRuntimeAction.KEEP_CONNECTION)
    }

    @Test
    fun `transient inactive playback does not emit disconnect state`() = runTest {
        val playbackActive = MutableStateFlow(true)
        val states = mutableListOf<Boolean>()
        val job = launch {
            playbackActive
                .delayDiscordPlaybackInactive(disconnectDelayMs = 60_000L)
                .collect(states::add)
        }
        runCurrent()

        playbackActive.value = false
        advanceTimeBy(30_000L)
        playbackActive.value = true
        runCurrent()

        assertThat(states).containsExactly(true)
        job.cancelAndJoin()
    }

    @Test
    fun `sustained inactive background playback emits disconnect state after grace`() = runTest {
        val playbackActive = MutableStateFlow(true)
        val states = mutableListOf<Boolean>()
        val job = launch {
            playbackActive
                .delayDiscordPlaybackInactive(disconnectDelayMs = 60_000L)
                .collect(states::add)
        }
        runCurrent()

        playbackActive.value = false
        advanceTimeBy(60_000L)
        runCurrent()

        assertThat(states).containsExactly(true, false).inOrder()
        job.cancelAndJoin()
    }
}
