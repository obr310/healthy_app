package org.bupt.demoapp.controller;

import org.bupt.demoapp.aiservice.IntentService;
import org.bupt.demoapp.common.DistributedSessionStateService;
import org.bupt.demoapp.common.MemoryIds;
import org.bupt.demoapp.common.Messages;
import org.bupt.demoapp.dto.ChatHistoryResponse;
import org.bupt.demoapp.dto.ChatRequest;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.dto.ChatTaskResponse;
import org.bupt.demoapp.dto.ConversationResponse;
import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.mapper.LogMapper;
import org.bupt.demoapp.service.ChatDispatchService;
import org.bupt.demoapp.service.ChatFunctionConcurrencyService;
import org.bupt.demoapp.service.ChatGlobalConcurrencyService;
import org.bupt.demoapp.service.ChatRateLimitService;
import org.bupt.demoapp.service.ChatTaskService;
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
import java.util.concurrent.TimeUnit;

@RestController
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    ChatDispatchService chatDispatchService;

    @Autowired
    ChatTaskService chatTaskService;

    @Autowired
    ChatGlobalConcurrencyService chatGlobalConcurrencyService;

    @Autowired
    ChatRateLimitService chatRateLimitService;

    @Autowired
    ChatFunctionConcurrencyService chatFunctionConcurrencyService;

    @Autowired
    IntentService intentService;

    @Autowired
    LogMapper logMapper;

    @Autowired
    MemoryIds memoryIds;

    @Autowired
    GetChatHistoryService getChatHistoryService;

    @Autowired
    DistributedSessionStateService distributedSessionStateService;

    private static final long SESSION_LOCK_WAIT_TIMEOUT = 0;   // 等0秒，不等待
    private static final long SESSION_LOCK_LEASE_TIMEOUT = 120; // 锁自动过期时间120秒

    @PostMapping("/chat")
    public Object recordByNaturalLanguage(@RequestBody ChatRequest request) {
        long startTime = System.currentTimeMillis();
        logger.info("========== 收到聊天请求 ==========");
        logger.info("memoryId: {}, msg: {}", request.getMemoryId(), request.getMsg());

        boolean rateAllowed = false;
        boolean acquired = false;
        boolean functionAcquired = false;
        String sessionLockToken = null;
        Intent intent = null;
        try {
            rateAllowed = chatRateLimitService.tryAcquire();
            if (!rateAllowed) {
                long duration = System.currentTimeMillis() - startTime;
                logger.warn("聊天请求因全局速率限流被拒绝 - memoryId: {}, 耗时: {}ms", request.getMemoryId(), duration);
                return new ChatResponse(null, "REJECTED", Messages.ERROR_RATE_LIMITED, false, false);
            }

            acquired = chatGlobalConcurrencyService.tryAcquire();
            if (!acquired) {
                long duration = System.currentTimeMillis() - startTime;
                logger.warn("聊天请求因全局并发限流被拒绝 - memoryId: {}, 耗时: {}ms", request.getMemoryId(), duration);
                return new ChatResponse(null, "REJECTED", Messages.ERROR_TOO_MANY_CONCURRENT_REQUESTS, false, false);
            }

            String lockToken = distributedSessionStateService.acquireSessionLock(
                    request.getMemoryId(),
                    SESSION_LOCK_WAIT_TIMEOUT, TimeUnit.SECONDS,
                    SESSION_LOCK_LEASE_TIMEOUT, TimeUnit.SECONDS
            );
            if (lockToken == null) {
                long duration = System.currentTimeMillis() - startTime;
                logger.warn("聊天请求因会话锁被拒绝 - memoryId: {}, 耗时: {}ms", request.getMemoryId(), duration);
                return new ChatResponse(null, "REJECTED", Messages.ERROR_SESSION_BUSY, false, false);
            }
            sessionLockToken = lockToken;

            intent = classifyIntent(request.getMemoryId(), request.getMsg());

            functionAcquired = chatFunctionConcurrencyService.tryAcquire(intent);
            if (!functionAcquired) {
                long duration = System.currentTimeMillis() - startTime;
                logger.warn("聊天请求因功能级并发限流被拒绝 - memoryId: {}, intent: {}, 耗时: {}ms", request.getMemoryId(), intent, duration);
                return new ChatResponse(null, "REJECTED", Messages.ERROR_FUNCTION_BUSY, false, false);
            }

            if (shouldUseAsyncTask(intent)) {
                ChatTaskResponse taskResponse = chatTaskService.submit(request.getMemoryId(), request.getMsg(), intent);
                long duration = System.currentTimeMillis() - startTime;
                logger.info("========== 聊天异步任务已提交，intent: {}, 耗时: {}ms ==========" , intent, duration);
                return taskResponse;
            }

            ChatResponse response = chatDispatchService.handle(request.getMemoryId(), request.getMsg(), intent);
            long duration = System.currentTimeMillis() - startTime;
            logger.info("========== 聊天请求完成，intent: {}, 耗时: {}ms ==========" , intent, duration);
            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("聊天请求处理失败 - memoryId: {}, msg: {}, 耗时: {}ms",
                    request.getMemoryId(), request.getMsg(), duration, e);
            return new ChatResponse(null, "ERROR", Messages.ERROR_SERVICE_BUSY, false, false);
        } finally {
            if (functionAcquired && intent != null) {
                chatFunctionConcurrencyService.release(intent);
            }
            if (acquired) {
                chatGlobalConcurrencyService.release();
            }
            if (sessionLockToken != null) {
                distributedSessionStateService.releaseSessionLock(request.getMemoryId(), sessionLockToken);
            }
        }
    }

    @GetMapping("/chat/tasks/{taskId}")
    public ChatTaskResponse getTask(@PathVariable("taskId") String taskId) {
        return chatTaskService.getTask(taskId);
    }

    private Intent classifyIntent(String memoryId, String msg) {
        try {
            return intentService.classify(memoryId, msg);
        } catch (Exception e) {
            logger.error("聊天入口意图识别失败 - memoryId: {}, msg: {}", memoryId, msg, e);
            return Intent.UNKNOWN;
        }
    }

    private boolean shouldUseAsyncTask(Intent intent) {
        return intent == Intent.PLAN || intent == Intent.SUMMARY || intent == Intent.QA;
    }

    @GetMapping("/chat/conversations")
    public List<ConversationResponse> getConversations(@RequestParam("userId") String userId) {
        logger.info("========== 获取会话列表 ==========");
        logger.info("userId: {}", userId);

        List<ConversationResponse> conversations = new ArrayList<>();

        try {
            List<String> memoryIdList = logMapper.findDistinctMemoryIdsByUserId(userId);
            logger.info("从 MySQL 找到 {} 个会话", memoryIdList.size());

            for (String memoryId : memoryIdList) {
                try {
                    long conversationId = this.memoryIds.extractConversationId(memoryId);
                    ChatHistoryResponse history = getChatHistoryService.getChatHistory(memoryId, 0, 1);

                    if (history != null && !history.getMessages().isEmpty()) {
                        ChatHistoryResponse.MessageItem lastItem = history.getMessages().get(history.getMessages().size() - 1);
                        String lastMessage = lastItem.getContent();
                        Long timestampMillis = lastItem.getTimestamp();

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

            logger.info("========== 会话列表获取完成，共 {} 个会话 ==========" , conversations.size());
            return conversations;
        } catch (Exception e) {
            logger.error("获取会话列表失败 - userId: {}", userId, e);
            throw e;
        }
    }

    @GetMapping("/chat/history")
    public ChatHistoryResponse getChatHistory(
            @RequestParam("memoryId") String memoryId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        logger.info("========== 获取聊天历史记录 ==========");
        logger.info("请求参数 - memoryId: {}, page: {}, size: {}", memoryId, page, size);

        try {
            return getChatHistoryService.getChatHistory(memoryId, page, size);
        } catch (Exception e) {
            logger.error("获取聊天历史失败 - memoryId: {}, page: {}, size: {}", memoryId, page, size, e);
            throw e;
        }
    }
}
