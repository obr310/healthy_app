package com.example.healthy.data.model

data class Conversation(
    val id: Long,              // 会话ID
    val title: String,         // 会话标题
    val lastMessage: String = "",  // 最后一条消息
    val timestamp: Long = System.currentTimeMillis()  // 创建时间
)

