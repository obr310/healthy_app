package org.bupt.demoapp.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import org.bupt.demoapp.entity.Intent;
import org.springframework.stereotype.Service;

@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel  = "openAiChatModel",
    //该服务知道历史会话
    chatMemoryProvider = "chatMemoryProvider"

)
public interface IntentService {
    @SystemMessage(fromResource = "prompts/intent_classification.txt")
    public Intent classify(@MemoryId String memoryId, @UserMessage String msg);

}
