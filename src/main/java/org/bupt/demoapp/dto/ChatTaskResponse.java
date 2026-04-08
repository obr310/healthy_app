package org.bupt.demoapp.dto;

public class ChatTaskResponse {
    private String taskId;
    private String status;
    private String intent;
    private String reply;

    public ChatTaskResponse() {
    }

    public ChatTaskResponse(String taskId, String status, String intent, String reply) {
        this.taskId = taskId;
        this.status = status;
        this.intent = intent;
        this.reply = reply;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }
}
