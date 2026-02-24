package org.bupt.demoapp.dto;

public class ChatRequest {
    private String memoryId; //会话标识
    private String msg; //用户输入
    public ChatRequest() {}
    public ChatRequest(String memoryId, String msg) {
        this.memoryId = memoryId;
        this.msg = msg;
    }
    public String getMemoryId() {
        return memoryId;
    }
    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }
    public String getMsg() {
        return msg;
    }
    public void setMsg(String msg) {
        this.msg = msg;
    }


}
