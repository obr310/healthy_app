package com.example.healthy.data.model

data class AuthResponse(
    val ok: Boolean,
    val message: String,
    val userId: Long?,
    val userName: String?
)

