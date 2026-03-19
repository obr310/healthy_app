package com.example.healthy.data.api

import com.example.healthy.data.model.AuthRequest
import com.example.healthy.data.model.AuthResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse
}

