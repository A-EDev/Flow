package io.github.aedev.flow.player.audio

import android.content.Context
import android.util.Log
import io.github.aedev.flow.data.local.AudioSettingsPersistence
import io.github.aedev.flow.data.model.EqPresets
import io.github.aedev.flow.data.model.FilterType
import io.github.aedev.flow.data.model.ParametricEQ
import io.github.aedev.flow.data.model.ParametricEQBand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for equalizer state shared by the music and video pipelines.
 *
 * Both players apply the same persisted EQ: the music path forwards [resolvedEq] to
 * [CustomEqualizerAudioProcessor] inside Media3MusicService via a session command, while the
 * video path applies it to the processor installed in its own audio sink. UI edits call the
 * setters here, so tuning persists across tracks, player restarts, and both playback surfaces.
 */
object AudioEffectsController {

    /** Reserved profile name for the user's live-edited (unsaved) EQ curve. */
    const val CUSTOM_PROFILE = "Custom"

    private const val TAG = "AudioEffectsController"
    private const val CUSTOM_EQ_SAVE_DEBOUNCE_MS = 400L

    private val json = Json { ignoreUnknownKeys = true }
    private val presetsMapSerializer = MapSerializer(String.serializer(), ParametricEQ.serializer())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var persistence: AudioSettingsPersistence? = null
    private var isInitialized = false
    private var customEqSaveJob: Job? = null

    private val _eqProfileName = MutableStateFlow("Flat")
    val eqProfileName: StateFlow<String> = _eqProfileName.asStateFlow()

    private val _bassBoost = MutableStateFlow(0f)
    val bassBoost: StateFlow<Float> = _bassBoost.asStateFlow()

    private val _customEq = MutableStateFlow(ParametricEQ.createFlat())
    val customEq: StateFlow<ParametricEQ> = _customEq.asStateFlow()

    private val _customPresets = MutableStateFlow<Map<String, ParametricEQ>>(emptyMap())
    val customPresets: StateFlow<Map<String, ParametricEQ>> = _customPresets.asStateFlow()

    /** The profile the audio pipelines should actually run: selected preset + bass boost folded in. */
    val resolvedEq: StateFlow<ParametricEQ> = combine(
        _eqProfileName, _bassBoost, _customEq, _customPresets
    ) { name, boost, custom, presets ->
        foldBassBoost(resolveProfile(name, custom, presets), boost)
    }.stateIn(scope, SharingStarted.Eagerly, ParametricEQ.createFlat())

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val store = AudioSettingsPersistence.getInstance(context.applicationContext)
        persistence = store
        scope.launch {
            try {
                store.customPresetsJsonFlow.first()?.let { raw ->
                    _customPresets.value = json.decodeFromString(presetsMapSerializer, raw)
                }
                store.customEqJsonFlow.first()?.let { raw ->
                    _customEq.value = json.decodeFromString(ParametricEQ.serializer(), raw)
                }
                val settings = store.settingsFlow.first()
                _bassBoost.value = settings.bassBoost
                _eqProfileName.value = settings.eqProfile
                Log.d(TAG, "Restored EQ state: profile=${settings.eqProfile}, bass=${settings.bassBoost}, customPresets=${_customPresets.value.keys}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore EQ state", e)
            }
        }
    }

    /** Built-in preset names plus saved custom presets, in display order. */
    fun availablePresetNames(): List<String> =
        EqPresets.presets.keys.sorted() + _customPresets.value.keys.sorted()

    fun isCustomPreset(name: String): Boolean = _customPresets.value.containsKey(name)

    /** The bands/preamp the editor should show for a profile name (without bass boost folded in). */
    fun editableProfile(name: String): ParametricEQ = resolveProfile(name, _customEq.value, _customPresets.value)

    fun setEqProfile(name: String) {
        _eqProfileName.value = name
        scope.launch { persistence?.saveEqProfile(name) }
    }

    fun setBassBoost(strength: Float) {
        _bassBoost.value = strength
        scope.launch { persistence?.saveBassBoost(strength) }
    }

    /**
     * Apply a live edit from the parametric editor. Switches the active profile to
     * [CUSTOM_PROFILE] and persists the curve (debounced — slider drags emit rapidly).
     */
    fun setCustomEq(profile: ParametricEQ) {
        _customEq.value = profile
        if (_eqProfileName.value != CUSTOM_PROFILE) {
            _eqProfileName.value = CUSTOM_PROFILE
            scope.launch { persistence?.saveEqProfile(CUSTOM_PROFILE) }
        }
        customEqSaveJob?.cancel()
        customEqSaveJob = scope.launch {
            delay(CUSTOM_EQ_SAVE_DEBOUNCE_MS)
            persistence?.saveCustomEqJson(json.encodeToString(ParametricEQ.serializer(), profile))
        }
    }

    /** Save the currently shown curve under [name] and select it. Returns false for invalid names. */
    fun saveCustomPreset(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == CUSTOM_PROFILE || EqPresets.presets.containsKey(trimmed)) return false
        val profile = editableProfile(_eqProfileName.value)
        val updated = _customPresets.value + (trimmed to profile)
        _customPresets.value = updated
        _eqProfileName.value = trimmed
        scope.launch {
            persistence?.saveCustomPresetsJson(json.encodeToString(presetsMapSerializer, updated))
            persistence?.saveEqProfile(trimmed)
        }
        return true
    }

    fun deleteCustomPreset(name: String) {
        val updated = _customPresets.value - name
        _customPresets.value = updated
        scope.launch { persistence?.saveCustomPresetsJson(json.encodeToString(presetsMapSerializer, updated)) }
        if (_eqProfileName.value == name) setEqProfile("Flat")
    }

    private fun resolveProfile(
        name: String,
        custom: ParametricEQ,
        presets: Map<String, ParametricEQ>
    ): ParametricEQ = when {
        name == CUSTOM_PROFILE -> custom
        else -> EqPresets.presets[name] ?: presets[name] ?: ParametricEQ.createFlat()
    }

    /** Fold the bass-boost slider into the profile as a low-shelf band (merging with an existing one). */
    private fun foldBassBoost(base: ParametricEQ, boost: Float): ParametricEQ {
        if (boost <= 0f) return base
        val existingIndex = base.bands.indexOfFirst {
            it.filterType == FilterType.LSC && it.frequency in 40.0..80.0
        }
        val newBands = if (existingIndex >= 0) {
            base.bands.toMutableList().also { bands ->
                val existing = bands[existingIndex]
                bands[existingIndex] = existing.copy(gain = existing.gain + boost.toDouble())
            }
        } else {
            listOf(ParametricEQBand(60.0, boost.toDouble(), 0.7, FilterType.LSC)) + base.bands
        }
        return base.copy(bands = newBands)
    }
}
