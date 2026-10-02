package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.data.recommendation.music.FavouriteArtist
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistCatalog
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistsStore
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a scrobbling account's most played artists into favourite artists, so the music
 * recommendations start from years of real listening instead of from nothing. Only artists whose
 * YouTube Music page has the same name are added; a near match is skipped rather than guessed.
 */
@Singleton
class TasteImport
    @Inject
    constructor(
        private val scrobbler: Scrobbler,
        private val catalog: FavouriteArtistCatalog,
        private val favourites: FavouriteArtistsStore,
    ) {
        /** How many artists were added; the ones already picked are not counted again. */
        suspend fun importTopArtists(service: ScrobbleService): Result<Int> =
            withContext(PerformanceDispatcher.networkIO) {
                runCatching {
                    val names = scrobbler.topArtists(service, TOP_ARTISTS).getOrThrow()
                    val picked = favourites.currentIds()
                    val found = resolve(names).filterNot { it.id in picked }
                    found.forEach { favourites.setFavourite(it, favourite = true) }
                    found.size
                }
            }

        /** One bounded round of searches per chunk, never a search per artist all at once. */
        private suspend fun resolve(names: List<String>): List<FavouriteArtist> =
            names
                .chunked(PARALLEL_SEARCHES)
                .flatMap { chunk ->
                    coroutineScope {
                        chunk.map { name -> async { runCatching { matchingArtist(name) }.getOrNull() } }.awaitAll()
                    }
                }.filterNotNull()
                .distinctBy { it.id }

        private suspend fun matchingArtist(name: String): FavouriteArtist? {
            val wanted = name.matchKey()
            return catalog.search(name).firstOrNull { it.name.matchKey() == wanted }
        }

        private companion object {
            const val TOP_ARTISTS = 20
            const val PARALLEL_SEARCHES = 4
        }
    }

/** Case, accents and spacing ignored, so "Beyoncé" matches "BEYONCE". */
internal fun String.matchKey(): String =
    Normalizer
        .normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")
