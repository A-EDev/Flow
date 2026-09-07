package io.github.aedev.flow.data.model

import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity

data class SubscriptionGroup(
    val name: String,
    val channelIds: List<String>,
    val sortOrder: Int = 0,
)

fun SubscriptionGroupEntity.toUiModel() =
    SubscriptionGroup(
        name = name,
        channelIds = if (channelIds.isBlank()) emptyList() else channelIds.split(",").filter { it.isNotBlank() },
        sortOrder = sortOrder,
    )
