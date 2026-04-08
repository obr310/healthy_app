package org.bupt.demoapp.serviceImp;

import org.bupt.demoapp.service.ChatRateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class ChatRateLimitServiceImp implements ChatRateLimitService {
    private static final Logger logger = LoggerFactory.getLogger(ChatRateLimitServiceImp.class);
    private static final String RATE_LIMIT_KEY = "chat:ratelimit:global";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> tokenBucketScript;
    private final boolean enabled;
    private final long capacity;
    private final long refillRate;

    public ChatRateLimitServiceImp(
            StringRedisTemplate stringRedisTemplate,
            @Value("${chat.ratelimit.enabled:true}") boolean enabled,
            @Value("${chat.ratelimit.capacity:20}") long capacity,
            @Value("${chat.ratelimit.refill-rate:20}") long refillRate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillRate = refillRate;

        RedisScript<Long> script = RedisScript.of(
                new ClassPathResource("scripts/rate_limit_token_bucket.lua"),
                Long.class
        );
        this.tokenBucketScript = script;
    }

    @Override
    public boolean tryAcquire() {
        if (!enabled) {
            logger.debug(">>> 速率限流已禁用，默认放行");
            return true;
        }

        long now = System.currentTimeMillis();
        List<String> keys = Collections.singletonList(RATE_LIMIT_KEY);
        Long result = stringRedisTemplate.execute(
                tokenBucketScript,
                keys,
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(now),
                "1"
        );

        if (result == null) {
            logger.warn(">>> 速率限流执行失败，默认放行");
            return true;
        }

        if (result == 1L) {
            logger.debug(">>> 速率限流通过 - capacity: {}, refillRate: {}/s", capacity, refillRate);
            return true;
        }

        logger.warn(">>> 速率限流触发，令牌桶无可用令牌 - capacity: {}, refillRate: {}/s",
                     capacity, refillRate);
        return false;
    }
}
