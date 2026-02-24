package org.bupt.demoapp.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

//用户的一条聊天日志
public class ChatLog {
    private Long logId;
    private String userId;
    private String memoryId;
    private String rawText;
    private String msg;
    private String intent;
    private LocalDateTime createTime;
    // 事件实际发生的日期
    private LocalDate eventDate;

    public Long getLogId() {
        return logId;
    }
    public void setLogId(Long logId) {
        this.logId = logId;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getMemoryId() {
        return memoryId;
    }
    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }
    public String getRawText() {
        return rawText;
    }
    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
    public String getMsg() {
        return msg;
    }
    public void setMsg(String msg) {
        this.msg = msg;
    }
    public String getIntent() {
        return intent;
    }
    public void setIntent(String intent) {
        this.intent = intent;
    }
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    public LocalDate getEventDate() {
        return eventDate;
    }
    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }
}
