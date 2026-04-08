package org.bupt.demoapp.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.bupt.demoapp.config.KnowledgeBaseInitializer;
import org.bupt.demoapp.entity.ChatLog;
import org.bupt.demoapp.mapper.LogMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 优化流程测试 - 对比基准 RetrievalRelevanceTest
 *
 * 测试目标：验证优化后的多路召回 RAG 流程在相同数据集下的检索效果提升
 *
 * 优化流程：
 * 1. 删除 Milvus / MySQL / ES 中 TEST_USER_ID=999999 的旧测试数据
 * 2. 重新写入与 RetrievalRelevanceTest 相同的 200 条测试数据
 * 3. 针对相同 20 个查询执行优化 RAG 流程：
 *    路A: Milvus 向量检索(topK=5) + 路B: ES BM25(top=5)
 *    -> RRF 融合(k=60) -> BGE Rerank(top=6)
 * 4. 计算并对比优化前后的 Precision / Recall / F1
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OptimizedRetrievalTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private MilvusEmbeddingStore userLogEmbeddingStore;

    @Autowired
    private LogMapper logMapper;

    @Autowired
    private KnowledgeBaseInitializer knowledgeBaseInitializer;

    @Autowired
    private ElasticsearchClient esClient;

    // ==================== 常量 ====================

    private static final String TEST_USER_ID    = "999999";
    private static final int    MILVUS_TOP_K    = 5;
    private static final double MILVUS_MIN_SCORE = 0.3;
    private static final int    BM25_TOP_K      = 5;
    private static final int    RRF_K           = 60;
    private static final int    PRERANK_POOL    = 10;
    private static final int    RERANK_TOP_K    = 6;

    private static final String BGE_RERANK_URL      = "http://localhost:8001/api/rerank";
    private static final String BGE_RERANK_MODEL     = "bge-reranker-v2-m3";
    // ES 索引名常量（与 application.yaml 中 elasticsearch.user-log-index 保持一致）
    private static final String ES_USER_LOG_INDEX    = "user_log_bm25";

    private final ObjectMapper   objectMapper = new ObjectMapper();
    private final RestTemplate   restTemplate = new RestTemplate();

    // 复用 RetrievalRelevanceTest 中的数据集和查询集
    private static final List<RetrievalRelevanceTest.TestLog>   CORE_LOGS    = RetrievalRelevanceTest.CORE_LOGS;
    private static final List<RetrievalRelevanceTest.TestQuery> TEST_QUERIES = RetrievalRelevanceTest.TEST_QUERIES;

    private static final List<Long> insertedLogIds = new ArrayList<>();

    // ==================== 生命周期 ====================

    @BeforeAll
    static void setupTestData(
            @Autowired LogMapper              logMapper,
            @Autowired EmbeddingModel         embeddingModel,
            @Autowired MilvusEmbeddingStore   userLogEmbeddingStore,
            @Autowired KnowledgeBaseInitializer knowledgeBaseInitializer,
            @Autowired ElasticsearchClient    esClient
    ) throws Exception {
        System.out.println("\n========================================");
        System.out.println("[优化RAG测试] 准备测试数据");
        System.out.println("========================================\n");

        // ── 0a. 删除 Milvus 中 user_id=999999 的旧向量 ──
        System.out.println("[清理] Milvus 删除 user_id=" + TEST_USER_ID + " 旧向量...");
        try {
            Filter f = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(TEST_USER_ID);
            userLogEmbeddingStore.removeAll(f);
            System.out.println("✓ Milvus 旧向量删除完成");
        } catch (Exception e) {
            System.out.println("⚠ Milvus 清理跳过: " + e.getMessage());
        }

        // ── 0b. 删除 MySQL 旧测试数据 ──
        System.out.println("[清理] MySQL 删除旧测试数据...");
        try {
            List<Long> oldIds = new ArrayList<>();
            for (long i = 1;    i <= 50;   i++) oldIds.add(i);
            for (long i = 1001; i <= 1150; i++) oldIds.add(i);
            logMapper.deleteByLogIds(oldIds);
            System.out.println("✓ MySQL 旧数据删除完成");
        } catch (Exception e) {
            System.out.println("⚠ MySQL 清理跳过: " + e.getMessage());
        }

        // ── 0c. 删除 ES 中 user_id=999999 的文档 ──
        System.out.println("[清理] ES 删除 user_id=" + TEST_USER_ID + " 旧文档...");
        try {
            esClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(ES_USER_LOG_INDEX)
                    .query(q -> q
                            .term(t -> t.field("user_id").value(TEST_USER_ID))
                    )
            ));
            System.out.println("✓ ES 旧文档删除完成");
        } catch (Exception e) {
            System.out.println("⚠ ES 清理跳过: " + e.getMessage());
        }

        // ── 1. 重新写入 200 条测试数据 ──
        List<RetrievalRelevanceTest.TestLog> allLogs = new ArrayList<>();
        allLogs.addAll(CORE_LOGS);
        allLogs.addAll(RetrievalRelevanceTest.generateNoiseData());

        System.out.println("\n数据统计:");
        System.out.println("  核心日志 : " + CORE_LOGS.size() + " 条");
        System.out.println("  干扰日志 : " + (allLogs.size() - CORE_LOGS.size()) + " 条");
        System.out.println("  总计     : " + allLogs.size() + " 条\n");

        // 写入 MySQL
        for (RetrievalRelevanceTest.TestLog tl : allLogs) {
            ChatLog chatLog = new ChatLog();
            chatLog.setLogId(tl.logId);
            chatLog.setUserId(TEST_USER_ID);
            chatLog.setMemoryId(TEST_USER_ID + ":0");
            chatLog.setRawText(tl.content);
            chatLog.setIntent("RECORD");
            chatLog.setEventDate(tl.eventDate);
            chatLog.setCreateTime(LocalDateTime.now());
            logMapper.insertChatLog(chatLog);
            insertedLogIds.add(tl.logId);
        }
        System.out.println("✓ MySQL 写入完成: " + allLogs.size() + " 条");

        // 写入 Milvus + ES
        for (int i = 0; i < allLogs.size(); i++) {
            RetrievalRelevanceTest.TestLog tl = allLogs.get(i);
            Embedding embedding = embeddingModel.embed(tl.content).content();
            TextSegment segment = TextSegment.from(tl.content);
            segment.metadata().put("log_id",    String.valueOf(tl.logId));
            segment.metadata().put("user_id",   TEST_USER_ID);
            segment.metadata().put("memory_id", TEST_USER_ID + ":0");
            segment.metadata().put("event_date",
                    tl.eventDate.atStartOfDay(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli());
            userLogEmbeddingStore.add(embedding, segment);
            knowledgeBaseInitializer.indexUserLog(
                    String.valueOf(tl.logId), TEST_USER_ID, tl.content);
            if ((i + 1) % 50 == 0 || i == allLogs.size() - 1) {
                System.out.println("  已写入 Milvus+ES " + (i + 1) + "/" + allLogs.size() + " 条");
            }
        }
        System.out.println("✓ Milvus + ES 写入完成");

        System.out.println("\n等待 Milvus 索引构建 (3s)...");
        Thread.sleep(3000);
        System.out.println("✓ 数据准备完成\n");
    }

    @AfterAll
    static void cleanupTestData(@Autowired LogMapper logMapper) {
        System.out.println("\n[优化RAG测试] 清理 MySQL 测试数据...");
        if (!insertedLogIds.isEmpty()) {
            logMapper.deleteByLogIds(insertedLogIds);
            System.out.println("✓ MySQL 测试数据已清理 (" + insertedLogIds.size() + " 条)");
        }
        System.out.println("注: Milvus/ES 向量数据已在 @BeforeAll 清理，本次保留供后续复用\n");
    }

    // ==================== 核心测试 ====================

    @Test
    @Order(1)
    @DisplayName("优化RAG检索测试 - 多路召回 + RRF + BGE Rerank")
    void testOptimizedRetrieval() {
        System.out.println("\n========================================");
        System.out.println("[优化RAG测试] 开始执行检索测试");
        System.out.println("========================================\n");

        List<QueryResult> results = new ArrayList<>();

        for (int i = 0; i < TEST_QUERIES.size(); i++) {
            RetrievalRelevanceTest.TestQuery query = TEST_QUERIES.get(i);
            System.out.println("查询 " + (i + 1) + "/" + TEST_QUERIES.size() + ": " + query.question);

            List<Long> retrievedIds = performOptimizedSearch(query.question);

            int relevantCount = 0;
            for (Long id : retrievedIds) {
                if (query.relevantLogIds.contains(id)) relevantCount++;
            }

            QueryResult result = new QueryResult(
                    query.question, retrievedIds.size(), relevantCount,
                    retrievedIds, query.relevantLogIds);
            results.add(result);

            System.out.println("  检索: " + retrievedIds.size() + " 条, 相关: " + relevantCount
                    + " 条, 准确率: " + String.format("%.1f%%", result.getPrecision() * 100));
        }

        RetrievalStats stats = calculateStats(results);
        printTestReport(results, stats);

        // 断言: 优化后准确率应 >= 60%（高于基准 40-60%）
        System.out.println("\n[断言] 优化RAG准确率应 >= 60%");
        Assertions.assertTrue(stats.precision >= 0.60,
                String.format("准确率 %.1f%% 未达到优化目标 60%%", stats.precision * 100));
    }

    // ==================== 优化RAG流程 ====================

    /**
     * 优化RAG检索流程：
     * Milvus向量召回 + ES BM25召回 -> RRF融合 -> BGE Rerank精排
     * 返回最终选出的 log_id 列表
     */
    private List<Long> performOptimizedSearch(String queryText) {
        try {
            Embedding embedding = embeddingModel.embed(queryText).content();

            // 路A: Milvus 向量召回，同时保留 log_id 映射
            Map<String, Long> textToLogId = new LinkedHashMap<>();
            List<String> milvusTexts = searchMilvus(embedding, textToLogId);

            // 路B: ES BM25 关键词召回
            List<String> bm25Texts = searchBM25(queryText);

            System.out.println("    Milvus召回: " + milvusTexts.size()
                    + " 条, BM25召回: " + bm25Texts.size() + " 条");

            // RRF 融合
            List<String> rrfTexts = rrfFuse(milvusTexts, bm25Texts, PRERANK_POOL);
            System.out.println("    RRF融合后候选: " + rrfTexts.size() + " 条");

            // BGE Rerank 精排
            List<String> rerankedTexts = rerank(queryText, rrfTexts, RERANK_TOP_K);
            System.out.println("    Rerank后保留: " + rerankedTexts.size() + " 条");

            // 将最终文本还原为 log_id
            return resolveLogIds(rerankedTexts, embedding, textToLogId);

        } catch (Exception e) {
            System.err.println("    检索失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Milvus 向量召回，同时填充 textToLogId 映射 */
    private List<String> searchMilvus(Embedding embedding, Map<String, Long> textToLogId) {
        try {
            Filter filter = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(TEST_USER_ID);
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(MILVUS_TOP_K)
                    .minScore(MILVUS_MIN_SCORE)
                    .filter(filter)
                    .build();
            EmbeddingSearchResult<TextSegment> result = userLogEmbeddingStore.search(req);
            List<String> texts = new ArrayList<>();
            for (var match : result.matches()) {
                String text = match.embedded().text();
                String logIdStr = match.embedded().metadata().getString("log_id");
                if (logIdStr != null) {
                    textToLogId.put(text, Long.parseLong(logIdStr));
                }
                texts.add(text);
            }
            return texts;
        } catch (Exception e) {
            System.err.println("    Milvus召回失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** ES BM25 关键词召回 */
    private List<String> searchBM25(String queryText) {
        try {
            return knowledgeBaseInitializer.bm25SearchUserLog(queryText, TEST_USER_ID, BM25_TOP_K);
        } catch (Exception e) {
            System.err.println("    BM25召回失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** RRF 倒数排名融合 */
    private List<String> rrfFuse(List<String> listA, List<String> listB, int topK) {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        for (int i = 0; i < listA.size(); i++)
            scoreMap.merge(listA.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        for (int i = 0; i < listB.size(); i++)
            scoreMap.merge(listB.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** BGE Rerank 精排 */
    private List<String> rerank(String query, List<String> candidates, int topK) {
        if (candidates.isEmpty()) return candidates;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", BGE_RERANK_MODEL);
            body.put("query", query);
            body.put("documents", candidates);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String respJson = restTemplate.postForObject(
                    BGE_RERANK_URL, new HttpEntity<>(body, headers), String.class);
            JsonNode results = objectMapper.readTree(respJson).path("results");
            List<String> reranked = new ArrayList<>();
            for (JsonNode item : results) {
                int idx = item.path("index").asInt();
                if (idx >= 0 && idx < candidates.size())
                    reranked.add(candidates.get(idx));
            }
            if (reranked.size() > topK) reranked = reranked.subList(0, topK);
            return reranked;
        } catch (Exception e) {
            System.err.println("    BGE Rerank失败，回退到RRF顺序: " + e.getMessage());
            return candidates.subList(0, Math.min(topK, candidates.size()));
        }
    }

    /**
     * 将最终文本列表还原为 log_id。
     * 优先使用 Milvus 召回时缓存的 textToLogId；
     * 若文本仅由 BM25 带入，则补充一次 Milvus 精确查询以获取 log_id。
     */
    private List<Long> resolveLogIds(List<String> texts, Embedding origEmbedding,
                                     Map<String, Long> textToLogId) {
        List<Long> ids = new ArrayList<>();
        for (String text : texts) {
            if (textToLogId.containsKey(text)) {
                ids.add(textToLogId.get(text));
            } else {
                // BM25 带入的文本：用原始 embedding 补充 Milvus 查询
                try {
                    Embedding textEmb = embeddingModel.embed(text).content();
                    Filter filter = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(TEST_USER_ID);
                    EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                            .queryEmbedding(textEmb).maxResults(1).minScore(0.95).filter(filter).build();
                    EmbeddingSearchResult<TextSegment> res = userLogEmbeddingStore.search(req);
                    if (!res.matches().isEmpty()) {
                        String logIdStr = res.matches().get(0).embedded().metadata().getString("log_id");
                        if (logIdStr != null) ids.add(Long.parseLong(logIdStr));
                    }
                } catch (Exception e) {
                    System.err.println("    resolveLogId失败: " + e.getMessage());
                }
            }
        }
        return ids;
    }

    // ==================== 统计与报告 ====================

    private RetrievalStats calculateStats(List<QueryResult> results) {
        int totalRetrieved = 0, totalRelevant = 0, totalExpected = 0;
        for (QueryResult r : results) {
            totalRetrieved += r.retrievedCount;
            totalRelevant  += r.relevantCount;
            totalExpected  += r.expectedIds.size();
        }
        double precision = totalRetrieved > 0 ? (double) totalRelevant / totalRetrieved : 0.0;
        double recall    = totalExpected  > 0 ? (double) totalRelevant / totalExpected  : 0.0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
        return new RetrievalStats(totalRetrieved, totalRelevant, totalExpected, precision, recall, f1);
    }

    private void printTestReport(List<QueryResult> results, RetrievalStats stats) {
        System.out.println("\n========================================");
        System.out.println("优化RAG检索测试报告");
        System.out.println("========================================\n");
        System.out.println("检索策略: Milvus向量(top" + MILVUS_TOP_K + ") + ES BM25(top" + BM25_TOP_K
                + ") -> RRF(pool=" + PRERANK_POOL + ") -> BGERerank(top" + RERANK_TOP_K + ")");
        System.out.println("数据规模: 200条(50核心+150干扰), 查询数: " + TEST_QUERIES.size() + "\n");

        System.out.println("┌─────┬────────────────────────────┬────────┬────────┬──────────┐");
        System.out.println("│ 序号 │ 查询问题                    │ 检索数 │ 相关数 │ 准确率   │");
        System.out.println("├─────┼────────────────────────────┼────────┼────────┼──────────┤");
        for (int i = 0; i < results.size(); i++) {
            QueryResult r = results.get(i);
            String q = r.question.length() > 26 ? r.question.substring(0, 26) + "..." : r.question;
            System.out.printf("│ %-4d│ %-26s │ %-6d │ %-6d │ %6.1f%% │%n",
                    i + 1, q, r.retrievedCount, r.relevantCount, r.getPrecision() * 100);
        }
        System.out.println("└─────┴────────────────────────────┴────────┴────────┴──────────┘\n");

        System.out.println("总体指标:");
        System.out.printf("  准确率 (Precision): %.1f%%%n", stats.precision * 100);
        System.out.printf("  召回率 (Recall)   : %.1f%%%n", stats.recall    * 100);
        System.out.printf("  F1 分数           : %.1f%%%n", stats.f1        * 100);
        System.out.println();
        System.out.println("对比基准 (RetrievalRelevanceTest - Milvus topK=5):");
        System.out.println("  基准准确率预期: 40-60%");
        System.out.printf("  优化后准确率  : %.1f%%%n", stats.precision * 100);
        String verdict = stats.precision >= 0.60 ? "✅ 优化有效，超越基准" : "⚠ 优化效果不足，需进一步调整";
        System.out.println("  结论: " + verdict + "\n");
    }

    // ==================== 辅助类 ====================

    static class QueryResult {
        final String     question;
        final int        retrievedCount;
        final int        relevantCount;
        final List<Long> retrievedIds;
        final List<Long> expectedIds;

        QueryResult(String question, int retrievedCount, int relevantCount,
                    List<Long> retrievedIds, List<Long> expectedIds) {
            this.question       = question;
            this.retrievedCount = retrievedCount;
            this.relevantCount  = relevantCount;
            this.retrievedIds   = retrievedIds;
            this.expectedIds    = expectedIds;
        }

        double getPrecision() {
            return retrievedCount > 0 ? (double) relevantCount / retrievedCount : 0.0;
        }
    }

    static class RetrievalStats {
        final int    totalRetrieved;
        final int    totalRelevant;
        final int    totalExpected;
        final double precision;
        final double recall;
        final double f1;

        RetrievalStats(int totalRetrieved, int totalRelevant, int totalExpected,
                       double precision, double recall, double f1) {
            this.totalRetrieved = totalRetrieved;
            this.totalRelevant  = totalRelevant;
            this.totalExpected  = totalExpected;
            this.precision      = precision;
            this.recall         = recall;
            this.f1             = f1;
        }
    }
}
