package org.bupt.demoapp.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * AI服务：总结查询分析
 * 
 * 分析用户的总结请求，提取时间范围和判断是否有类别要求。
 */
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel"
)
public interface SummaryQueryAnalysisService {

    /**
     * 分析用户的总结请求
     * 
     * @param currentDate 当前日期（格式：yyyy-MM-dd）
     * @param query 用户的查询语句
     * @return 分析结果，格式：startDate,endDate,hasCategory 或 INCOMPLETE
     */
    @SystemMessage(fromResource = "prompts/summary_query_analysis.txt")
    String analyzeQuery(
        @V("currentDate") String currentDate,
        @UserMessage String query
    );
}
