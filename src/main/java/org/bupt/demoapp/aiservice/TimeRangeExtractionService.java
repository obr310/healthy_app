package org.bupt.demoapp.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * AI服务：时间范围提取
 * 
 * 从用户的查询语句中提取时间范围，用于 Milvus 的 event_date 过滤。
 */
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel"
)
public interface TimeRangeExtractionService {

    /**
     * 从用户查询中提取时间范围
     * 
     * @param currentDate 当前日期（格式：yyyy-MM-dd）
     * @param query 用户的查询语句
     * @return 时间范围（格式：startDate,endDate 或 "无"）
     */
    @SystemMessage(fromResource = "prompts/time_range_extraction.txt")
    String extractTimeRange(
        @V("currentDate") String currentDate,
        @UserMessage String query
    );
}
