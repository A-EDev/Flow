package io.github.aedev.flow.data.audio.eq

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EqLegacyMigrationTest {
    private var nextId = 0

    private fun migrate(
        profile: String?,
        bass: Float? = null,
        custom: String? = null,
        presets: String? = null,
    ) = EqLegacyMigration.migrate(profile, bass, custom, presets, "My curve") { "user:${nextId++}" }

    @Test
    fun `a fresh install starts flat with auto preamp on`() {
        val state = migrate(profile = null)
        assertThat(state.active.presetId).isEqualTo(BuiltInEqPresets.FLAT_ID)
        assertThat(state.autoPreamp).isTrue()
        assertThat(state.userPresets).isEmpty()
    }

    @Test
    fun `a built-in selection maps to its id and bass boost carries over`() {
        val state = migrate(profile = "Rock", bass = 6f)
        assertThat(state.active.presetId).isEqualTo("builtin:rock")
        assertThat(state.bassBoost).isEqualTo(6.0)
    }

    @Test
    fun `the old Custom curve becomes a saved preset called My curve`() {
        val custom = """{"preamp":-1.0,"bands":[{"frequency":1000.0,"gain":3.0,"q":1.41,"filterType":"PK","enabled":true}]}"""
        val state = migrate(profile = "Custom", custom = custom)

        val preset = state.userPreset(state.active.presetId)!!
        assertThat(preset.name).isEqualTo("My curve")
        assertThat(preset.curve.bands).containsExactly(EqBand(1_000.0, 3.0, 1.41))
        assertThat(state.isEdited).isFalse()
    }

    @Test
    fun `bands keep the sound the old processor made`() {
        val custom =
            """
            {"preamp":0.0,"bands":[
              {"frequency":80.0,"gain":4.0,"q":3.0,"filterType":"LSC","enabled":true},
              {"frequency":9000.0,"gain":2.0,"q":0.3,"filterType":"HSC","enabled":false},
              {"frequency":500.0,"gain":2.0,"q":2.0,"filterType":"LPQ","enabled":true}
            ],"metadata":{}}
            """.trimIndent()
        val bands = migrate(profile = "Custom", custom = custom).active.curve.bands

        assertThat(bands[0]).isEqualTo(EqBand(80.0, 4.0, EqLimits.DEFAULT_SHELF_Q, EqFilterType.LOW_SHELF))
        assertThat(bands[1]).isEqualTo(EqBand(9_000.0, 2.0, EqLimits.DEFAULT_SHELF_Q, EqFilterType.HIGH_SHELF, enabled = false))
        assertThat(bands[2]).isEqualTo(EqBand(500.0, 2.0, 2.0, EqFilterType.PEAK))
    }

    @Test
    fun `named presets migrate and the selected one stays selected`() {
        val presets = """{"Car":{"preamp":0.0,"bands":[{"frequency":60.0,"gain":5.0}]},"Flat":{"preamp":0.0,"bands":[]}}"""
        val state = migrate(profile = "Car", presets = presets)

        assertThat(state.userPresets.map { it.name }).containsExactly("Car")
        assertThat(state.userPreset(state.active.presetId)!!.name).isEqualTo("Car")
    }

    @Test
    fun `unreadable old data falls back to flat`() {
        val state = migrate(profile = "Custom", custom = "{broken", presets = "[]")
        assertThat(state.active.presetId).isEqualTo(BuiltInEqPresets.FLAT_ID)
        assertThat(state.userPresets).isEmpty()
    }
}
