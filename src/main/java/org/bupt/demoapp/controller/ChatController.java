package org.bupt.demoapp.controller;

import org.bupt.demoapp.common.MemoryIds;
import org.bupt.demoapp.common.Messages;
import org.bupt.demoapp.dto.ChatHistoryResponse;
import org.bupt.demoapp.dto.ChatRequest;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.dto.ConversationResponse;
import org.bupt.demoapp.entity.ChatLog;
import org.bupt.demoapp.mapper.LogMapper;
import org.bupt.demoapp.service.ChatDispatchService;
import org.bupt.demoapp.service.GetChatHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@RestController
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    @Autowired
    ChatDispatchService chatDispatchService;

    @Autowired
    LogMapper logMapper;

    @Autowired
    MemoryIds memoryIds;

    @Autowired
    GetChatHistoryService getChatHistoryService;

    @PostMapping("/chat")
    public ChatResponse recordByNaturalLanguage(@RequestBody ChatRequest request) {
        long startTime = System.currentTimeMillis();
        logger.info("========== 收到聊天请求 ==========");
        logger.info("memoryId: {}, msg: {}", request.getMemoryId(), request.getMsg());
        
        try {
            ChatResponse response = chatDispatchService.handle(request.getMemoryId(), request.getMsg());
            long duration = System.currentTimeMillis() - startTime;
            logger.info("========== 聊天请求完成，耗时: {}ms ==========", duration);
            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("聊天请求处理失败 - memoryId: {}, msg: {}, 耗时: {}ms", 
                    request.getMemoryId(), request.getMsg(), duration, e);
            // 返回友好的错误消息
            return new ChatResponse(null, "ERROR", Messages.ERROR_SERVICE_BUSY, false, false);
        }
    }

    /**
     * 获取用户的所有会话列表
     * @param userId 用户ID
     * @return 会话列表
     */
    @GetMapping("/chat/conversations")
    public List<ConversationResponse> getConversations(@RequestParam("userId") String userId) {
        logger.info("========== 获取会话列表 ==========");
        logger.info("userId: {}", userId);
        
        List<ConversationResponse> conversations = new ArrayList<>();
        
        try {
            // 查询该用户的所有 memoryId（从 MySQL）
            List<String> memoryIdList = logMapper.findDistinctMemoryIdsByUserId(userId);
            logger.info("从 MySQL 找到 {} 个会话", memoryIdList.size());
            
            // 对每个 memoryId，从 Redis 获取最后一条消息
            for (String memoryId : memoryIdList) {
                try {
                    // 解析出 conversationId
                    long conversationId = this.memoryIds.extractConversationId(memoryId);
                    
                    // 从 Redis 获取聊天历史（最新1条）
                    ChatHistoryResponse history = getChatHistoryService.getChatHistory(memoryId, 0, 1);
                    
                    if (history != null && !history.getMessages().isEmpty()) {
                        // 使用 Redis 中的最新消息
                        ChatHistoryResponse.MessageItem lastItem = history.getMessages().get(history.getMessages().size() - 1);
                        String lastMessage = lastItem.getContent();
                        Long timestampMillis = lastItem.getTimestamp();
                        
                        // 将时间戳（毫秒）转换为 LocalDateTime
                        LocalDateTime timestamp = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(timestampMillis),
                            ZoneId.systemDefault()
                        );
                        
                        logger.info("从 Redis 获取最新消息 - conversationId: {}, lastMessage: {}", 
                                conversationId, lastMessage.substring(0, Math.min(30, lastMessage.length())));
                        
                        ConversationResponse response = new ConversationResponse(
                            conversationId,
                            Messages.CONVERSATION_TITLE_PREFIX + conversationId,
                            lastMessage,
                            timestamp
                        );
                        conversations.add(response);
                        logger.info("添加会话: id={}, title={}", conversationId, response.getTitle());
                    } else {
                        logger.warn("Redis 中没有消息 - memoryId: {}, 跳过该会话", memoryId);
                    }
                    
                } catch (Exception e) {
                    logger.error("处理 memoryId {} 时出错", memoryId, e);
                }
            }
            
            logger.info("========== 会话列表获取完成，共 {} 个会话 ==========", conversations.size());
            return conversations;
        } catch (Exception e) {
            logger.error("获取会话列表失败 - userId: {}", userId, e);
            throw e;
        }
    }

    /**
     * 获取会话的历史记录（分页）
     * @param memoryId 会话标识（格式：userId:conversationId）
     * @param page 页码（从0开始）
     * @param size 每页大小（默认10条）
     * @return 历史消息列表
     */
    @GetMapping("/chat/history")
    public ChatHistoryResponse getChatHistory(
            @RequestParam("memoryId") String memoryId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        
        logger.info("========== 获取聊天历史记录 ==========");
        logger.info("请求参数 - memoryId: {}, page: {}, size: {}", memoryId, page, size);
        
        try {
            ChatHistoryResponse response = getChatHistoryService.getChatHistory(memoryId, page, size);
            return response;
        } catch (Exception e) {
            logger.error("获取聊天历史失败 - memoryId: {}, page: {}, size: {}", memoryId, page, size, e);
            throw e;
        }
    }

 
 }





