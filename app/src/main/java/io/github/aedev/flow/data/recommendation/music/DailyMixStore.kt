package io.github.aedev.flow.data.recommendation.music

import io.github.aedev.flow.data.recommendation.MusicSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Daily Mixes the music home built last, so a mix page opens with the songs the home showed
 * without reading the home's state (which would keep the home loading behind the page).
 */
@Singleton
class DailyMixStore
    @Inject
    constructor() {
        private val _mixes = MutableStateFlow<List<MusicSection>>(emptyList())
        val mixes: StateFlow<List<MusicSection>> = _mixes.asStateFlow()

        fun publish(mixes: List<MusicSection>) {
            _mixes.value = mixes
        }
    }
