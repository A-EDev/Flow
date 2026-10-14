package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeArrowCaseMarkerTest {
    @Test
    fun `strips case markers at word starts`() {
        assertThat(stripCaseMarkers("National >Flying >Laboratory >Centre | Tom >Scott: >England >E24"))
            .isEqualTo("National Flying Laboratory Centre | Tom Scott: England E24")
        assertThat(stripCaseMarkers(">US Supreme Court")).isEqualTo("US Supreme Court")
    }

    @Test
    fun `keeps literal greater-than signs`() {
        assertThat(stripCaseMarkers("5 > 3")).isEqualTo("5 > 3")
        assertThat(stripCaseMarkers("a->b >")).isEqualTo("a->b >")
        assertThat(stripCaseMarkers(">>Mixed")).isEqualTo(">Mixed")
    }
}
