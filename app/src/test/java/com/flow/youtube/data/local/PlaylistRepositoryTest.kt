package com.flow.youtube.data.local

import com.flow.youtube.data.local.dao.PlaylistDao
import com.flow.youtube.data.local.dao.VideoDao
import com.flow.youtube.data.local.entity.PlaylistEntity
import com.flow.youtube.data.local.entity.VideoEntity
import com.flow.youtube.data.model.Video
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class PlaylistRepositoryTest {

    private val playlistDao: PlaylistDao = mockk()
    private val videoDao: VideoDao = mockk()
    private lateinit var repository: PlaylistRepository

    @Before
    fun setup() {
        repository = PlaylistRepository(playlistDao, videoDao)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun createVideo(id: String) = Video(
        id = id,
        title = "Title $id",
        channelName = "Channel",
        channelId = "channelId",
        thumbnailUrl = "thumbnail",
        duration = 100,
        viewCount = 1000L,
        uploadDate = "today",
        likeCount = 0
    )

    @Test
    fun `addToSavedShorts inserts playlist if not exists`() = runTest {
        val video = createVideo("short1")
        
        coEvery { playlistDao.getPlaylist("saved_shorts") } returns null
        coEvery { playlistDao.insertPlaylist(any()) } just Runs
        coEvery { videoDao.insertVideo(any()) } just Runs
        coEvery { playlistDao.insertPlaylistVideoCrossRef(any()) } just Runs
        
        repository.addToSavedShorts(video)
        
        coVerify { playlistDao.insertPlaylist(match { it.id == "saved_shorts" }) }
        coVerify { videoDao.insertVideo(any()) }
        coVerify { playlistDao.insertPlaylistVideoCrossRef(match { it.playlistId == "saved_shorts" && it.videoId == "short1" }) }
    }

    @Test
    fun `addToWatchLater inserts video and relation`() = runTest {
        val video = createVideo("vid1")
        
        coEvery { playlistDao.getPlaylist("watch_later") } returns mockk()
        coEvery { videoDao.insertVideo(any()) } just Runs
        coEvery { playlistDao.insertPlaylistVideoCrossRef(any()) } just Runs
        
        repository.addToWatchLater(video)
        
        coVerify(exactly = 0) { playlistDao.insertPlaylist(any()) }
        coVerify { videoDao.insertVideo(match { it.id == "vid1" }) }
        coVerify { playlistDao.insertPlaylistVideoCrossRef(match { it.playlistId == "watch_later" && it.videoId == "vid1" }) }
    }

    @Test
    fun `removeFromWatchLater calls dao`() = runTest {
        coEvery { playlistDao.removeVideoFromPlaylist("watch_later", "vid1") } just Runs
        
        repository.removeFromWatchLater("vid1")
        
        coVerify { playlistDao.removeVideoFromPlaylist("watch_later", "vid1") }
    }
}
