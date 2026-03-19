package com.example.healthy.data.model

data class ChatResponse(
    val logId: String,           // 日志唯一标识
    val intent: String,          // AI识别的意图类型（如：record, query, chat等）
    val reply: String,           // LLM生成的回复消息（给用户展示的友好回复）
    val mysqlStored: Boolean,    // 是否成功存入mysql
    val milvusStored: Boolean    // 是否存入milvus
)

