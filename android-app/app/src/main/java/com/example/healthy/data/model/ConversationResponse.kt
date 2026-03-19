package com.example.healthy.data.model

/**
 * 后端返回的会话信息
 */
data class ConversationResponse(
    val conversationId: Long,
    val title: String,
    val lastMessage: String?,
    val timestamp: String // ISO 8601 格式的时间字符串
)
