package org.bupt.demoapp.common;

import org.springframework.stereotype.Component;

@Component
public class MemoryIds {
    /**
     * 构造memoryId
     * key:"chat:conversation:"+userId
     * value:conversationId,从0开始redis自增
     * @param userId
     * @param conversationId
     * @return
     */
    public String buildMemoryId(Long userId,Long conversationId) {
        return userId + ":" + conversationId;
    }

    /**
     * 从memoryId中获取userId
     * @param memoryId
     * @return
     */
    public long extractUserId(String memoryId) {
        String[] parts=memoryId.split(":");
        if(parts.length!=2){
            throw new IllegalArgumentException("Invalid memory id: " + memoryId);
        }
        return  Long.parseLong(parts[0]);
    }

    /**
     * 从memoryId中获取conversationId
     * @param memoryId
     * @return
     */
    public long extractConversationId(String memoryId) {
        String[] parts=memoryId.split(":");
        if(parts.length!=2){
            throw new IllegalArgumentException("Invalid memory id: " + memoryId);
        }
        return Long.parseLong(parts[1]);
    }

}
