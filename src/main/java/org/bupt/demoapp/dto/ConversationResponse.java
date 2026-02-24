package org.bupt.demoapp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * 会话信息响应
 */
public class ConversationResponse {
    private Long conversationId;      // 会话ID
    private String title;             // 会话标题
    private String lastMessage;       // 最后一条消息
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;  // 最后更新时间

    public ConversationResponse() {}

    public ConversationResponse(Long conversationId, String title, String lastMessage, LocalDateTime timestamp) {
        this.conversationId = conversationId;
        this.title = title;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
