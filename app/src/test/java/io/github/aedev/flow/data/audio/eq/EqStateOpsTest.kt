package io.github.aedev.flow.data.audio.eq

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EqStateOpsTest {
    private val rock = "builtin:rock"
    private val mine = EqPreset("user:a", "Mine", EqCurve(bands = listOf(EqBand(500.0, 3.0))))

    @Test
    fun `selecting a preset loads its curve and is not an edit`() {
        val state = EqState().selectPreset(rock)
        assertThat(state.active.presetId).isEqualTo(rock)
        assertThat(state.active.curve).isEqualTo(BuiltInEqPresets.byId(rock)!!.curve)
        assertThat(state.isEdited).isFalse()
    }

    @Test
    fun `an edited curve stays reachable and reverts to its preset`() {
        val edited = EqState().selectPreset(rock).withBand(0, EqBand(60.0, 1.0, 0.707, EqFilterType.LOW_SHELF))
        assertThat(edited.isEdited).isTrue()
        assertThat(edited.active.presetId).isEqualTo(rock)
        assertThat(edited.revert().isEdited).isFalse()
    }

    @Test
    fun `a built-in cannot be overwritten, a user preset can`() {
        val builtIn = EqState().selectPreset(rock).addBand(EqBand(8_000.0, 2.0))
        assertThat(builtIn.canSaveActive).isFalse()
        assertThat(builtIn.saveActive()).isEqualTo(builtIn)

        val own = EqState(userPresets = listOf(mine)).selectPreset(mine.id).addBand(EqBand(8_000.0, 2.0))
        val saved = own.saveActive()
        assertThat(saved.userPreset(mine.id)!!.curve.bands).hasSize(2)
        assertThat(saved.isEdited).isFalse()
    }

    @Test
    fun `save as creates a preset and selects it`() {
        val state = EqState().selectPreset(rock).addBand(EqBand(8_000.0, 2.0)).saveActiveAs("user:b", "Car")
        assertThat(state.active.presetId).isEqualTo("user:b")
        assertThat(state.userPreset("user:b")!!.name).isEqualTo("Car")
        assertThat(state.isEdited).isFalse()
    }

    @Test
    fun `deleting a selected preset keeps the sound and undo restores it in place`() {
        val other = mine.copy(id = "user:z", name = "Other")
        val state = EqState(userPresets = listOf(other, mine)).selectPreset(mine.id)
        val (deleted, removed) = state.deletePreset(mine.id)

        assertThat(deleted.active.presetId).isNull()
        assertThat(deleted.active.curve).isEqualTo(mine.curve)

        val restored = deleted.restorePreset(removed!!)
        assertThat(restored.userPresets.map { it.id }).containsExactly("user:z", mine.id).inOrder()
        assertThat(restored.active.presetId).isEqualTo(mine.id)
    }

    @Test
    fun `each mode keeps its own curve`() {
        val parametric = EqState().selectPreset(rock)
        val graphic = parametric.copy(mode = EqMode.GRAPHIC).withActiveCurve(GraphicEq.curveOf(List(10) { 3.0 }))
        val back = graphic.copy(mode = EqMode.PARAMETRIC)
        assertThat(back.active.curve).isEqualTo(parametric.active.curve)
        assertThat(
            back.graphic.curve.bands
                .map { it.gain },
        ).containsExactlyElementsIn(List(10) { 3.0 })
    }

    @Test
    fun `importing adds a preset and plays it`() {
        val curve = EqCurve(-2.0, listOf(EqBand(910.0, 9.0, 0.71)))
        val state = EqState().importPreset("user:i", "Hefty Metal", curve)
        assertThat(state.active.presetId).isEqualTo("user:i")
        assertThat(state.userPreset("user:i")!!.imported).isTrue()
        assertThat(state.mode).isEqualTo(EqMode.PARAMETRIC)
    }

    @Test
    fun `the processing spec adds bass boost and protects the peak`() {
        val state = EqState().selectPreset(rock).withBassBoost(6.0)
        val spec = state.processingSpec(bypass = false)
        assertThat(spec.enabled).isTrue()
        assertThat(spec.bands.last().type).isEqualTo(EqFilterType.LOW_SHELF)
        assertThat(spec.bands.last().gain).isEqualTo(6.0)
        assertThat(spec.preampDb).isWithin(0.01).of(EqFilterMath.autoPreampDb(spec.bands))
        assertThat(spec.preampDb).isLessThan(0.0)
    }

    @Test
    fun `manual preamp is used when auto preamp is off`() {
        val state = EqState(autoPreamp = false).selectPreset(rock).withManualPreamp(-3.0)
        assertThat(state.processingSpec(bypass = false).preampDb).isEqualTo(-3.0)
    }

    @Test
    fun `off and compare both produce an unprocessed spec`() {
        val state = EqState().selectPreset(rock)
        assertThat(state.copy(enabled = false).processingSpec(bypass = false)).isEqualTo(EqProcessingSpec.OFF)
        assertThat(state.processingSpec(bypass = true)).isEqualTo(EqProcessingSpec.OFF)
    }

    @Test
    fun `a preview replaces the playing curve without touching the state`() {
        val preview = EqCurve(bands = listOf(EqBand(2_000.0, 5.0)))
        val spec = EqState().selectPreset(rock).processingSpec(bypass = false, preview = preview)
        assertThat(spec.bands).containsExactly(EqBand(2_000.0, 5.0))
    }

    @Test
    fun `flat and off settings change nothing, so offload can stay on`() {
        assertThat(EqState().changesSound).isFalse()
        assertThat(EqState().selectPreset(rock).changesSound).isTrue()
        assertThat(EqState().selectPreset(rock).copy(enabled = false).changesSound).isFalse()
        assertThat(EqState().withBassBoost(3.0).changesSound).isTrue()
        assertThat(EqState().addBand(EqBand(1_000.0, 0.0)).changesSound).isFalse()
    }

    @Test
    fun `names are unique regardless of case, including built-in names`() {
        val state = EqState(userPresets = listOf(mine))
        assertThat(state.isNameAvailable("mine", listOf("Rock"))).isFalse()
        assertThat(state.isNameAvailable("ROCK", listOf("Rock"))).isFalse()
        assertThat(state.isNameAvailable("Mine", listOf("Rock"), exceptId = mine.id)).isTrue()
        assertThat(state.isNameAvailable("  ", listOf("Rock"))).isFalse()
    }

    @Test
    fun `band edits respect the band limit and graphic mode`() {
        val full = (1..EqLimits.MAX_BANDS).fold(EqState()) { state, i -> state.addBand(EqBand(i * 100.0, 1.0)) }
        assertThat(full.addBand(EqBand(5_000.0, 1.0))).isEqualTo(full)
        val graphic = EqState(mode = EqMode.GRAPHIC)
        assertThat(graphic.addBand(EqBand(5_000.0, 1.0))).isEqualTo(graphic)
    }

    @Test
    fun `sanitising repairs a damaged state`() {
        val damaged =
            EqState(
                graphic = EqWorkingCopy(null, EqCurve(bands = listOf(EqBand(1.0, 99.0)))),
                bassBoost = Double.NaN,
                userPresets = listOf(mine, mine, EqPreset("", "x", EqCurve())),
            )
        val clean = damaged.sanitized()
        assertThat(clean.graphic.curve).isEqualTo(GraphicEq.flatCurve())
        assertThat(clean.bassBoost).isEqualTo(0.0)
        assertThat(clean.userPresets).containsExactly(mine)
    }

    @Test
    fun `state survives its JSON form`() {
        val state = EqState(userPresets = listOf(mine)).selectPreset(mine.id).withBassBoost(4.0)
        assertThat(EqStateJson.decode(EqStateJson.encode(state))).isEqualTo(state)
        assertThat(EqStateJson.decode("not json")).isNull()
    }
}
