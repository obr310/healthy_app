package org.bupt.demoapp.repository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@Repository
public class RedisChatMemory implements ChatMemoryStore {
    private static final Logger logger = LoggerFactory.getLogger(RedisChatMemory.class);
    private static final String KEY_PREFIX = "chat:memory:";
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private String buildKey(Object memoryId) {
        String key = KEY_PREFIX + memoryId.toString();
        return key;
    }

    @Override
    public List<ChatMessage> getMessages(Object messageId) {
        String key = buildKey(messageId);
        logger.debug(">>> Redis GET - key: {}", key);
        String jsonMessage = stringRedisTemplate.opsForValue().get(key);
        List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(jsonMessage);
        logger.debug(">>> Redis GET 完成 - key: {}, 消息数: {}", key, messages != null ? messages.size() : 0);
        return messages;
    }

    @Override
    public void updateMessages(Object messageId, List<ChatMessage> list) {
        String key = buildKey(messageId);
        String jsonMessage = ChatMessageSerializer.messagesToJson(list);
        logger.debug(">>> Redis SET - key: {}, 消息数: {}", key, list != null ? list.size() : 0);
        stringRedisTemplate.opsForValue().set(key, jsonMessage);
    }

    @Override
    public void deleteMessages(Object messageId) {
        String key = buildKey(messageId);
        logger.debug(">>> Redis DELETE - key: {}", key);
        stringRedisTemplate.delete(key);
    }
}