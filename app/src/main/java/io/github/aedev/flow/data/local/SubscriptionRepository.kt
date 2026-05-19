package io.github.aedev.flow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.subscriptionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "subscriptions")

class SubscriptionRepository private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: SubscriptionRepository? = null
        
        fun getInstance(context: Context): SubscriptionRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubscriptionRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Keys format: "channel_{channelId}" -> JSON string with channel info
        private fun channelKey(channelId: String) = stringPreferencesKey("channel_$channelId")
        private const val SUBSCRIPTIONS_ORDER_KEY = "subscriptions_order"
    }
    
    /**
     * Subscribe to a channel
     */
    suspend fun subscribe(channel: ChannelSubscription) {
        context.subscriptionsDataStore.edit { preferences ->
            val existing = preferences[channelKey(channel.channelId)]?.let { deserializeChannel(it) }
            val safeChannel = when {
                ThumbnailUrlResolver.isYoutubeVideoThumbnail(channel.channelThumbnail) &&
                    existing?.channelThumbnail?.isNotBlank() == true &&
                    !ThumbnailUrlResolver.isYoutubeVideoThumbnail(existing.channelThumbnail) ->
                    channel.copy(channelThumbnail = existing.channelThumbnail)
                else -> channel
            }

            // Save channel data
            preferences[channelKey(safeChannel.channelId)] = serializeChannel(safeChannel)
            
            // Update order list
            val currentOrder = preferences[stringPreferencesKey(SUBSCRIPTIONS_ORDER_KEY)] ?: ""
            val orderList = if (currentOrder.isEmpty()) {
                mutableListOf()
            } else {
                currentOrder.split(",").toMutableList()
            }
            
            if (!orderList.contains(safeChannel.channelId)) {
                orderList.add(0, safeChannel.channelId)
                preferences[stringPreferencesKey(SUBSCRIPTIONS_ORDER_KEY)] = orderList.joinToString(",")
            }
        }
    }
    
    /**
     * Unsubscribe from a channel
     */
    suspend fun unsubscribe(channelId: String) {
        context.subscriptionsDataStore.edit { preferences ->
            preferences.remove(channelKey(channelId))
            
            // Update order list
            val currentOrder = preferences[stringPreferencesKey(SUBSCRIPTIONS_ORDER_KEY)] ?: ""
            if (currentOrder.isNotEmpty()) {
                val orderList = currentOrder.split(",").toMutableList()
                orderList.remove(channelId)
                preferences[stringPreferencesKey(SUBSCRIPTIONS_ORDER_KEY)] = orderList.joinToString(",")
            }
        }

        AppDatabase.getDatabase(context)
            .cacheDao()
            .deleteSubscriptionFeedForChannel(channelId)
    }
    
    /**
     * Check if subscribed to a channel
     */
    fun isSubscribed(channelId: String): Flow<Boolean> {
        return context.subscriptionsDataStore.data.map { preferences ->
            preferences.contains(channelKey(channelId))
        }
    }
    
    /**
     * Get all subscriptions
     */
    fun getAllSubscriptions(): Flow<List<ChannelSubscription>> {
        return context.subscriptionsDataStore.data.map { preferences ->
            val orderString = preferences[stringPreferencesKey(SUBSCRIPTIONS_ORDER_KEY)] ?: ""
            if (orderString.isEmpty()) {
                emptyList()
            } else {
                val orderList = orderString.split(",")
                orderList.mapNotNull { channelId ->
                    val channelData = preferences[channelKey(channelId)]
                    channelData?.let { deserializeChannel(it) }
                }
            }
        }
    }
    
    /**
     * Get all subscription IDs as a Set
     */
    suspend fun getAllSubscriptionIds(): Set<String> {
        val orderString = context.subscriptionsDataStore.data.map { preferences ->
            preferences[stringPreferencesKey(SUBSCRIPTIONS_ORDER_KEY)] ?: ""
        }.first()
        
        return if (orderString.isEmpty()) {
            emptySet()
        } else {
            orderString.split(",").toSet()
        }
    }

    /**
     * Get subscription by channel ID
     */
    fun getSubscription(channelId: String): Flow<ChannelSubscription?> {
        return context.subscriptionsDataStore.data.map { preferences ->
            val channelData = preferences[channelKey(channelId)]
            channelData?.let { deserializeChannel(it) }
        }
    }

    suspend fun repairVideoThumbnailSubscriptions(
        fetchChannelThumbnail: suspend (String) -> String
    ): Int {
        val subscriptions = getAllSubscriptions().first()
        val repairs = subscriptions
            .filter { ThumbnailUrlResolver.isYoutubeVideoThumbnail(it.channelThumbnail) }
            .mapNotNull { subscription ->
                val avatar = fetchChannelThumbnail(subscription.channelId).trim()
                if (avatar.isNotEmpty() && !ThumbnailUrlResolver.isYoutubeVideoThumbnail(avatar)) {
                    subscription.channelId to subscription.copy(channelThumbnail = avatar)
                } else {
                    null
                }
            }
            .toMap()

        if (repairs.isEmpty()) return 0

        context.subscriptionsDataStore.edit { preferences ->
            repairs.forEach { (channelId, subscription) ->
                preferences[channelKey(channelId)] = serializeChannel(subscription)
            }
        }
        return repairs.size
    }
    
    private fun serializeChannel(channel: ChannelSubscription): String {
        return "${channel.channelId}|${channel.channelName}|${channel.channelThumbnail}|${channel.subscribedAt}|${channel.lastVideoId ?: ""}|${channel.lastCheckTime}|${channel.isNotificationEnabled}|${channel.isMusic}"
    }
    
    private fun deserializeChannel(data: String): ChannelSubscription? {
        return try {
            val parts = data.split("|")
            if (parts.size >= 4) {
                ChannelSubscription(
                    channelId = parts[0],
                    channelName = parts[1],
                    channelThumbnail = parts[2],
                    subscribedAt = parts[3].toLong(),
                    lastVideoId = if (parts.size > 4 && parts[4].isNotEmpty()) parts[4] else null,
                    lastCheckTime = if (parts.size > 5 && parts[5].isNotEmpty()) parts[5].toLong() else 0L,
                    isNotificationEnabled = if (parts.size > 6 && parts[6].isNotEmpty()) parts[6].toBoolean() else false,
                    isMusic = if (parts.size > 7 && parts[7].isNotEmpty()) parts[7].toBoolean() else false
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Update the notification state for a channel
     */
    suspend fun updateNotificationState(channelId: String, enabled: Boolean) {
        context.subscriptionsDataStore.edit { preferences ->
            val channelData = preferences[channelKey(channelId)]
            if (channelData != null) {
                val subscription = deserializeChannel(channelData)
                if (subscription != null) {
                    val updated = subscription.copy(isNotificationEnabled = enabled)
                    preferences[channelKey(channelId)] = serializeChannel(updated)
                }
            }
        }
    }

    /**
     * Update the last seen video for a channel
     */
    suspend fun updateChannelLatestVideo(channelId: String, videoId: String) {
        context.subscriptionsDataStore.edit { preferences ->
            val channelData = preferences[channelKey(channelId)]
            if (channelData != null) {
                val subscription = deserializeChannel(channelData)
                if (subscription != null) {
                    val updated = subscription.copy(
                        lastVideoId = videoId,
                        lastCheckTime = System.currentTimeMillis()
                    )
                    preferences[channelKey(channelId)] = serializeChannel(updated)
                }
            }
        }
    }
}

data class ChannelSubscription(
    val channelId: String,
    val channelName: String,
    val channelThumbnail: String,
    val subscribedAt: Long = System.currentTimeMillis(),
    val lastVideoId: String? = null,
    val lastCheckTime: Long = 0L,
    val isNotificationEnabled: Boolean = false,
    val isMusic: Boolean = false
)
