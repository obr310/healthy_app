package org.bupt.demoapp.service;

import org.bupt.demoapp.dto.ChatHistoryResponse;

/**
 * 聊天历史记录服务接口
 */
public interface GetChatHistoryService {
    /**
     * 获取会话的历史记录（分页）
     * @param memoryId 会话标识（格式：userId:conversationId）
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 历史消息列表
     */
    ChatHistoryResponse getChatHistory(String memoryId, int page, int size);
}
