package org.bupt.demoapp.serviceImp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.bupt.demoapp.aiservice.QueryRewriteService;
import org.bupt.demoapp.aiservice.ReplyGenerationService;
import org.bupt.demoapp.common.MemoryIds;
import org.bupt.demoapp.common.Messages;
import org.bupt.demoapp.config.KnowledgeBaseInitializer;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.service.QAService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 健康知识问答服务实现（高并发优化版）
 *
 * 数据流向：
 * 知识库路：Milvus(15) + BM25(5) → RRF融合(6) → Rerank(3) → LLM
 * 用户日志：Milvus(30) + BM25(10) → RRF融合(15) → LLM
 *
 * 并发优化点：
 * 1. 知识库 Milvus + BM25 并行召回（retrievalExecutor，4线程）
 * 2. 用户日志 Milvus + BM25 并行召回，与知识库召回同时进行（共享 retrievalExecutor）
 * 3. Rerank 批处理并发：知识库 RRF 后的候选集按 RERANK_BATCH_SIZE(5) 切分，
 *    rerankExecutor(2线程) 并发打分，汇总后全局排序取 top-K
 */
@Service
public class QAServiceImp implements QAService {
    private static final Logger log = LoggerFactory.getLogger(QAServiceImp.class);

    private static final int    KNOWLEDGE_MAX_RESULTS     = 15;
    private static final double KNOWLEDGE_MIN_SCORE       = 0.5;
    private static final int    BM25_MAX_RESULTS          = 5;
    private static final int    RRF_K                     = 60;
    private static final int    KNOWLEDGE_RRF_TOP_K       = 6;
    private static final int    RERANK_TOP_K              = 3;
    private static final int    USER_LOG_MAX_RESULTS      = 30;
    private static final double USER_LOG_MIN_SCORE        = 0.3;
    private static final int    USER_LOG_BM25_MAX_RESULTS = 10;
    private static final int    USER_LOG_RRF_TOP_K        = 15;
    private static final long   RETRIEVAL_TIMEOUT_SECONDS = 10;
    private static final long   USER_LOG_TIMEOUT_SECONDS  = 10;
    private static final long   RERANK_TIMEOUT_SECONDS    = 25;

    /**
     * Rerank 批处理参数
     * RERANK_BATCH_SIZE=5  : 单次请求文档数，平衡 Reranker 延迟与并发粒度
     * RERANK_THREAD_COUNT=2: 并发线程数
     */
    private static final int RERANK_BATCH_SIZE   = 5;
    private static final int RERANK_THREAD_COUNT = 2;

    /** 召回并发线程池（4线程）：知识库2路 + 用户日志2路同时运行 */
    private final ExecutorService retrievalExecutor =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "retrieval-pool");
                t.setDaemon(true);
                return t;
            });

    /** Rerank 批处理线程池（2线程，对应2批次并发） */
    private final ExecutorService rerankExecutor =
            Executors.newFixedThreadPool(RERANK_THREAD_COUNT, r -> {
                Thread t = new Thread(r, "rerank-pool");
                t.setDaemon(true);
                return t;
            });

    private static final String BGE_RERANK_URL   = "http://localhost:8001/api/rerank";
    private static final String BGE_RERANK_MODEL = "bge-reranker-v2-m3";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("knowledgeBaseEmbeddingStore")
    private MilvusEmbeddingStore knowledgeBaseEmbeddingStore;

    @Autowired
    @Qualifier("userLogEmbeddingStore")
    private MilvusEmbeddingStore userLogEmbeddingStore;

    @Autowired
    private KnowledgeBaseInitializer knowledgeBaseInitializer;

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private ReplyGenerationService replyGenerationService;

    @Autowired
    private MemoryIds memoryIds;

    @Autowired
    private RestTemplate restTemplate;

    // =========================================================================
    // 主流程
    // =========================================================================

    @Override
    public ChatResponse heathQA(String memoryId, String msg) {
        log.info(">>> QA 请求 - memoryId: {}, msg: {}", memoryId, msg);
        long startTime = System.currentTimeMillis();
        try {
            // 步骤1: Query 重写
            QueryRewriteResult rewrite = rewriteQuery(msg);
            log.info(">>> Query 重写完成 - q_text: {}, q_kw: {}", rewrite.qText, rewrite.qKw);

            // 步骤2: 生成查询向量
            Embedding queryEmbedding = embeddingModel.embed(rewrite.qText).content();
            log.info(">>> 查询向量生成完成 - 维度: {}", queryEmbedding.dimension());

            // 步骤3: 并行启动3路异步任务，最大化 IO 并发
            //   - 知识库 Milvus 召回
            //   - 知识库 BM25 召回
            //   - 用户日志召回（内部再并行 Milvus + BM25）
            long retrievalStart = System.currentTimeMillis();

            CompletableFuture<List<String>> kbMilvusFuture = CompletableFuture.supplyAsync(
                    () -> searchKnowledgeBaseMilvus(queryEmbedding), retrievalExecutor);
            CompletableFuture<List<String>> kbBm25Future = CompletableFuture.supplyAsync(
                    () -> searchKnowledgeBM25(rewrite.qKw), retrievalExecutor);
            CompletableFuture<String> userLogsFuture = CompletableFuture.supplyAsync(
                    () -> searchUserLogs(queryEmbedding, rewrite.qKw, memoryId), retrievalExecutor);

            // 等待知识库两路结果（10s 超时降级为空列表）
            List<String> milvusHits = getWithFallback(
                    kbMilvusFuture,
                    new ArrayList<>(),
                    "知识库Milvus召回",
                    RETRIEVAL_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            List<String> bm25Hits = getWithFallback(
                    kbBm25Future,
                    new ArrayList<>(),
                    "知识库BM25召回",
                    RETRIEVAL_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            log.info(">>> 知识库并行召回完成 - Milvus: {} 条, BM25: {} 条, 耗时: {}ms",
                    milvusHits.size(), bm25Hits.size(), System.currentTimeMillis() - retrievalStart);

            // 步骤4: RRF 融合知识库两路结果
            List<String> rrfCandidates = rrfFuseList(milvusHits, bm25Hits, KNOWLEDGE_RRF_TOP_K);
            log.info(">>> 知识库 RRF 融合后: {} 条", rrfCandidates.size());

            // 步骤5: Rerank 批处理并发精排
            List<String> rerankedChunks = rerankBatchConcurrent(rewrite.qText, rrfCandidates, RERANK_TOP_K);
            log.info(">>> Rerank 精排完成，保留: {} 条", rerankedChunks.size());

            String knowledgeContext = rerankedChunks.isEmpty()
                    ? Messages.NO_RELEVANT_KNOWLEDGE
                    : String.join("\n\n---\n\n", rerankedChunks);

            // 等待用户日志召回（10s 超时降级）
            String userLogsContext = getWithFallback(
                    userLogsFuture,
                    Messages.USER_LOG_SEARCH_FAILED,
                    "用户日志召回",
                    USER_LOG_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            log.info(">>> 知识库上下文: {} 字符", knowledgeContext.length());
            log.info(">>> 用户日志上下文: {} 字符", userLogsContext.length());

            // 步骤6: LLM 生成回答
            String reply = replyGenerationService.generateQAReply(msg, knowledgeContext, userLogsContext);

            long duration = System.currentTimeMillis() - startTime;
            log.info(">>> QA 完成 - 耗时: {}ms", duration);
            return new ChatResponse("QA-" + System.currentTimeMillis(), Intent.QA.name(), reply, false, false);

        } catch (Exception e) {
            log.error(">>> QA 处理失败 - memoryId: {}, msg: {}", memoryId, msg, e);
            return errorResponse(Messages.QA_PROCESSING_ERROR);
        }
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    /** 带超时兜底的 CompletableFuture 获取 */
    private <T> T getWithFallback(CompletableFuture<T> future, T fallback, String taskName,
                                  long timeout, TimeUnit timeUnit) {
        try {
            return future.get(timeout, timeUnit);
        } catch (Exception e) {
            log.warn(">>> {} 超时或失败，使用降级结果 - timeout: {} {}, error: {}",
                    taskName, timeout, timeUnit, e.getMessage());
            return fallback;
        }
    }

    // =========================================================================
    // 知识库召回
    // =========================================================================

    /** Milvus 向量检索知识库，返回按相似度排序的文本列表（索引0=最相关） */
    private List<String> searchKnowledgeBaseMilvus(Embedding queryEmbedding) {
        try {
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(KNOWLEDGE_MAX_RESULTS)
                    .minScore(KNOWLEDGE_MIN_SCORE)
                    .build();
            EmbeddingSearchResult<TextSegment> result = knowledgeBaseEmbeddingStore.search(request);
            List<EmbeddingMatch<TextSegment>> matches = result.matches();
            log.info(">>> Milvus 知识库检索成功 - 召回: {} 条", matches.size());
            return matches.stream().map(m -> m.embedded().text()).collect(Collectors.toList());
        } catch (Exception e) {
            log.error(">>> Milvus 知识库检索失败", e);
            return new ArrayList<>();
        }
    }

    /** BM25 关键词检索知识库，返回按 BM25 分数排序的文本列表（索引0=最相关） */
    private List<String> searchKnowledgeBM25(String qKw) {
        try {
            List<String> hits = knowledgeBaseInitializer.bm25Search(qKw, BM25_MAX_RESULTS);
            log.info(">>> BM25 知识库检索成功 - 召回: {} 条", hits.size());
            return hits;
        } catch (Exception e) {
            log.error(">>> BM25 知识库检索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 用户日志两路并行召回 + RRF 融合
     *
     * 路A: Milvus 向量检索（按 user_id metadata 过滤）
     * 路B: Elasticsearch BM25（按 user_id term 过滤）
     * 两路在 retrievalExecutor 上并发执行，与知识库召回共享线程池（4线程）。
     */
    private String searchUserLogs(Embedding queryEmbedding, String qKw, String memoryId) {
        try {
            String userId = String.valueOf(memoryIds.extractUserId(memoryId));

            // 用户日志 Milvus 路
            CompletableFuture<List<String>> logMilvusFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Filter filter = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(userId);
                    EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(USER_LOG_MAX_RESULTS)
                            .minScore(USER_LOG_MIN_SCORE)
                            .filter(filter)
                            .build();
                    EmbeddingSearchResult<TextSegment> res = userLogEmbeddingStore.search(req);
                    List<String> hits = res.matches().stream()
                            .map(m -> m.embedded().text()).collect(Collectors.toList());
                    log.info(">>> 用户日志 Milvus 检索成功 - userId: {}, 召回: {} 条", userId, hits.size());
                    return hits;
                } catch (Exception e) {
                    log.error(">>> 用户日志 Milvus 检索失败 - userId: {}", userId, e);
                    return new ArrayList<>();
                }
            }, retrievalExecutor);

            // 用户日志 BM25 路
            CompletableFuture<List<String>> logBm25Future = CompletableFuture.supplyAsync(() -> {
                try {
                    List<String> hits = knowledgeBaseInitializer
                            .bm25SearchUserLog(qKw, userId, USER_LOG_BM25_MAX_RESULTS);
                    log.info(">>> 用户日志 BM25 检索成功 - userId: {}, 召回: {} 条", userId, hits.size());
                    return hits;
                } catch (Exception e) {
                    log.error(">>> 用户日志 BM25 检索失败 - userId: {}", userId, e);
                    return new ArrayList<>();
                }
            }, retrievalExecutor);

            List<String> logMilvusHits = getWithFallback(
                    logMilvusFuture,
                    new ArrayList<>(),
                    "用户日志Milvus",
                    USER_LOG_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            List<String> logBm25Hits = getWithFallback(
                    logBm25Future,
                    new ArrayList<>(),
                    "用户日志BM25",
                    USER_LOG_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            log.info(">>> 用户日志并行召回完成 - Milvus: {} 条, BM25: {} 条",
                    logMilvusHits.size(), logBm25Hits.size());

            List<String> fused = rrfFuseList(logMilvusHits, logBm25Hits, USER_LOG_RRF_TOP_K);
            if (fused.isEmpty()) return Messages.NO_RELEVANT_LOGS;

            return fused.stream()
                    .map(line -> line.startsWith("-") ? line : "- " + line)
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.error(">>> 用户日志检索失败 - memoryId: {}", memoryId, e);
            return Messages.USER_LOG_SEARCH_FAILED;
        }
    }

    // =========================================================================
    // RRF 融合
    // =========================================================================

    /**
     * RRF（倒数排名融合）
     * 公式: score(d) = sum( 1 / (RRF_K + rank_i(d)) )，rank 从 1 开始
     */
    private List<String> rrfFuseList(List<String> hitsA, List<String> hitsB, int topK) {
        Map<String, Double> scoreMap = new LinkedHashMap<>();

        // 计算 hitsA 的 RRF 分数
        for (int i = 0; i < hitsA.size(); i++) {
            String hit = hitsA.get(i);
            double score = 1.0 / (RRF_K + i + 1);
            scoreMap.put(hit, scoreMap.getOrDefault(hit, 0.0) + score);
        }

        // 计算 hitsB 的 RRF 分数
        for (int i = 0; i < hitsB.size(); i++) {
            String hit = hitsB.get(i);
            double score = 1.0 / (RRF_K + i + 1);
            scoreMap.put(hit, scoreMap.getOrDefault(hit, 0.0) + score);
        }

        // 按分数排序，取 topK
        return scoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Rerank 批处理并发
    // =========================================================================

    /**
     * Rerank 批处理并发精排
     * 
     * 策略：
     * 1. 将候选集按 RERANK_BATCH_SIZE 切分成多个批次
     * 2. 每个批次异步提交到 rerankExecutor 进行打分
     * 3. 汇总所有批次的打分结果
     * 4. 全局排序后取 topK
     */
    private List<String> rerankBatchConcurrent(String query, List<String> candidates, int topK) {
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        long batchStart = System.currentTimeMillis();
        
        // 将候选集分批
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i += RERANK_BATCH_SIZE) {
            int end = Math.min(i + RERANK_BATCH_SIZE, candidates.size());
            batches.add(candidates.subList(i, end));
        }
        
        log.info(">>> Rerank 批处理 - 总候选数: {}, 批大小: {}, 批数: {}, 线程数: {}",
                candidates.size(), RERANK_BATCH_SIZE, batches.size(), RERANK_THREAD_COUNT);

        // 并发提交各批次任务
        List<CompletableFuture<Map<String, Double>>> batchFutures = new ArrayList<>();
        for (List<String> batch : batches) {
            CompletableFuture<Map<String, Double>> future = CompletableFuture.supplyAsync(
                    () -> rerankBatch(query, batch),
                    rerankExecutor
            );
            batchFutures.add(future);
        }

        // 汇总所有批次的打分结果
        Map<String, Double> allScores = new LinkedHashMap<>();
        for (CompletableFuture<Map<String, Double>> future : batchFutures) {
            try {
                Map<String, Double> batchScores = future.get(RERANK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                allScores.putAll(batchScores);
            } catch (Exception e) {
                log.warn(">>> Rerank 批处理超时或失败 - error: {}", e.getMessage());
            }
        }

        // 全局排序，取 topK
        List<String> result = allScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info(">>> Rerank 批处理完成 - 耗时: {}ms, 返回: {} 条",
                System.currentTimeMillis() - batchStart, result.size());
        
        return result;
    }

    /**
     * 单个批次的 Rerank 打分
     * 返回 Map<文本, 分数>
     */
    private Map<String, Double> rerankBatch(String query, List<String> batch) {
        Map<String, Double> scores = new LinkedHashMap<>();
        
        if (batch.isEmpty()) {
            return scores;
        }

        try {
            // 构建 Rerank 请求
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", BGE_RERANK_MODEL);
            requestBody.put("query", query);
            requestBody.put("documents", batch);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // 调用 Rerank 服务
            String response = restTemplate.postForObject(BGE_RERANK_URL, request, String.class);
            if (response == null) {
                throw new IllegalStateException("Rerank 服务返回空响应");
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    int index = result.get("index").asInt();
                    double score = result.get("score").asDouble();
                    if (index < batch.size()) {
                        scores.put(batch.get(index), score);
                    }
                }
            }

            log.debug(">>> Rerank 批处理 - 批大小: {}, 打分数: {}", batch.size(), scores.size());

        } catch (ResourceAccessException e) {
            log.error(">>> Rerank 批处理超时或连接失败 - batch size: {}", batch.size(), e);
            for (int i = 0; i < batch.size(); i++) {
                scores.put(batch.get(i), (double) (batch.size() - i));
            }
        } catch (Exception e) {
            log.error(">>> Rerank 批处理失败 - batch size: {}", batch.size(), e);
            // 降级：返回原始顺序的默认分数
            for (int i = 0; i < batch.size(); i++) {
                scores.put(batch.get(i), (double) (batch.size() - i));
            }
        }

        return scores;
    }

    // =========================================================================
    // Query 重写
    // =========================================================================

    private QueryRewriteResult rewriteQuery(String msg) {
        try {
            String result = queryRewriteService.rewriteQuery(msg);
            JsonNode root = objectMapper.readTree(result);
            String qText = root.get("q_text").asText(msg);
            String qKw = root.get("q_kw").asText(msg);
            return new QueryRewriteResult(qText, qKw);
        } catch (Exception e) {
            log.warn(">>> Query 重写失败，使用原始查询", e);
            return new QueryRewriteResult(msg, msg);
        }
    }

    // =========================================================================
    // 错误处理
    // =========================================================================

    private ChatResponse errorResponse(String message) {
        return new ChatResponse("ERROR-" + System.currentTimeMillis(), Intent.QA.name(), message, false, true);
    }

    // =========================================================================
    // 内部类
    // =========================================================================

    private static class QueryRewriteResult {
        String qText;
        String qKw;

        QueryRewriteResult(String qText, String qKw) {
            this.qText = qText;
            this.qKw = qKw;
        }
    }
}
