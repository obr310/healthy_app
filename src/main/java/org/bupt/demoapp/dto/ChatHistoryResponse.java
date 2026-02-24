package org.bupt.demoapp.dto;

import java.util.List;

/**
 * 聊天历史记录响应
 */
public class ChatHistoryResponse {
    private List<MessageItem> messages;  // 消息列表
    private int totalCount;              // 总消息数
    private int page;                    // 当前页码
    private int pageSize;                // 每页大小
    private boolean hasMore;             // 是否还有更多消息

    public ChatHistoryResponse() {}

    public ChatHistoryResponse(List<MessageItem> messages, int totalCount, int page, int pageSize, boolean hasMore) {
        this.messages = messages;
        this.totalCount = totalCount;
        this.page = page;
        this.pageSize = pageSize;
        this.hasMore = hasMore;
    }

    public List<MessageItem> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageItem> messages) {
        this.messages = messages;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    /**
     * 单条消息项
     */
    public static class MessageItem {
        private String role;      // USER 或 AI
        private String content;   // 消息内容
        private Long timestamp;   // 时间戳（可选）

        public MessageItem() {}

        public MessageItem(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public MessageItem(String role, String content, Long timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
