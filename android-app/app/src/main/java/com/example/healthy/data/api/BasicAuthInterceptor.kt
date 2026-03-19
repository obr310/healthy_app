package com.example.healthy.data.api

import android.util.Base64
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Basic Auth 拦截器
 * 为每个请求自动添加 Basic 认证头
 */
class BasicAuthInterceptor : Interceptor {
    private var credentials: String? = null
    
    fun setCredentials(username: String, password: String) {
        val auth = "$username:$password"
        val encodedAuth = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
        credentials = "Basic $encodedAuth"
        Log.d("BasicAuthInterceptor", "设置认证凭证: username=$username")
    }
    
    fun clearCredentials() {
        credentials = null
        Log.d("BasicAuthInterceptor", "清除认证凭证")
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // 如果有凭证，添加到请求头
        val authenticatedRequest = credentials?.let {
            request.newBuilder()
                .header("Authorization", it)
                .build()
        } ?: request
        
        return chain.proceed(authenticatedRequest)
    }
}

