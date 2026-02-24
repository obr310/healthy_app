package org.bupt.demoapp.service;

import org.bupt.demoapp.dto.ChatResponse;

public interface QAService {
    /**
     * 对用户关于健康知识的疑问做出回答
     *
     * @param memoryId
     * @param msg
     * @return
     */
    ChatResponse heathQA(String memoryId,String msg);
}
