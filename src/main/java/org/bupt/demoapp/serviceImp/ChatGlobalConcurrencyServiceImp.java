package org.bupt.demoapp.serviceImp;

import org.bupt.demoapp.service.ChatGlobalConcurrencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatGlobalConcurrencyServiceImp implements ChatGlobalConcurrencyService {
    private static final Logger logger = LoggerFactory.getLogger(ChatGlobalConcurrencyServiceImp.class);
    private static final String GLOBAL_CONCURRENCY_KEY = "chat:concurrency:global";

    private final StringRedisTemplate stringRedisTemplate;
    private final boolean enabled;
    private final long maxConcurrentRequests;

    public ChatGlobalConcurrencyServiceImp(
            StringRedisTemplate stringRedisTemplate,
            @Value("${chat.concurrency.enabled:true}") boolean enabled,
            @Value("${chat.concurrency.max-concurrent-requests:40}") long maxConcurrentRequests) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.enabled = enabled;
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    @Override
    public boolean tryAcquire() {
        if (!enabled) {
            logger.debug(">>> 全局并发限流已禁用，默认放行");
            return true;
        }
        Long current = stringRedisTemplate.opsForValue().increment(GLOBAL_CONCURRENCY_KEY);
        if (current == null) {
            logger.warn(">>> 全局并发计数失败，默认放行");
            return true;
        }
        if (current <= maxConcurrentRequests) {
            logger.debug(">>> 全局并发限流通过 - current: {}, max: {}", current, maxConcurrentRequests);
            return true;
        }
        release();
        logger.warn(">>> 全局并发已达上限，拒绝请求 - current: {}, max: {}", current, maxConcurrentRequests);
        return false;
    }

    @Override
    public void release() {
        if (!enabled) {
            return;
        }
        Long current = stringRedisTemplate.opsForValue().decrement(GLOBAL_CONCURRENCY_KEY);
        if (current != null && current < 0) {
            stringRedisTemplate.opsForValue().set(GLOBAL_CONCURRENCY_KEY, "0");
        }
    }
}
