package com.example.healthy

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.healthy.data.api.RetrofitClient
import com.example.healthy.data.model.Conversation
import com.example.healthy.ui.chat.ChatScreen
import com.example.healthy.ui.conversation.ConversationListScreen
import com.example.healthy.ui.disclaimer.DisclaimerScreen
import com.example.healthy.ui.guide.UserGuideScreen
import com.example.healthy.ui.login.LoginScreen
import com.example.healthy.ui.theme.HealthyTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "HealthyPrefs"
        private const val KEY_USER_GUIDE_PREFIX = "user_guide_shown_"  // 每个用户独立的引导状态
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // SharedPreferences 用于存储用户引导状态
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setContent {
            HealthyTheme {
                var isLoggedIn by remember { mutableStateOf(false) }
                var userId by remember { mutableStateOf("") }
                var userName by remember { mutableStateOf("") }
                var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
                var currentConversation by remember { mutableStateOf<Conversation?>(null) }
                var nextConversationId by remember { mutableStateOf(1L) }
                var isLoadingConversations by remember { mutableStateOf(false) }
                
                // 引导和免责声明状态
                var showGuide by remember { mutableStateOf(false) }
                var showDisclaimerDialog by remember { mutableStateOf(false) }

                // 登录成功后加载会话列表
                LaunchedEffect(isLoggedIn, userId) {
                    if (isLoggedIn && userId.isNotEmpty() && conversations.isEmpty() && !isLoadingConversations) {
                        isLoadingConversations = true
                        Log.d(TAG, "开始加载会话列表 - userId: $userId")

                        try {
                            val conversationList = RetrofitClient.chatApi.getConversations(userId)
                            Log.d(TAG, "获取到 ${conversationList.size} 个会话")

                            if (conversationList.isNotEmpty()) {
                                // 转换为前端数据模型
                                conversations = conversationList.map { dto ->
                                    // 解析时间戳（ISO 8601 格式）
                                    val timestamp = try {
                                        // 尝试解析 ISO 8601 格式：2024-01-01T12:00:00
                                        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                        // 如果包含毫秒，使用带毫秒的格式
                                        val formatterWithMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                                        try {
                                            formatterWithMillis.parse(dto.timestamp)?.time ?: System.currentTimeMillis()
                                        } catch (e: Exception) {
                                            formatter.parse(dto.timestamp)?.time ?: System.currentTimeMillis()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "解析时间戳失败: ${dto.timestamp}", e)
                                        System.currentTimeMillis()
                                    }

                                    Conversation(
                                        id = dto.conversationId,
                                        title = dto.title,
                                        lastMessage = dto.lastMessage ?: "",
                                        timestamp = timestamp
                                    )
                                }

                                // 更新 nextConversationId 为最大ID + 1
                                val maxId = conversations.maxOfOrNull { it.id } ?: 0L
                                nextConversationId = maxId + 1
                                Log.d(TAG, "会话列表加载完成，nextConversationId: $nextConversationId")
                            } else {
                                // 没有会话，创建第一个
                                Log.d(TAG, "没有现有会话，创建第一个会话")
                                val firstConversation = Conversation(
                                    id = nextConversationId++,
                                    title = "Conversation 1"
                                )
                                conversations = listOf(firstConversation)
                                currentConversation = firstConversation
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "加载会话列表失败", e)
                            // 如果加载失败，创建默认会话
                            val firstConversation = Conversation(
                                id = nextConversationId++,
                                title = "Conversation 1"
                            )
                            conversations = listOf(firstConversation)
                            currentConversation = firstConversation
                        } finally {
                            isLoadingConversations = false
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        // 1. 显示使用引导（用户首次登录）
                        showGuide -> {
                            UserGuideScreen(
                                onGetStarted = {
                                    showGuide = false
                                    // 标记该用户已看过引导
                                    prefs.edit().putBoolean(KEY_USER_GUIDE_PREFIX + userId, true).apply()
                                    Log.d(TAG, "用户 $userId 完成使用引导")
                                }
                            )
                        }
                        // 2. 查看免责声明（从设置入口）
                        showDisclaimerDialog -> {
                            DisclaimerScreen(
                                isFirstTime = false,
                                onClose = {
                                    showDisclaimerDialog = false
                                }
                            )
                        }
                        // 3. 登录界面
                        !isLoggedIn -> {
                            LoginScreen(
                                onLoginSuccess = { result ->
                                    userId = result.userId
                                    userName = result.userName
                                    isLoggedIn = true
                                    
                                    // 检查该用户是否第一次登录（是否看过引导）
                                    val hasSeenGuide = prefs.getBoolean(KEY_USER_GUIDE_PREFIX + userId, false)
                                    if (!hasSeenGuide) {
                                        // 第一次登录，显示引导
                                        showGuide = true
                                        Log.d(TAG, "用户 $userId 第一次登录，显示引导")
                                    } else {
                                        Log.d(TAG, "用户 $userId 已看过引导，直接进入")
                                    }
                                },
                                onViewDisclaimer = {
                                    // 从登录页面查看免责声明
                                    showDisclaimerDialog = true
                                }
                            )
                        }
                        // 4. 聊天界面
                        currentConversation != null -> {
                            // 聊天界面
                            ChatScreen(
                                userId = userId,
                                userName = userName,
                                conversationId = currentConversation!!.id,
                                conversationTitle = currentConversation!!.title,
                                onBack = {
                                    currentConversation = null
                                },
                                onUpdateLastMessage = { lastMessage ->
                                    conversations = conversations.map { conv ->
                                        if (conv.id == currentConversation!!.id) {
                                            conv.copy(
                                                lastMessage = lastMessage,
                                                timestamp = System.currentTimeMillis()
                                            )
                                        } else conv
                                    }
                                }
                            )
                        }
                        else -> {
                            // 5. 会话列表界面
                            ConversationListScreen(
                                userName = userName,
                                conversations = conversations.sortedByDescending { it.timestamp },
                                onConversationClick = { conversation ->
                                    currentConversation = conversation
                                },
                                onNewConversation = {
                                    val conversationId = nextConversationId++
                                    val newConversation = Conversation(
                                        id = conversationId,
                                        title = "Conversation $conversationId"
                                    )
                                    conversations = conversations + newConversation
                                    currentConversation = newConversation
                                },
                                onViewDisclaimer = {
                                    // 从设置入口查看免责声明
                                    showDisclaimerDialog = true
                                },
                                onLogout = {
                                    // 清除认证凭证
                                    RetrofitClient.clearAuthCredentials()
                                    isLoggedIn = false
                                    userId = ""
                                    userName = ""
                                    conversations = emptyList()
                                    currentConversation = null
                                    nextConversationId = 1L
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
