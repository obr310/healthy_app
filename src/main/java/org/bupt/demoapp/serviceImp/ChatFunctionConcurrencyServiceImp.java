package org.bupt.demoapp.serviceImp;

import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.service.ChatFunctionConcurrencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ChatFunctionConcurrencyServiceImp implements ChatFunctionConcurrencyService {
    private static final Logger logger = LoggerFactory.getLogger(ChatFunctionConcurrencyServiceImp.class);
    private static final String KEY_PREFIX = "chat:concurrency:intent:";

    private static final Set<Intent> LIMITED_INTENTS = Set.of(
            Intent.PLAN,
            Intent.QA,
            Intent.SUMMARY
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final boolean enabled;

    private final long planLimit;
    private final long qaLimit;
    private final long summaryLimit;

    public ChatFunctionConcurrencyServiceImp(
            StringRedisTemplate stringRedisTemplate,
            @Value("${chat.intent-concurrency.enabled:true}") boolean enabled,
            @Value("${chat.intent-concurrency.plan:3}") long planLimit,
            @Value("${chat.intent-concurrency.qa:8}") long qaLimit,
            @Value("${chat.intent-concurrency.summary:6}") long summaryLimit) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.enabled = enabled;
        this.planLimit = planLimit;
        this.qaLimit = qaLimit;
        this.summaryLimit = summaryLimit;
    }

    @Override
    public boolean tryAcquire(Intent intent) {
        if (!enabled || !shouldLimit(intent)) {
            return true;
        }

        String key = buildKey(intent);
        long limit = resolveLimit(intent);

        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current == null) {
            logger.warn(">>> 功能级并发计数失败，默认放行 - intent: {}", intent);
            return true;
        }

        if (current <= limit) {
            return true;
        }

        stringRedisTemplate.opsForValue().decrement(key);
        logger.warn(">>> 功能级并发已达上限，拒绝请求 - intent: {}, current: {}, limit: {}", intent, current, limit);
        return false;
    }

    @Override
    public void release(Intent intent) {
        if (!enabled || !shouldLimit(intent)) {
            return;
        }

        String key = buildKey(intent);
        Long current = stringRedisTemplate.opsForValue().decrement(key);
        if (current != null && current < 0) {
            stringRedisTemplate.opsForValue().set(key, "0");
        }
    }

    private boolean shouldLimit(Intent intent) {
        return LIMITED_INTENTS.contains(intent);
    }

    private String buildKey(Intent intent) {
        return KEY_PREFIX + intent.name();
    }

    private long resolveLimit(Intent intent) {
        return switch (intent) {
            case PLAN -> planLimit;
            case QA -> qaLimit;
            case SUMMARY -> summaryLimit;
            default -> Long.MAX_VALUE;
        };
    }
}
