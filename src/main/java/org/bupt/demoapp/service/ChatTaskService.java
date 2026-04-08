package org.bupt.demoapp.service;

import org.bupt.demoapp.dto.ChatTaskResponse;
import org.bupt.demoapp.entity.Intent;

/**
 * 异步处理长任务服务
 */
public interface ChatTaskService {
    ChatTaskResponse submit(String memoryId, String msg, Intent intent);

    ChatTaskResponse getTask(String taskId);
}
