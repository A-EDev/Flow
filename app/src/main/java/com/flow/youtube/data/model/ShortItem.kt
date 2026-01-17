package com.flow.youtube.data.model

// Renamed from ShortVideo to ShortItem to solve compilation cache/ghost class issues
data class ShortItem(
    val id: String,
    val title: String,
    val viewCountStr: String,
    val channelName: String,
    val channelId: String,
    val thumbnail: String, 
    val params: String? = null
)
