package com.example.healthy.data.api

import com.example.healthy.data.model.ChatHistoryResponse
import com.example.healthy.data.model.ChatRequest
import com.example.healthy.data.model.ChatResponse
import com.example.healthy.data.model.ConversationResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ChatApi {
    @POST("chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse

    @GET("chat/conversations")
    suspend fun getConversations(@Query("userId") userId: String): List<ConversationResponse>

    @GET("chat/history")
    suspend fun getChatHistory(
        @Query("memoryId") memoryId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): ChatHistoryResponse
}

