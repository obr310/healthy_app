package org.bupt.demoapp.service;
import org.bupt.demoapp.dto.ChatResponse;

public interface ChatDispatchService {
    ChatResponse handle(String memoryId, String msg);
}