package com.example.healthy.data.api

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val TAG = "RetrofitClient"
    
    private const val BASE_URL = "serverhost"
    
    // Basic Auth 拦截器
    private val basicAuthInterceptor = BasicAuthInterceptor()
    
    init {
        Log.d(TAG, "初始化 Retrofit，BASE_URL = $BASE_URL")
    }
    
    /**
     * 设置登录凭证
     * 登录成功后调用，用于后续所有请求的认证
     */
    fun setAuthCredentials(username: String, password: String) {
        basicAuthInterceptor.setCredentials(username, password)
        Log.d(TAG, "已设置 Basic Auth 凭证")
    }
    
    /**
     * 清除登录凭证
     * 退出登录时调用
     */
    fun clearAuthCredentials() {
        basicAuthInterceptor.clearCredentials()
        Log.d(TAG, "已清除 Basic Auth 凭证")
    }
    
    // Cookie 管理器 - 自动保存和发送 Session Cookie
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
        
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            if (cookies.isNotEmpty()) {
                cookieStore[host] = cookies.toMutableList()
                Log.d(TAG, " 保存 Cookie: url=${url.encodedPath}, host=$host, cookies=${cookies.map { "${it.name}=${it.value}" }}")
                Log.d(TAG, "当前 Cookie 存储状态: ${cookieStore.entries.map { "${it.key} -> ${it.value.map { c -> c.name }}" }}")
            } else {
                Log.d(TAG, "响应中没有 Cookie: url=${url.encodedPath}")
            }
        }
        
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val cookies = cookieStore[host]?.filter { cookie ->
                // 检查 Cookie 是否过期
                cookie.expiresAt > System.currentTimeMillis()
            } ?: emptyList()
            
            if (cookies.isNotEmpty()) {
                Log.d(TAG, "加载 Cookie: url=${url.encodedPath}, host=$host, cookies=${cookies.map { "${it.name}=${it.value}" }}")
            } else {
                Log.w(TAG, "没有可用的 Cookie: url=${url.encodedPath}, host=$host")
                Log.w(TAG, "当前 Cookie 存储状态: ${cookieStore.entries.map { "${it.key} -> ${it.value.map { c -> c.name }}" }}")
            }
            return cookies
        }
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)  // Cookie 管理器
        .addInterceptor(basicAuthInterceptor)  // Basic Auth 拦截器（必须在 logging 之前）
        .addInterceptor(loggingInterceptor)    // 日志拦截器
        .connectTimeout(60, TimeUnit.SECONDS)  // 连接超时增加到60秒
        .readTimeout(60, TimeUnit.SECONDS)     // 读取超时增加到60秒
        .writeTimeout(60, TimeUnit.SECONDS)    // 写入超时增加到60秒
        .retryOnConnectionFailure(true)        // 连接失败时自动重试
        .build()
    
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val chatApi: ChatApi = retrofit.create(ChatApi::class.java)
}

