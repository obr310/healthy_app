package org.bupt.demoapp.service;

/**
 * 用户可见的聊天历史记录服务接口
 * 用于单独存储用户输入和LLM回复（不包括提示词、意图识别结果等）
 */
public interface SaveChatHistoryService {
    
    /**
     * 保存用户消息
     * @param memoryId 会话标识（格式：userId:conversationId）
     * @param content 用户输入的消息内容
     */
    void saveUserMessage(String memoryId, String content);
    
    /**
     * 保存AI回复消息
     * @param memoryId 会话标识（格式：userId:conversationId）
     * @param content AI生成的回复内容（用户可见的部分）
     */
    void saveAiMessage(String memoryId, String content);
}
