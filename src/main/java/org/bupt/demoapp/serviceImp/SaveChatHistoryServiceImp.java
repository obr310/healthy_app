package org.bupt.demoapp.serviceImp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bupt.demoapp.service.SaveChatHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户可见的聊天历史记录服务实现
 * 使用单独的Redis key存储用户输入和LLM回复
 * Key格式：chat:display:{userId}:{conversationId}
 * 使用Redis List结构存储，便于分页查询
 */
@Service
public class SaveChatHistoryServiceImp implements SaveChatHistoryService {
    private static final Logger logger = LoggerFactory.getLogger(SaveChatHistoryServiceImp.class);
    
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
    
    /**
     * 将消息序列化为JSON
     */
    private String serializeMessage(String role, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", System.currentTimeMillis());
        
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            logger.error(">>> 消息序列化失败 - role: {}, content: {}", role, content, e);
            throw new RuntimeException("消息序列化失败", e);
        }
    }
    
    @Override
    public void saveUserMessage(String memoryId, String content) {
        String key = buildKey(memoryId);
        String jsonMessage = serializeMessage("USER", content);
        
        logger.debug(">>> 保存用户消息到Redis - key: {}, content: {}", key, content);
        stringRedisTemplate.opsForList().rightPush(key, jsonMessage);
        logger.info(">>> 用户消息保存成功 - key: {}", key);
    }
    
    @Override
    public void saveAiMessage(String memoryId, String content) {
        String key = buildKey(memoryId);
        String jsonMessage = serializeMessage("AI", content);
        
        logger.debug(">>> 保存AI消息到Redis - key: {}, content: {}", key, content);
        stringRedisTemplate.opsForList().rightPush(key, jsonMessage);
        logger.info(">>> AI消息保存成功 - key: {}", key);
    }
}
