package io.github.aedev.flow.utils.potoken

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PoTokenAttestationPolicyTest {
    private fun token(decodedBytes: Int): String {
        val unpadded = "A".repeat(decodedBytes * 4 / 3)
        return unpadded + "=".repeat((4 - unpadded.length % 4) % 4)
    }

    @Test
    fun `a warm full-trust session is reused as is`() {
        val reattest =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = true,
                isExpired = false,
                sessionIdChanged = false,
                lastTokenWasLowTrust = false,
            )

        assertThat(reattest).isFalse()
    }

    @Test
    fun `no session yet forces an attestation`() {
        val reattest =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = false,
                isExpired = false,
                sessionIdChanged = false,
                lastTokenWasLowTrust = false,
            )

        assertThat(reattest).isTrue()
    }

    @Test
    fun `a low-trust token is never pinned`() {
        val reattest =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = true,
                isExpired = false,
                sessionIdChanged = false,
                lastTokenWasLowTrust = true,
            )

        assertThat(reattest).isTrue()
    }

    @Test
    fun `expiry and a session change each force an attestation`() {
        val expired =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = true,
                isExpired = true,
                sessionIdChanged = false,
                lastTokenWasLowTrust = false,
            )
        val newSession =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = true,
                isExpired = false,
                sessionIdChanged = true,
                lastTokenWasLowTrust = false,
            )

        assertThat(expired).isTrue()
        assertThat(newSession).isTrue()
    }

    @Test
    fun `a caller forcing recreation always wins`() {
        val reattest =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = true,
                hasSession = true,
                isExpired = false,
                sessionIdChanged = false,
                lastTokenWasLowTrust = false,
            )

        assertThat(reattest).isTrue()
    }

    @Test
    fun `token length ignores base64 padding`() {
        assertThat(PoTokenAttestationPolicy.tokenByteLength("QUJD")).isEqualTo(3)
        assertThat(PoTokenAttestationPolicy.tokenByteLength("QUJDRA==")).isEqualTo(4)
    }

    @Test
    fun `a cold 88-byte attestation is treated as low trust`() {
        assertThat(PoTokenAttestationPolicy.isLowTrust(token(88))).isTrue()
    }

    @Test
    fun `a full-trust 110-byte token is accepted`() {
        assertThat(PoTokenAttestationPolicy.isLowTrust(token(110))).isFalse()
    }
}
