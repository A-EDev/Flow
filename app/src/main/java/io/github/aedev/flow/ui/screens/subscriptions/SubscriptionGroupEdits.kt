package io.github.aedev.flow.ui.screens.subscriptions

import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity

internal suspend fun SubscriptionGroupDao.appendGroup(
    name: String,
    channelIds: List<String>,
) {
    insertGroup(
        SubscriptionGroupEntity(
            name = name,
            channelIds = channelIds.joinToString(","),
            sortOrder = getAllGroupsOnce().size,
        ),
    )
}

/** Returns false when no group is called [oldName]. A rename is a delete and insert because the name is the key. */
internal suspend fun SubscriptionGroupDao.editGroup(
    oldName: String,
    newName: String,
    channelIds: List<String>,
): Boolean {
    val existing = getAllGroupsOnce().find { it.name == oldName } ?: return false
    if (oldName != newName) {
        deleteGroup(oldName)
        insertGroup(existing.copy(name = newName, channelIds = channelIds.joinToString(",")))
    } else {
        updateGroup(existing.copy(channelIds = channelIds.joinToString(",")))
    }
    return true
}
