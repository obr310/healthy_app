package org.bupt.demoapp.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    public long nextConversationId(Long userId) {
     String key="chat:conversation:"+userId;
     Long nextId= redisTemplate.opsForValue().increment(key);
     if(nextId==null){
         throw  new IllegalStateException("Redis INCR returned null for key=" + key);
     };
     return nextId;
    }

}
