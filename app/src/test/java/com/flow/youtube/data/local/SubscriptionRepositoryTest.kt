package com.flow.youtube.data.local

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test

class SubscriptionRepositoryTest {

    private lateinit var repository: SubscriptionRepository

    @Before
    fun setup() {
        // We mock the context but since we only test internal logic via reflection, 
        // it shouldn't hit the DataStore extension property if we avoid calling live methods.
        repository = SubscriptionRepository.getInstance(mockk(relaxed = true))
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `serialize and deserialize channel subscription correctly`() {
        val subscription = ChannelSubscription(
            channelId = "UC123",
            channelName = "Test Channel",
            channelThumbnail = "thumb_url",
            subscribedAt = 123456789L,
            lastVideoId = "vid99",
            lastCheckTime = 987654321L
        )

        // Access private methods via reflection
        val serializeMethod = SubscriptionRepository::class.java.getDeclaredMethod("serializeChannel", ChannelSubscription::class.java)
        serializeMethod.isAccessible = true
        val serialized = serializeMethod.invoke(repository, subscription) as String

        assertThat(serialized).contains("UC123")
        assertThat(serialized).contains("Test Channel")
        assertThat(serialized).contains("vid99")

        val deserializeMethod = SubscriptionRepository::class.java.getDeclaredMethod("deserializeChannel", String::class.java)
        deserializeMethod.isAccessible = true
        val deserialized = deserializeMethod.invoke(repository, serialized) as ChannelSubscription?

        assertThat(deserialized).isNotNull()
        assertThat(deserialized?.channelId).isEqualTo(subscription.channelId)
        assertThat(deserialized?.channelName).isEqualTo(subscription.channelName)
        assertThat(deserialized?.lastVideoId).isEqualTo(subscription.lastVideoId)
        assertThat(deserialized?.lastCheckTime).isEqualTo(subscription.lastCheckTime)
    }

    @Test
    fun `deserializeChannel handles corrupted data gracefully`() {
        val deserializeMethod = SubscriptionRepository::class.java.getDeclaredMethod("deserializeChannel", String::class.java)
        deserializeMethod.isAccessible = true
        
        val result = deserializeMethod.invoke(repository, "corrupted|data|only") as ChannelSubscription?
        
        assertThat(result).isNull()
    }
}
