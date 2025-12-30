package com.flow.youtube.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.flow.youtube.data.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem

/**
 * PagingSource for loading channel playlists with infinite scroll support.
 */
class ChannelPlaylistsPagingSource(
    private val channelInfo: ChannelInfo,
    private val playlistsTab: ListLinkHandler?
) : PagingSource<Page, Playlist>() {
    
    companion object {
        private const val TAG = "ChannelPlaylistsPaging"
    }
    
    override fun getRefreshKey(state: PagingState<Page, Playlist>): Page? {
        return null
    }
    
    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, Playlist> {
        return try {
            withContext(Dispatchers.IO) {
                val page = params.key
                
                Log.d(TAG, "Loading page: ${page?.url ?: "initial"}")
                
                val playlists = mutableListOf<Playlist>()
                var nextPage: Page? = null
                
                if (playlistsTab == null) {
                    Log.w(TAG, "No playlists tab found")
                    return@withContext LoadResult.Page(
                        data = emptyList(),
                        prevKey = null,
                        nextKey = null
                    )
                }
                
                if (page == null) {
                    // Initial load
                    val tabInfo = ChannelTabInfo.getInfo(NewPipe.getService(0), playlistsTab)
                    nextPage = tabInfo.nextPage
                    
                    tabInfo.relatedItems.filterIsInstance<PlaylistInfoItem>().forEach { item ->
                        playlists.add(item.toPlaylist())
                    }
                } else {
                    // Load more
                    val moreItems = ChannelTabInfo.getMoreItems(NewPipe.getService(0), playlistsTab, page)
                    nextPage = moreItems.nextPage
                    
                    moreItems.items.filterIsInstance<PlaylistInfoItem>().forEach { item ->
                        playlists.add(item.toPlaylist())
                    }
                }
                
                LoadResult.Page(
                    data = playlists,
                    prevKey = null,
                    nextKey = nextPage
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading channel playlists", e)
            LoadResult.Error(e)
        }
    }
    
    private fun PlaylistInfoItem.toPlaylist(): Playlist {
        val playlistId = when {
            this.url.contains("list=") -> this.url.substringAfter("list=").substringBefore("&")
            else -> this.url.substringAfterLast("/").substringBefore("?")
        }
        
        return Playlist(
            id = playlistId,
            name = this.name,
            thumbnailUrl = this.thumbnails.firstOrNull()?.url ?: "",
            videoCount = this.streamCount.toInt(),
            isLocal = false
        )
    }
}
