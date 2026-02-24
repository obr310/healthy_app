package org.bupt.demoapp.service;
import org.bupt.demoapp.dto.ChatResponse;

public interface LogRecordService {
    ChatResponse record(String memoryId, String msg);
}
