package io.github.aedev.flow.ui.screens.music

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * One [MusicViewModel] per activity, shared by the Music tab and every music
 * route (artist, playlist, mix, view-all) — the same scoping the TV shell uses.
 *
 * This is load-bearing for battery and latency: the ViewModel's init kicks off
 * the entire home-feed load (dozens of network calls), so a per-route instance
 * used to re-run that flood on every artist/album/mix open, starving the fixed
 * network pool (30 s Daily Mix pages) and heating the device. One shared
 * instance loads home once and lets every route reuse its section state and
 * related-lane/artist caches.
 */
@Composable
fun sharedMusicViewModel(): MusicViewModel {
    val activity = LocalContext.current as? ComponentActivity
    return if (activity != null) hiltViewModel(activity) else hiltViewModel()
}
