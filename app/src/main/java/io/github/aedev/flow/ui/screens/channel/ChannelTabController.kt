package io.github.aedev.flow.ui.screens.channel

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import io.github.aedev.flow.data.paging.ChannelTabPagingSource
import io.github.aedev.flow.innertube.pages.channel.ChannelItem
import io.github.aedev.flow.innertube.pages.channel.ChannelOwner
import io.github.aedev.flow.innertube.pages.channel.ChannelSortOption
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class ChannelTabState(
    val items: Flow<PagingData<ChannelItem>>? = null,
    val filters: List<String> = emptyList(),
    val selectedFilter: Int = 0,
)

/**
 * Holds one lazily built pager per channel tab.
 *
 * A tab is fetched when it is first shown, never on channel open. The screen used to prefetch the
 * Videos and Live tabs to their page cap the moment a channel opened — up to fifty requests each,
 * throttled to 800 ms apart, on both tabs at once, whether or not the channel had a Live tab and
 * whether or not either was ever looked at.
 */
internal class ChannelTabController(
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<ChannelTabKind, ChannelTabState>>(emptyMap())
    val states: StateFlow<Map<ChannelTabKind, ChannelTabState>> = _states.asStateFlow()

    private var browseId: String = ""
    private var owner: ChannelOwner = ChannelOwner()
    private val filterTokens = mutableMapOf<ChannelTabKind, List<String>>()

    fun reset(
        browseId: String,
        owner: ChannelOwner,
    ) {
        this.browseId = browseId
        this.owner = owner
        filterTokens.clear()
        _states.value = emptyMap()
    }

    fun ensureLoaded(
        kind: ChannelTabKind,
        params: String?,
    ) {
        if (browseId.isBlank() || params.isNullOrBlank()) return
        if (_states.value[kind]?.items != null) return
        build(kind, params, sortToken = null, selectedFilter = 0)
    }

    /** A chip token is the tab's first page in a different order, not a filter over the current one. */
    fun selectFilter(
        kind: ChannelTabKind,
        params: String?,
        index: Int,
    ) {
        val tokens = filterTokens[kind].orEmpty()
        if (params.isNullOrBlank() || index !in tokens.indices) return
        if (index == _states.value[kind]?.selectedFilter) return
        build(kind, params, sortToken = tokens.getOrNull(index).takeIf { index != 0 }, selectedFilter = index)
    }

    private fun build(
        kind: ChannelTabKind,
        params: String,
        sortToken: String?,
        selectedFilter: Int,
    ) {
        val pager =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                pagingSourceFactory = {
                    ChannelTabPagingSource(
                        browseId = browseId,
                        params = params,
                        kind = kind,
                        sortToken = sortToken,
                        owner = owner,
                        onPageLoaded = { page -> publishFilters(kind, page.filters) },
                    )
                },
            ).flow.cachedIn(scope)

        _states.update { states ->
            states + (kind to (states[kind] ?: ChannelTabState()).copy(items = pager, selectedFilter = selectedFilter))
        }
    }

    private fun publishFilters(
        kind: ChannelTabKind,
        filters: List<ChannelSortOption>,
    ) {
        if (filters.isEmpty()) return
        filterTokens[kind] = filters.map { it.token }
        val labels = filters.map { it.label }
        _states.update { states ->
            val current = states[kind] ?: ChannelTabState()
            if (current.filters == labels) states else states + (kind to current.copy(filters = labels))
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
