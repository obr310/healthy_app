package org.bupt.demoapp.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * AI服务：回复生成
 * 
 * 负责生成最终给用户的回复：
 * 1. 记录回复：用户记录健康日志后的确认回复
 * 2. 查询回复：根据筛选后的日志生成回复
 * 3. QA回复：基于知识库和用户日志生成健康知识问答回复
 */
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel",
    chatMemoryProvider = "chatMemoryProvider"
)
public interface ReplyGenerationService {

    /**
     * 为 RECORD 意图生成确认回复
     * 
     * @param memoryId 会话标识
     * @param userMessage 用户的健康日志内容
     * @return 友好的确认回复
     */
    @SystemMessage(fromResource = "prompts/record_reply.txt")
    String generateRecordReply(@MemoryId String memoryId, @UserMessage String userMessage);

    /**
     * 为 QUERY 意图生成查询回复
     * 
     * @param query 用户的查询语句
     * @param filteredLogs 筛选后的日志列表（已经过 QueryAnalysisService 筛选）
     * @return 友好的查询回复
     */
    @SystemMessage(fromResource = "prompts/query_reply_generation.txt")
    String generateQueryReply(
        @UserMessage String query,
        @V("filteredLogs") String filteredLogs
    );

    /**
     * 为 QA 意图生成健康知识问答回复
     * 
     * @param query 用户的健康问题
     * @param knowledgeContext 从知识库检索到的相关内容
     * @param userLogsContext 用户的相关个人日志
     * @return 基于知识库和个人日志的回答
     */
    @SystemMessage(fromResource = "prompts/qa_reply_generation.txt")
    String generateQAReply(
        @UserMessage String query,
        @V("knowledgeContext") String knowledgeContext,
        @V("userLogsContext") String userLogsContext
    );
}
