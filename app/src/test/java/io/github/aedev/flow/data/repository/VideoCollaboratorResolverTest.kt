package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.VideoCollaborator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoCollaboratorResolverTest {
    @Test
    fun successfulEmptyResultIsNegativeCachedUntilExpiry() = runTest {
        var nowMillis = 0L
        var fetchCount = 0
        val lookup = VideoCollaboratorLookup(
            fetchCollaborators = {
                fetchCount += 1
                Result.success(emptyList())
            },
            negativeCacheTtlMillis = 1_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            timeSourceMillis = { nowMillis },
        )

        assertTrue(lookup.resolve("video").isEmpty())
        assertTrue(lookup.resolve("video").isEmpty())
        assertEquals(1, fetchCount)

        nowMillis = 1_001L

        assertTrue(lookup.resolve("video").isEmpty())
        assertEquals(2, fetchCount)
    }

    @Test
    fun successfulCollaboratorsAreCached() = runTest {
        var fetchCount = 0
        val collaborators = listOf(
            VideoCollaborator(name = "First", channelId = "UC-first"),
            VideoCollaborator(name = "Second", channelId = "UC-second"),
        )
        val lookup = VideoCollaboratorLookup(
            fetchCollaborators = {
                fetchCount += 1
                Result.success(collaborators)
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(collaborators, lookup.resolve("video"))
        assertEquals(collaborators, lookup.resolve("video"))
        assertEquals(1, fetchCount)
    }

    @Test
    fun failedResultIsNotCached() = runTest {
        var fetchCount = 0
        val lookup = VideoCollaboratorLookup(
            fetchCollaborators = {
                fetchCount += 1
                Result.failure(IllegalStateException("network"))
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertTrue(lookup.resolve("video").isEmpty())
        assertTrue(lookup.resolve("video").isEmpty())
        assertEquals(2, fetchCount)
    }

    @Test
    fun timeoutIsNotNegativeCached() = runTest {
        var fetchCount = 0
        val lookup = VideoCollaboratorLookup(
            fetchCollaborators = {
                fetchCount += 1
                delay(2_000L)
                Result.success(emptyList())
            },
            timeoutMillis = 1_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertTrue(lookup.resolve("video").isEmpty())
        assertTrue(lookup.resolve("video").isEmpty())
        assertEquals(2, fetchCount)
    }

    @Test
    fun concurrentRequestsForSameVideoShareOneFetch() = runTest {
        var fetchCount = 0
        val lookup = VideoCollaboratorLookup(
            fetchCollaborators = {
                fetchCount += 1
                delay(100L)
                Result.success(emptyList())
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val first = async { lookup.resolve("video") }
        val second = async { lookup.resolve("video") }

        assertTrue(first.await().isEmpty())
        assertTrue(second.await().isEmpty())
        assertEquals(1, fetchCount)
    }

    @Test
    fun blankVideoIdDoesNotFetch() = runTest {
        var fetchCount = 0
        val lookup = VideoCollaboratorLookup(
            fetchCollaborators = {
                fetchCount += 1
                Result.success(emptyList())
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertTrue(lookup.resolve(" ").isEmpty())
        assertEquals(0, fetchCount)
    }
}
