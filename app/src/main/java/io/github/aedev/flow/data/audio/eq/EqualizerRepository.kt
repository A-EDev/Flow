package io.github.aedev.flow.data.audio.eq

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one equalizer state for music, video and Shorts. The UI changes it through [update]; every
 * player collects [processingSpec] in process and hands it to its own processor.
 */
@Singleton
class EqualizerRepository internal constructor(
    private val persistence: EqStatePersistence,
    private val scope: CoroutineScope,
    computeDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(persistence: EqStatePersistence) : this(
        persistence,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        Dispatchers.Default,
    )

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val preview = MutableStateFlow<EqCurve?>(null)
    private val bypass = MutableStateFlow(false)
    private var saveJob: Job? = null
    private val earlyEdits = mutableListOf<(EqState) -> EqState>()

    val processingSpec: StateFlow<EqProcessingSpec> =
        combine(_state, preview, bypass) { state, previewCurve, bypassed ->
            state.processingSpec(bypass = bypassed, preview = previewCurve)
        }.conflate()
            .distinctUntilChanged()
            .flowOn(computeDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, EqProcessingSpec.OFF)

    /** Whether the saved settings change the sound at all; bypass and drag previews are ignored. */
    val needsProcessing: StateFlow<Boolean> =
        _state
            .map { it.changesSound }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            val restored = persistence.load()
            if (_loaded.value) return@launch
            val early = synchronized(earlyEdits) { earlyEdits.toList().also { earlyEdits.clear() } }
            _state.value = early.fold(restored) { state, edit -> edit(state) }
            _loaded.value = true
            if (early.isNotEmpty()) scheduleSave()
        }
    }

    /**
     * Applies [transform] to the state. An edit made before the saved state has loaded is replayed on
     * top of it, so a switch flipped in the first moments after start is not lost.
     */
    fun update(transform: (EqState) -> EqState) {
        if (!_loaded.value) {
            synchronized(earlyEdits) { earlyEdits += transform }
            _state.value = transform(_state.value)
            return
        }
        val before = _state.value
        val after = transform(before)
        if (after == before) return
        _state.value = after
        preview.value = null
        scheduleSave()
    }

    /** Replaces the whole state, for Undo. */
    fun restore(state: EqState) = update { state }

    /** Plays [curve] in place of the active one until [update] or a null preview. */
    fun preview(curve: EqCurve?) {
        preview.value = curve?.sanitized()
    }

    /** Held Compare: the players hear the unprocessed sound. Never saved. */
    fun setBypass(bypassed: Boolean) {
        bypass.value = bypassed
    }

    /** Waits for the saved state, so an early backup or sync never exports the defaults. */
    suspend fun exportJson(): String {
        _loaded.first { it }
        return EqStateJson.encode(_state.value)
    }

    /** Applies a backup or a synced value; ignored when it cannot be read. */
    suspend fun importJson(raw: String): Boolean {
        val decoded = EqStateJson.decode(raw) ?: return false
        _loaded.value = true
        _state.value = decoded
        saveJob?.cancel()
        persistence.save(decoded)
        return true
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob =
            scope.launch {
                delay(SAVE_DEBOUNCE_MS)
                persistence.save(_state.value)
            }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 400L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface EqualizerModule {
    @Binds
    fun bindPersistence(store: EqStateStore): EqStatePersistence
}
