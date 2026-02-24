package org.bupt.demoapp.serviceImp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bupt.demoapp.dto.ChatHistoryResponse;
import org.bupt.demoapp.service.GetChatHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天历史记录服务实现
 * 直接从 chat:display:{memoryId} 读取用户可见的消息
 */
@Service
public class GetChatHistoryServiceImp implements GetChatHistoryService {
    private static final Logger logger = LoggerFactory.getLogger(GetChatHistoryServiceImp.class);
    
    private static final String KEY_PREFIX = "chat:display:";
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 构建Redis key
     * @param memoryId 格式：userId:conversationId
     * @return chat:display:{userId}:{conversationId}
     */
    private String buildKey(String memoryId) {
        return KEY_PREFIX + memoryId;
    }
    
    @Override
    public ChatHistoryResponse getChatHistory(String memoryId, int page, int size) {
        logger.info(">>> 开始获取聊天历史 - memoryId: {}, page: {}, size: {}", memoryId, page, size);
        
        try {
            String key = buildKey(memoryId);
            
            // 获取消息总数
            Long totalCountLong = stringRedisTemplate.opsForList().size(key);
            int totalCount = totalCountLong != null ? totalCountLong.intValue() : 0;
            
            if (totalCount == 0) {
                logger.info(">>> 没有找到历史记录 - memoryId: {}", memoryId);
                return new ChatHistoryResponse(Collections.emptyList(), 0, page, size, false);
            }
            
            logger.info(">>> Redis 中消息总数: {}", totalCount);
            
            // 计算分页范围
            // page=0 返回最新的消息，page=1 返回更早的消息
            // 消息内部保持时间升序（用户问→AI答的正确顺序）
            int startIndex = Math.max(totalCount - (page + 1) * size, 0);
            int endIndex = totalCount - page * size - 1;
            
            // 检查是否超出范围
            if (endIndex < 0 || startIndex >= totalCount) {
                logger.info(">>> 页码超出范围 - startIndex: {}, endIndex: {}, totalCount: {}", 
                        startIndex, endIndex, totalCount);
                return new ChatHistoryResponse(Collections.emptyList(), totalCount, page, size, false);
            }
            
            logger.info(">>> 计算分页范围 - page: {}, size: {}, totalCount: {}, startIndex: {}, endIndex: {}", 
                    page, size, totalCount, startIndex, endIndex);
            
            // 从Redis List中获取指定范围的消息（保持时间升序，不反转）
            List<String> jsonMessages = stringRedisTemplate.opsForList().range(key, startIndex, endIndex);
            
            if (jsonMessages == null || jsonMessages.isEmpty()) {
                logger.info(">>> 获取消息为空 - memoryId: {}", memoryId);
                return new ChatHistoryResponse(Collections.emptyList(), totalCount, page, size, false);
            }
            
            // 将JSON消息转换为MessageItem
            List<ChatHistoryResponse.MessageItem> messageItems = new ArrayList<>();
            for (String jsonMessage : jsonMessages) {
                ChatHistoryResponse.MessageItem item = deserializeMessage(jsonMessage);
                if (item != null) {
                    messageItems.add(item);
                }
            }
            
            // 判断是否还有更多消息（从末尾往前查，如果 startIndex > 0 说明还有更旧的消息）
            boolean hasMore = startIndex > 0;
            
            logger.info(">>> 历史记录获取完成 - 返回 {} 条消息，总共 {} 条消息，hasMore: {}", 
                    messageItems.size(), totalCount, hasMore);
            
            return new ChatHistoryResponse(messageItems, totalCount, page, size, hasMore);
        } catch (Exception e) {
            logger.error(">>> 获取聊天历史失败 - memoryId: {}, page: {}, size: {}", memoryId, page, size, e);
            throw new RuntimeException("获取聊天历史失败", e);
        }
    }
    
    /**
     * 将JSON字符串反序列化为MessageItem
     */
    private ChatHistoryResponse.MessageItem deserializeMessage(String jsonMessage) {
        try {
            JsonNode node = objectMapper.readTree(jsonMessage);
            String role = node.get("role").asText();
            String content = node.get("content").asText();
            Long timestamp = node.has("timestamp") ? node.get("timestamp").asLong() : null;
            
            return new ChatHistoryResponse.MessageItem(role, content, timestamp);
        } catch (JsonProcessingException e) {
            logger.error(">>> 消息反序列化失败 - jsonMessage: {}", jsonMessage, e);
            return null;
        }
    }
}
