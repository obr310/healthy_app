package org.bupt.demoapp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 会话锁(防止用户多次点击消息)
 * plan状态管理(redis分布式锁)
 */
@Service
public class DistributedSessionStateService {
    private static final Logger logger = LoggerFactory.getLogger(DistributedSessionStateService.class);
   //会话锁
    private static final String LOCK_PREFIX = "chat:lock:";
    //修改会话状态锁(是否在plan中)
    private static final String PLAN_PREFIX = "chat:plan:";
    private static final Duration PLAN_STATUS_TTL = Duration.ofHours(2);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>();
//lua释放锁代码
    static {
        RELEASE_LOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) " +
                "else return 0 end"
        );
        RELEASE_LOCK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public DistributedSessionStateService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //获取会话锁
    public String acquireSessionLock(String memoryId, long waitTimeout, TimeUnit waitUnit,
                                     long leaseTimeout, TimeUnit leaseUnit) {
        String key = buildLockKey(memoryId);
        String lockToken = UUID.randomUUID().toString();

        // 先尝试一次获取锁（waitTimeout=0时也应该尝试）
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, lockToken, leaseTimeout, leaseUnit);
        if (Boolean.TRUE.equals(acquired)) {
            logger.debug(">>> 获取分布式会话锁成功 - memoryId: {}", memoryId);
            return lockToken;
        }

        // 如果不需要等待，直接返回
        if (waitTimeout <= 0) {
            logger.debug(">>> 获取分布式会话锁失败（不等待）- memoryId: {}", memoryId);
            return null;
        }

        long deadlineNanos = System.nanoTime() + waitUnit.toNanos(waitTimeout);

        while (System.nanoTime() < deadlineNanos) {
            acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, lockToken, leaseTimeout, leaseUnit);
            if (Boolean.TRUE.equals(acquired)) {
                logger.debug(">>> 获取分布式会话锁成功 - memoryId: {}", memoryId);
                return lockToken;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn(">>> 获取分布式会话锁被中断 - memoryId: {}", memoryId);
                return null;
            }
        }

        logger.warn(">>> 获取分布式会话锁超时 - memoryId: {}", memoryId);
        return null;
    }
//释放会话锁
    public void releaseSessionLock(String memoryId, String lockToken) {
        if (lockToken == null) {
            return;
        }

        String key = buildLockKey(memoryId);
        Long released = stringRedisTemplate.execute(
                RELEASE_LOCK_SCRIPT,
                Collections.singletonList(key),
                lockToken
        );

        logger.debug(">>> 释放分布式会话锁 - memoryId: {}, released: {}", memoryId, released);
    }

    public boolean isPlanFlowActive(String memoryId) {
        String status = stringRedisTemplate.opsForValue().get(buildPlanKey(memoryId));
        return "ACTIVE".equals(status);
    }

    public void markPlanFlowActive(String memoryId) {
        stringRedisTemplate.opsForValue().set(buildPlanKey(memoryId), "ACTIVE", PLAN_STATUS_TTL);
        logger.debug(">>> 标记 PLAN 流程激活 - memoryId: {}", memoryId);
    }

    public void clearPlanFlow(String memoryId) {
        stringRedisTemplate.delete(buildPlanKey(memoryId));
        logger.debug(">>> 清除 PLAN 流程状态 - memoryId: {}", memoryId);
    }

    public void refreshPlanFlow(String memoryId) {
        String key = buildPlanKey(memoryId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            stringRedisTemplate.expire(key, PLAN_STATUS_TTL);
        }
    }

    private String buildLockKey(String memoryId) {
        return LOCK_PREFIX + memoryId;
    }

    private String buildPlanKey(String memoryId) {
        return PLAN_PREFIX + memoryId;
    }
}

