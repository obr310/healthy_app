package org.bupt.demoapp.serviceImp;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.bupt.demoapp.aiservice.ReplyGenerationService;
import org.bupt.demoapp.common.MemoryIds;
import org.bupt.demoapp.common.Messages;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.service.QAService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 健康知识问答服务实现
 * 
 * 使用双 collection 检索策略：
 * 1. 从 knowledge_base_vectors 检索医学知识库
 * 2. 从 chat_log_vectors 检索用户个人日志
 * 3. 合并两部分内容，调用 LLM 生成个性化回答
 */
@Service
public class QAServiceImp implements QAService {
    private static final Logger log = LoggerFactory.getLogger(QAServiceImp.class);

 //召回参数
    private static final int KNOWLEDGE_MAX_RESULTS = 3;
    private static final double KNOWLEDGE_MIN_SCORE = 0.5;
    

    private static final int USER_LOG_MAX_RESULTS = 5;
    private static final double USER_LOG_MIN_SCORE = 0.3;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("knowledgeBaseEmbeddingStore")
    private MilvusEmbeddingStore knowledgeBaseEmbeddingStore;

    @Autowired
    @Qualifier("userLogEmbeddingStore")
    private MilvusEmbeddingStore userLogEmbeddingStore;

    @Autowired
    private ReplyGenerationService replyGenerationService;

    @Autowired
    private MemoryIds memoryIds;

    @Override
    public ChatResponse heathQA(String memoryId, String msg) {
        log.info(">>> QA 请求 - memoryId: {}, msg: {}", memoryId, msg);
        long startTime = System.currentTimeMillis();

        try {
            //生成查询向量
            Embedding queryEmbedding = embeddingModel.embed(msg).content();
            log.info(">>> 查询向量生成完成 - 维度: {}", queryEmbedding.dimension());

            //并行检索知识库和用户日志
            String knowledgeContext = searchKnowledgeBase(queryEmbedding);
            String userLogsContext = searchUserLogs(queryEmbedding, memoryId);

            log.info(">>> 知识库召回: {} 字符", knowledgeContext.length());
            log.info(">>> 用户日志召回: {} 字符", userLogsContext.length());

            // 调用 LLM 生成回答
            String reply = replyGenerationService.generateQAReply(msg, knowledgeContext, userLogsContext);

            long duration = System.currentTimeMillis() - startTime;
            log.info(">>> QA 完成 - 耗时: {}ms", duration);

            String qaId = "QA-" + System.currentTimeMillis();
            return new ChatResponse(qaId, Intent.QA.name(), reply, false, false);

        } catch (Exception e) {
            log.error(">>> QA 处理失败 - memoryId: {}, msg: {}", memoryId, msg, e);
            return errorResponse(Messages.QA_PROCESSING_ERROR);
        }
    }

    /**
     * 从知识库 collection 检索相关医学知识
     */
    private String searchKnowledgeBase(Embedding queryEmbedding) {
        try {
            // 知识库检索：该 collection 全是知识库数据，无需 filter
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(KNOWLEDGE_MAX_RESULTS)
                    .minScore(KNOWLEDGE_MIN_SCORE)
                    .build();

            EmbeddingSearchResult<TextSegment> result = knowledgeBaseEmbeddingStore.search(request);
            List<EmbeddingMatch<TextSegment>> matches = result.matches();

            log.info(">>> 知识库检索成功 - 召回: {} 条", matches.size());

            if (matches.isEmpty()) {
                return Messages.NO_RELEVANT_KNOWLEDGE;
            }

            // 构建知识库上下文
            return matches.stream()
                    .map(match -> match.embedded().text())
                    .collect(Collectors.joining("\n\n---\n\n"));

        } catch (Exception e) {
            log.error(">>> 知识库检索失败", e);
            return Messages.KNOWLEDGE_SEARCH_FAILED;
        }
    }

    /**
     * 从用户日志 collection 检索相关个人健康记录
     */
    private String searchUserLogs(Embedding queryEmbedding, String memoryId) {
        try {
            String userId = String.valueOf(memoryIds.extractUserId(memoryId));

            // 用户日志检索：只检索当前用户的日志
            Filter filter = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(userId);

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(USER_LOG_MAX_RESULTS)
                    .minScore(USER_LOG_MIN_SCORE)
                    .filter(filter)
                    .build();

            EmbeddingSearchResult<TextSegment> result = userLogEmbeddingStore.search(request);
            List<EmbeddingMatch<TextSegment>> matches = result.matches();

            log.info(">>> 用户日志检索成功 - userId: {}, 召回: {} 条", userId, matches.size());

            if (matches.isEmpty()) {
                return Messages.NO_RELEVANT_LOGS;
            }

            // 构建用户日志上下文
            return matches.stream()
                    .map(match -> "- " + match.embedded().text())
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.error(">>> 用户日志检索失败 - memoryId: {}", memoryId, e);
            return Messages.USER_LOG_SEARCH_FAILED;
        }
    }

    private ChatResponse errorResponse(String message) {
        String qaId = "QA-" + System.currentTimeMillis();
        return new ChatResponse(qaId, Intent.QA.name(), message, false, false);
    }
}
