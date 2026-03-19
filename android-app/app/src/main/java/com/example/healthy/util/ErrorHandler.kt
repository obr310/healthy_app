package com.example.healthy.util

import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Error message handler
 * Converts technical errors into user-friendly messages
 */
object ErrorHandler {
    
    /**
     * Get user-friendly error message
     * Hides technical details from users
     */
    fun getUserFriendlyMessage(exception: Exception): String {
        return when (exception) {
            is ConnectException, is UnknownHostException -> {
                "Unable to connect to server. Please check your network connection."
            }
            is SocketTimeoutException -> {
                "Request timeout. Please try again."
            }
            is HttpException -> {
                when (exception.code()) {
                    400 -> "Invalid request. Please try again."
                    401, 403 -> "Authentication failed. Please log in again."
                    404 -> "Service not found. Please contact support."
                    500, 502, 503 -> "Server error. Please try again later."
                    else -> "Network error. Please try again."
                }
            }
            else -> {
                // Generic error message for all other exceptions
                "Sorry, something went wrong. Please try again."
            }
        }
    }
    
    /**
     * Check if error is network related
     */
    fun isNetworkError(exception: Exception): Boolean {
        return exception is ConnectException ||
                exception is UnknownHostException ||
                exception is SocketTimeoutException
    }
    
    /**
     * Check if error requires re-authentication
     */
    fun requiresReauth(exception: Exception): Boolean {
        return exception is HttpException && 
                (exception.code() == 401 || exception.code() == 403)
    }
}




