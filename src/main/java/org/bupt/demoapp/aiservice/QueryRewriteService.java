package org.bupt.demoapp.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        chatMemoryProvider = "chatMemoryProvider"
)
public interface QueryRewriteService {
    /**
     * 查询重写：将用户原始问题改写成两种检索表达
     *
     * @param originalQuery 用户的原始问题
     * @return JSON 格式的重写结果，包含 q_text（语义完整版）和 q_kw（关键词版）
     */
    @SystemMessage(fromResource = "prompts/query_rewrite.txt")
    String rewriteQuery(@UserMessage String originalQuery);

}
