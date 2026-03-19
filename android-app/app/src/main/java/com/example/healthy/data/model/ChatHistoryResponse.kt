package com.example.healthy.data.model

/**
 * 聊天历史记录响应
 */
data class ChatHistoryResponse(
    val messages: List<MessageItem>,  // 消息列表
    val totalCount: Int,              // 总消息数
    val page: Int,                    // 当前页码
    val pageSize: Int,                // 每页大小
    val hasMore: Boolean              // 是否还有更多消息
)

/**
 * 单条消息项
 */
data class MessageItem(
    val role: String,      // USER 或 AI
    val content: String,   // 消息内容
    val timestamp: Long? = null  // 时间戳（可选）
)
