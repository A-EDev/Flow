package io.github.aedev.flow.data.model

data class LiveChatMessage(
    val id: String,
    val author: String,
    val authorPhotoUrl: String?,
    val message: String,
    val timestamp: String?,
    val type: LiveChatMessageType = LiveChatMessageType.TEXT,
    val isOwner: Boolean = false,
    val isModerator: Boolean = false,
    val isVerified: Boolean = false,
    val isMember: Boolean = false,
    val memberBadgeUrl: String? = null,
    // Super-chat only. ARGB colors are packed ints suitable for Compose Color(argb).
    val superChatAmount: String? = null,
    val superChatArgb: Int? = null,
    val superChatHeaderArgb: Int? = null,
)

enum class LiveChatMessageType { TEXT, SUPER_CHAT, MEMBERSHIP }
