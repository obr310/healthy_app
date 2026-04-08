package org.bupt.demoapp.serviceImp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bupt.demoapp.common.Messages;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.dto.ChatTaskResponse;
import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.service.ChatDispatchService;
import org.bupt.demoapp.service.ChatTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class ChatTaskServiceImp implements ChatTaskService {
    private static final Logger logger = LoggerFactory.getLogger(ChatTaskServiceImp.class);
    private static final String TASK_KEY_PREFIX = "chat:task:";
    private static final String MEMORY_ACTIVE_TASK_KEY_PREFIX = "chat:task:memory:";
    private static final Duration TASK_TTL = Duration.ofHours(2);
    private static final Duration ACTIVE_TASK_TTL = Duration.ofMinutes(30);
    private static final long GENERAL_TASK_TIMEOUT_SECONDS = 60;
    private static final long PLAN_TASK_TIMEOUT_SECONDS = 90;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ChatDispatchService chatDispatchService;
    @Autowired
    @Qualifier("generalChatTaskExecutor")
    private ThreadPoolTaskExecutor generalChatTaskExecutor;
    @Autowired
    @Qualifier("planTaskExecutor")
    private ThreadPoolTaskExecutor planTaskExecutor;

    @Override
    public ChatTaskResponse submit(String memoryId, String msg, Intent intent) {
        ChatTaskResponse existingTask = getActiveTaskByMemoryId(memoryId);
        if (existingTask != null) {
            logger.info(">>> 检测到会话已有进行中的任务，直接复用 - memoryId: {}, taskId: {}, status: {}",
                    memoryId, existingTask.getTaskId(), existingTask.getStatus());
            return existingTask;
        }

        String taskId = UUID.randomUUID().toString();
        ChatTaskResponse task = new ChatTaskResponse(
                taskId,
                "PENDING",
                intent != null ? intent.name() : null,
                "任务已受理，请稍后查看结果"
        );

        try {
            ThreadPoolTaskExecutor executor = selectExecutor(intent);
            ensureExecutorCapacity(executor, intent);
            saveTask(task);
            bindActiveTask(memoryId, taskId);
            submitAsync(taskId, memoryId, msg, intent, executor);
            return task;
        } catch (RejectedExecutionException e) {
            logger.warn(">>> 异步任务提交被拒绝 - memoryId: {}, intent: {}", memoryId, intent, e);
            return new ChatTaskResponse(
                    taskId,
                    "REJECTED",
                    intent != null ? intent.name() : null,
                    Messages.ERROR_SERVICE_BUSY
            );
        } catch (RuntimeException e) {
            logger.error(">>> 异步任务提交失败 - memoryId: {}, intent: {}", memoryId, intent, e);
            return new ChatTaskResponse(
                    taskId,
                    "FAILED",
                    intent != null ? intent.name() : null,
                    Messages.ERROR_SERVICE_BUSY
            );
        }
    }

    @Override
    public ChatTaskResponse getTask(String taskId) {
        String json = stringRedisTemplate.opsForValue().get(buildTaskKey(taskId));
        if (json == null || json.isEmpty()) {
            return new ChatTaskResponse(taskId, "NOT_FOUND", null, "任务不存在或已过期");
        }
        try {
            return objectMapper.readValue(json, ChatTaskResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("任务状态解析失败", e);
        }
    }

    private void submitAsync(String taskId, String memoryId, String msg, Intent intent, ThreadPoolTaskExecutor executor) {
        CompletableFuture
                .supplyAsync(() -> processTask(taskId, memoryId, msg, intent), executor)
                .orTimeout(resolveTimeoutSeconds(intent), TimeUnit.SECONDS)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error(">>> 异步任务执行失败或超时 - taskId: {}, memoryId: {}", taskId, memoryId, throwable);
                        if (isTimeoutException(throwable)) {
                            updateTask(new ChatTaskResponse(taskId, "TIMEOUT", intentName(intent), Messages.ERROR_SERVICE_BUSY));
                        } else {
                            updateTask(new ChatTaskResponse(taskId, "FAILED", intentName(intent), Messages.ERROR_SERVICE_BUSY));
                        }
                    } else {
                        updateTask(result);
                    }
                    clearActiveTask(memoryId, taskId);
                });
    }

    private ChatTaskResponse processTask(String taskId, String memoryId, String msg, Intent intent) {
        updateTask(new ChatTaskResponse(taskId, "PROCESSING", intentName(intent), "任务处理中，请稍后查看结果"));
        ChatResponse response = chatDispatchService.handle(memoryId, msg, intent);
        return new ChatTaskResponse(taskId, "SUCCESS", response.getIntent(), response.getReply());
    }

    private ThreadPoolTaskExecutor selectExecutor(Intent intent) {
        if (intent == Intent.PLAN) {
            return planTaskExecutor;
        }
        return generalChatTaskExecutor;
    }

    private void ensureExecutorCapacity(ThreadPoolTaskExecutor executor, Intent intent) {
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        if (threadPoolExecutor == null) {
            throw new RejectedExecutionException("线程池未初始化, intent=" + intent);
        }
        if (threadPoolExecutor.getQueue().remainingCapacity() <= 0
                && executor.getActiveCount() >= executor.getMaxPoolSize()) {
            throw new RejectedExecutionException("线程池已满, intent=" + intent);
        }
    }

    private long resolveTimeoutSeconds(Intent intent) {
        if (intent == Intent.PLAN) {
            return PLAN_TASK_TIMEOUT_SECONDS;
        }
        return GENERAL_TASK_TIMEOUT_SECONDS;
    }

    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ChatTaskResponse getActiveTaskByMemoryId(String memoryId) {
        String taskId = stringRedisTemplate.opsForValue().get(buildMemoryActiveTaskKey(memoryId));
        if (taskId == null || taskId.isEmpty()) {
            return null;
        }
        ChatTaskResponse task = getTask(taskId);
        if ("NOT_FOUND".equals(task.getStatus()) || isTerminalStatus(task.getStatus())) {
            clearActiveTask(memoryId, taskId);
            return null;
        }
        return task;
    }

    private boolean isTerminalStatus(String status) {
        return "SUCCESS".equals(status)
                || "FAILED".equals(status)
                || "TIMEOUT".equals(status)
                || "REJECTED".equals(status);
    }

    private void bindActiveTask(String memoryId, String taskId) {
        stringRedisTemplate.opsForValue().set(buildMemoryActiveTaskKey(memoryId), taskId, ACTIVE_TASK_TTL);
    }

    private void clearActiveTask(String memoryId, String taskId) {
        String key = buildMemoryActiveTaskKey(memoryId);
        String currentTaskId = stringRedisTemplate.opsForValue().get(key);
        if (taskId.equals(currentTaskId)) {
            stringRedisTemplate.delete(key);
        }
    }

    private void saveTask(ChatTaskResponse task) {
        updateTask(task);
    }

    private void updateTask(ChatTaskResponse task) {
        try {
            stringRedisTemplate.opsForValue().set(
                    buildTaskKey(task.getTaskId()),
                    objectMapper.writeValueAsString(task),
                    TASK_TTL
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("任务状态序列化失败", e);
        }
    }

    private String buildTaskKey(String taskId) {
        return TASK_KEY_PREFIX + taskId;
    }

    private String buildMemoryActiveTaskKey(String memoryId) {
        return MEMORY_ACTIVE_TASK_KEY_PREFIX + memoryId;
    }

    private String intentName(Intent intent) {
        return intent != null ? intent.name() : null;
    }
}
