package org.bupt.demoapp.service;

import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.entity.Intent;

public interface ChatDispatchService {
    ChatResponse handle(String memoryId, String msg);

    ChatResponse handle(String memoryId, String msg, Intent intent);
}
