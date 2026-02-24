package org.bupt.demoapp.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * AI服务：事件日期提取
 * 
 * 从用户的健康日志内容中提取事件实际发生的日期。
 * 用于打标签，存入MySQL的event_date字段和Milvus的metadata。
 */
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel"
)
public interface EventDateExtractionService {

    /**
     * 从日志内容中提取事件发生的日期
     * 
     * @param currentDate 当前日期（格式：yyyy-MM-dd）
     * @param logContent 用户的日志内容
     * @return 事件日期（格式：yyyy-MM-dd），如果无法确定则返回当前日期
     */
    @SystemMessage(fromResource = "prompts/event_date_extraction.txt")
    String extractEventDate(
        @V("currentDate") String currentDate,
        @UserMessage String logContent
    );
}
