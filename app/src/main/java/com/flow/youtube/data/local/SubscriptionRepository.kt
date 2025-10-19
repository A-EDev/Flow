package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
            // Save channel data
            preferences[channelKey(channel.channelId)] = serializeChannel(channel)
            
            // Update order list
            val currentOrder = preferences[stringPreferencesKey(SUBSCRIPTIONS_ORDER_KEY)] ?: ""
            val orderList = if (currentOrder.isEmpty()) {
                mutableListOf()
            } else {
                currentOrder.split(",").toMutableList()
            }
            
            if (!orderList.contains(channel.channelId)) {
                orderList.add(0, channel.channelId) // Add to front
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
     * Get subscription by channel ID
     */
    fun getSubscription(channelId: String): Flow<ChannelSubscription?> {
        return context.subscriptionsDataStore.data.map { preferences ->
            val channelData = preferences[channelKey(channelId)]
            channelData?.let { deserializeChannel(it) }
        }
    }
    
    private fun serializeChannel(channel: ChannelSubscription): String {
        return "${channel.channelId}|${channel.channelName}|${channel.channelThumbnail}|${channel.subscribedAt}"
    }
    
    private fun deserializeChannel(data: String): ChannelSubscription? {
        return try {
            val parts = data.split("|")
            if (parts.size >= 4) {
                ChannelSubscription(
                    channelId = parts[0],
                    channelName = parts[1],
                    channelThumbnail = parts[2],
                    subscribedAt = parts[3].toLong()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class ChannelSubscription(
    val channelId: String,
    val channelName: String,
    val channelThumbnail: String,
    val subscribedAt: Long = System.currentTimeMillis()
)
