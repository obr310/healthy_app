package com.example.healthy.ui.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.healthy.data.api.RetrofitClient
import com.example.healthy.data.model.ChatRequest
import com.example.healthy.util.ErrorHandler
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userId: String,
    userName: String,
    conversationId: Long,
    conversationTitle: String,
    onBack: () -> Unit = {},
    onUpdateLastMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var messages by remember(conversationId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isLoadingHistory by remember(conversationId) { mutableStateOf(false) }
    var currentPage by remember(conversationId) { mutableStateOf(0) }
    var hasMore by remember(conversationId) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // memoryId 格式：userId:conversationId
    val memoryId = remember(userId, conversationId) { "$userId:$conversationId" }

    // 加载历史消息
    LaunchedEffect(conversationId) {
        if (messages.isEmpty() && !isLoadingHistory) {
            isLoadingHistory = true
            Log.d("ChatScreen", "========== 开始加载历史消息 ==========")
            Log.d("ChatScreen", "memoryId: $memoryId, page: 0, size: 10")
            Log.d("ChatScreen", "conversationId: $conversationId, userId: $userId")
            
            try {
                val historyResponse = RetrofitClient.chatApi.getChatHistory(memoryId, 0, 10)
                Log.d("ChatScreen", "========== API 响应成功 ==========")
                Log.d("ChatScreen", "totalCount: ${historyResponse.totalCount}")
                Log.d("ChatScreen", "page: ${historyResponse.page}")
                Log.d("ChatScreen", "pageSize: ${historyResponse.pageSize}")
                Log.d("ChatScreen", "hasMore: ${historyResponse.hasMore}")
                Log.d("ChatScreen", "messages.size: ${historyResponse.messages.size}")
                
                // 打印每条消息
                historyResponse.messages.forEachIndexed { index, item ->
                    Log.d("ChatScreen", "消息[$index] - role: ${item.role}, content: ${item.content.take(50)}, timestamp: ${item.timestamp}")
                }
                
                // 转换为 ChatMessage 格式
                val historyMessages = historyResponse.messages.mapIndexed { index, item ->
                    val chatMessage = ChatMessage(
                        id = "${item.role}_${item.timestamp ?: System.currentTimeMillis()}_$index",
                        content = item.content,
                        isUser = item.role == "USER",
                        timestamp = item.timestamp ?: System.currentTimeMillis()
                    )
                    Log.d("ChatScreen", "转换消息[$index] - id: ${chatMessage.id}, isUser: ${chatMessage.isUser}, content: ${chatMessage.content.take(30)}")
                    chatMessage
                }
                
                Log.d("ChatScreen", "转换后消息数量: ${historyMessages.size}")
                
                messages = if (historyMessages.isEmpty()) {
                    Log.d("ChatScreen", "历史消息为空，显示欢迎消息")
                    // 如果没有历史消息，显示欢迎消息
                    listOf(
                        ChatMessage(
                            id = "welcome_$conversationId",
                            content = "Hello! I'm your Health Assistant AI. How can I help you today?",
                            isUser = false
                        )
                    )
                } else {
                    Log.d("ChatScreen", "设置历史消息，共 ${historyMessages.size} 条")
                    historyMessages
                }
                
                hasMore = historyResponse.hasMore
                currentPage = 0
                
                Log.d("ChatScreen", "当前消息列表大小: ${messages.size}")
                
                // 滚动到底部
                if (messages.isNotEmpty()) {
                    listState.scrollToItem(messages.size - 1)
                    Log.d("ChatScreen", "滚动到底部，索引: ${messages.size - 1}")
                }
                
                Log.d("ChatScreen", "========== 历史消息加载完成 ==========")
            } catch (e: Exception) {
                Log.e("ChatScreen", "========== 加载历史消息失败 ==========", e)
                Log.e("ChatScreen", "错误类型: ${e.javaClass.simpleName}")
                Log.e("ChatScreen", "错误消息: ${e.message}")
                e.printStackTrace()
                
                // 显示欢迎消息
                messages = listOf(
                    ChatMessage(
                        id = "welcome_$conversationId",
                        content = "Hello! I'm your Health Assistant AI. How can I help you today?",
                        isUser = false
                    )
                )
            } finally {
                isLoadingHistory = false
                Log.d("ChatScreen", "isLoadingHistory 设置为 false")
            }
        } else {
            Log.d("ChatScreen", "跳过加载 - messages.isEmpty: ${messages.isEmpty()}, isLoadingHistory: $isLoadingHistory")
        }
    }

    // 监听滚动，到顶部时加载更多
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisibleIndex ->
                if (firstVisibleIndex == 0 && hasMore && !isLoadingHistory && messages.isNotEmpty()) {
                    isLoadingHistory = true
                    val nextPage = currentPage + 1
                    Log.d("ChatScreen", "滚动到顶部，加载更多历史 - page: $nextPage")
                    
                    try {
                        val historyResponse = RetrofitClient.chatApi.getChatHistory(memoryId, nextPage, 10)
                        Log.d("ChatScreen", "加载更多历史成功 - 获取 ${historyResponse.messages.size} 条消息")
                        
                        val moreMessages = historyResponse.messages.map { item ->
                            ChatMessage(
                                id = "${item.role}_${item.timestamp ?: System.currentTimeMillis()}",
                                content = item.content,
                                isUser = item.role == "USER",
                                timestamp = item.timestamp ?: System.currentTimeMillis()
                            )
                        }
                        
                        // 在前面添加历史消息
                        messages = moreMessages + messages
                        hasMore = historyResponse.hasMore
                        currentPage = nextPage
                        
                        // 保持滚动位置（滚动到新加载的消息之后）
                        listState.scrollToItem(moreMessages.size)
                    } catch (e: Exception) {
                        Log.e("ChatScreen", "加载更多历史失败", e)
                    } finally {
                        isLoadingHistory = false
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = conversationTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Online",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 消息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5)),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            // 输入区域
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        placeholder = { Text("Type a message...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val userMessageText = inputText
                                inputText = ""
                                
                                // 添加用户消息
                                val userMessage = ChatMessage(
                                    id = System.currentTimeMillis().toString(),
                                    content = userMessageText,
                                    isUser = true
                                )
                                messages = messages + userMessage
                                
                                // 滚动到底部
                                scope.launch {
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                                
                                // 调用后端 API
                                scope.launch {
                                    try {
                                        Log.d("ChatScreen", "发送消息: memoryId=$memoryId, userId=$userId, conversationId=$conversationId, msg=$userMessageText")
                                        val response = RetrofitClient.chatApi.sendMessage(
                                            ChatRequest(
                                                memoryId = memoryId,  // 使用正确的格式：userId:conversationId
                                                msg = userMessageText
                                            )
                                        )
                                        Log.d("ChatScreen", "AI回复: intent=${response.intent}, reply=${response.reply}, logId=${response.logId}")
                                        
                                        // 添加 AI 回复
                                        // 如果 logId 为 null，使用时间戳作为 ID
                                        val aiMessage = ChatMessage(
                                            id = response.logId ?: "AI_${System.currentTimeMillis()}",
                                            content = response.reply,  // 使用LLM生成的友好回复
                                            isUser = false
                                        )
                                        messages = messages + aiMessage
                                        
                                        // 更新会话的最后一条消息（使用 AI 回复，因为它是最新的）
                                        onUpdateLastMessage(response.reply)
                                        
                                        // 滚动到底部
                                        listState.animateScrollToItem(messages.size - 1)
                                    } catch (e: Exception) {
                                        Log.e("ChatScreen", "发送消息失败", e)
                                        // 使用 ErrorHandler 获取友好的错误消息
                                        val friendlyMessage = ErrorHandler.getUserFriendlyMessage(e)
                                        val errorMessage = ChatMessage(
                                            id = System.currentTimeMillis().toString(),
                                            content = friendlyMessage,
                                            isUser = false
                                        )
                                        messages = messages + errorMessage
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else Color.Gray.copy(alpha = 0.3f),
                                RoundedCornerShape(24.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            // AI 头像
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "AI",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 消息气泡
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = if (message.isUser) 16.dp else 4.dp,
                topEnd = if (message.isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (message.isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (message.isUser) Color.White
                else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
        }

        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Me",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

