package org.bupt.demoapp.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * AI服务：健康总结生成
 * 
 * 根据日志数据生成健康总结，支持单批生成和多批合并。
 */
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel"
)
public interface SummaryGenerationService {

    /**
     * 生成单批日志的总结
     * 
     * @param currentDate 当前日期（用于参考）
     * @param timeRange 时间范围描述
     * @param totalCount 总记录数
     * @param logs 日志内容
     * @param query 用户的查询问题
     * @return 健康总结
     */
    @SystemMessage(fromResource = "prompts/summary_generation.txt")
    String generateSummary(
        @V("currentDate") String currentDate,
        @V("timeRange") String timeRange,
        @V("totalCount") int totalCount,
        @V("logs") String logs,
        @UserMessage String query
    );

    /**
     * 合并多个子总结
     * 
     * @param currentDate 当前日期（用于参考）
     * @param timeRange 时间范围描述
     * @param totalCount 总记录数
     * @param subSummaries 子总结内容
     * @param query 用户的查询问题
     * @return 合并后的完整总结
     */
    @SystemMessage(fromResource = "prompts/summary_merge.txt")
    String mergeSummaries(
        @V("currentDate") String currentDate,
        @V("timeRange") String timeRange,
        @V("totalCount") int totalCount,
        @V("subSummaries") String subSummaries,
        @UserMessage String query
    );
}
