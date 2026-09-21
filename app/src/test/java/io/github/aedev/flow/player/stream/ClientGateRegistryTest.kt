package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClientGateRegistryTest {
    private var nowMs = 0L
    private val registry = ClientGateRegistry(ttlMs = 1_000L, clockMs = { nowMs })

    @Test
    fun `a reported client is demoted until its entry lapses`() {
        registry.reportGated("VISIONOS")

        assertThat(registry.isGated("VISIONOS")).isTrue()
        nowMs += 999L
        assertThat(registry.isGated("VISIONOS")).isTrue()
        nowMs += 1L
        assertThat(registry.isGated("VISIONOS")).isFalse()
    }

    @Test
    fun `the client name is matched regardless of case because it comes off a url`() {
        registry.reportGated("visionos")

        assertThat(registry.isGated("VISIONOS")).isTrue()
    }

    @Test
    fun `an unreported client is never demoted`() {
        registry.reportGated("VISIONOS")

        assertThat(registry.isGated("MWEB")).isFalse()
        assertThat(registry.isGated(null)).isFalse()
        assertThat(registry.isGated("")).isFalse()
    }

    @Test
    fun `reporting again extends the demotion`() {
        registry.reportGated("ANDROID_VR")
        nowMs += 900L
        registry.reportGated("ANDROID_VR")
        nowMs += 900L

        assertThat(registry.isGated("ANDROID_VR")).isTrue()
    }

    @Test
    fun `lapsed entries drop out of the listing`() {
        registry.reportGated("VISIONOS")
        registry.reportGated("MWEB")
        assertThat(registry.gatedClients()).containsExactly("VISIONOS", "MWEB")

        nowMs += 1_000L
        assertThat(registry.gatedClients()).isEmpty()
    }

    @Test
    fun `clearing restores every client at once`() {
        registry.reportGated("VISIONOS")
        registry.clear()

        assertThat(registry.isGated("VISIONOS")).isFalse()
    }
}
