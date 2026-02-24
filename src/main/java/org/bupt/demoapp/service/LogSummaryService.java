package org.bupt.demoapp.service;

import org.bupt.demoapp.dto.ChatResponse;

/**
 * 健康总结服务
 */
public interface LogSummaryService {
    /**
     * 生成健康总结
     * 
     * @param memoryId 会话标识
     * @param msg 用户的总结请求
     * @return 总结响应
     */
    ChatResponse summarize(String memoryId, String msg);
}
