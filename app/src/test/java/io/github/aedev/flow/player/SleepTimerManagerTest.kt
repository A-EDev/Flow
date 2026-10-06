package io.github.aedev.flow.player

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class SleepTimerManagerTest {
    private val owners = mutableListOf<Any>()

    @Before
    fun setUp() {
        SleepTimerManager.cancel()
    }

    @After
    fun tearDown() {
        SleepTimerManager.cancel()
        owners.forEach { SleepTimerManager.detachPlayer(it) }
    }

    @Test
    fun `disposing older owner leaves the newer active player and callbacks attached`() {
        val olderOwner = owner()
        val activeOwner = owner()
        val olderPlayer = mockk<Player>(relaxed = true)
        val activePlayer = mockk<Player>(relaxed = true)
        val olderListener = slot<Player.Listener>()
        val activeListener = slot<Player.Listener>()
        var olderPauses = 0
        var activePauses = 0
        var olderExits = 0
        var activeExits = 0
        every { olderPlayer.addListener(capture(olderListener)) } just Runs
        every { activePlayer.addListener(capture(activeListener)) } just Runs

        SleepTimerManager.attachToPlayer(olderOwner, olderPlayer, { olderPauses++ }, { olderExits++ })
        SleepTimerManager.updateOwnerActive(olderOwner, true)
        SleepTimerManager.attachToPlayer(activeOwner, activePlayer, { activePauses++ }, { activeExits++ })
        SleepTimerManager.updateOwnerActive(activeOwner, true)
        SleepTimerManager.detachPlayer(olderOwner)
        SleepTimerManager.startEndOfMedia(closeApp = true)

        activeListener.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertThat(olderPauses).isEqualTo(0)
        assertThat(olderExits).isEqualTo(0)
        assertThat(activePauses).isEqualTo(0)
        assertThat(activeExits).isEqualTo(1)
        verify(exactly = 1) { olderPlayer.removeListener(any()) }
        verify(exactly = 0) { activePlayer.removeListener(any()) }
    }

    @Test
    fun `inactive warm owner cannot replace active playback owner`() {
        val warmOwner = owner()
        val activeOwner = owner()
        val warmPlayer = mockk<Player>(relaxed = true)
        val activePlayer = mockk<Player>(relaxed = true)
        val activeListener = slot<Player.Listener>()
        var warmPauses = 0
        var activePauses = 0
        every { activePlayer.addListener(capture(activeListener)) } just Runs

        SleepTimerManager.attachToPlayer(warmOwner, warmPlayer, { warmPauses++ }, {})
        SleepTimerManager.updateOwnerActive(warmOwner, false)
        SleepTimerManager.attachToPlayer(activeOwner, activePlayer, { activePauses++ }, {})
        SleepTimerManager.updateOwnerActive(activeOwner, true)
        SleepTimerManager.startEndOfMedia()

        activeListener.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertThat(warmPauses).isEqualTo(0)
        assertThat(activePauses).isEqualTo(1)
        verify(exactly = 0) { warmPlayer.addListener(any()) }
    }

    @Test
    fun `disposing active surface preserves timer target for background playback`() {
        val owner = owner()
        val player = mockk<Player>(relaxed = true)
        val listener = slot<Player.Listener>()
        var pauses = 0
        var exits = 0
        every { player.addListener(capture(listener)) } just Runs

        SleepTimerManager.attachToPlayer(owner, player, { pauses++ }, { exits++ })
        SleepTimerManager.updateOwnerActive(owner, true)
        SleepTimerManager.startEndOfMedia(closeApp = true)
        SleepTimerManager.detachPlayer(owner)

        listener.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertThat(pauses).isEqualTo(1)
        assertThat(exits).isEqualTo(0)
        assertThat(SleepTimerManager.isActive).isFalse()
        verify(exactly = 1) { player.removeListener(any()) }
    }

    @Test
    fun `reattaching same owner replaces detached timer target`() {
        val owner = owner()
        val oldPlayer = mockk<Player>(relaxed = true)
        val newPlayer = mockk<Player>(relaxed = true)
        val newListener = slot<Player.Listener>()
        var oldPauses = 0
        var newPauses = 0
        every { newPlayer.addListener(capture(newListener)) } just Runs

        SleepTimerManager.attachToPlayer(owner, oldPlayer, { oldPauses++ }, {})
        SleepTimerManager.updateOwnerActive(owner, true)
        SleepTimerManager.startEndOfMedia()
        SleepTimerManager.detachPlayer(owner)
        SleepTimerManager.attachToPlayer(owner, newPlayer, { newPauses++ }, {})

        newListener.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertThat(oldPauses).isEqualTo(0)
        assertThat(newPauses).isEqualTo(1)
    }

    private fun owner(): Any = Any().also(owners::add)
}
