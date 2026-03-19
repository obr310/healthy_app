package com.example.healthy.data.model

data class ChatRequest(
    val memoryId: String,  // 会话标识（使用 userId）
    val msg: String        // 用户输入
)

