package org.bupt.demoapp.dto;

public class ChatResponse {
    private String logId; //日志唯一标识
    private String intent; //AI识别的意图类型（如：RECORD, QUERY, QA等）
    private String reply; //LLM生成的回复消息（给用户展示的友好回复）
    private boolean mysqlStored; //是否成功存入mysql
    private boolean milvusStored; // 是否存入milvus
    
    public ChatResponse(String logId, String intent, String reply, boolean mysqlStored, boolean milvusStored) {
        this.logId = logId;
        this.intent = intent;
        this.reply = reply;
        this.mysqlStored = mysqlStored;
        this.milvusStored = milvusStored;
    }
    public ChatResponse() {}
    public String getLogId() {
        return logId;
    }
    public void setLogId(String logId) {
        this.logId = logId;
    }
    public String getIntent() {
        return intent;
    }
    public void setIntent(String intent) {
        this.intent = intent;
    }
    public boolean isMysqlStored() {
        return mysqlStored;
    }
    public void setMysqlStored(boolean mysqlStored) {
        this.mysqlStored = mysqlStored;
    }
    public boolean isMilvusStored() {
        return milvusStored;

    }
    public void setMilvusStored(boolean milvusStored) {
        this.milvusStored = milvusStored;
    }
    
    public String getReply() {
        return reply;
    }
    
    public void setReply(String reply) {
        this.reply = reply;
    }
}
