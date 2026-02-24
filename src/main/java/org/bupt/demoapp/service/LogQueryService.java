package org.bupt.demoapp.service;

import org.bupt.demoapp.dto.ChatResponse;

public interface LogQueryService {
    ChatResponse queryChat(String memoryId,String msg);

}
