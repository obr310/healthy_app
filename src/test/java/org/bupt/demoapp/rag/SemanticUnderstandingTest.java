package org.bupt.demoapp.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.bupt.demoapp.entity.ChatLog;
import org.bupt.demoapp.mapper.LogMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 测试方法 4: 语义理解测试
 * 
 * 目的: 验证向量检索能够理解不同表述方式的相同语义
 * 
 * 测试原理:
 * - 同一件事，用户可以用不同的方式表达
 * - 传统关键词匹配：必须包含相同的词才能匹配
 * - 语义向量检索：理解意思，即使用词不同也能匹配
 * 
 * 测试方法:
 * 1. 准备9条健康日志（原始表述）
 * 2. 对每条日志设计3种不同的查询方式（同义改写）
 * 3. 验证每种查询方式都能检索到对应的日志
 * 4. 计算语义理解成功率 = 成功检索数 / 总查询数
 * 
 * 预期结果: 成功率 ≥ 90% (27个查询中至少24个成功)
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SemanticUnderstandingTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private MilvusEmbeddingStore userLogEmbeddingStore;

    @Autowired
    private LogMapper logMapper;

    private static final String TEST_USER_ID = "888888";
    private static final int TOP_K = 3;
    private static final double MIN_SCORE = 0.5;

    // 测试数据: 9条健康日志（原始表述）
    private static final List<TestLog> TEST_LOGS = Arrays.asList(
        new TestLog(101L, "I ate an apple and a banana for breakfast", 
            LocalDate.of(2026, 2, 20), "diet"),
        
        new TestLog(102L, "Went to bed at 11pm and woke up at 7am, slept for 8 hours", 
            LocalDate.of(2026, 2, 20), "sleep"),
        
        new TestLog(103L, "Ran 5 kilometers in the park this morning", 
            LocalDate.of(2026, 2, 20), "exercise"),
        
        new TestLog(104L, "Feeling stressed because of work deadline", 
            LocalDate.of(2026, 2, 20), "mood"),
        
        new TestLog(105L, "Drank 8 glasses of water throughout the day", 
            LocalDate.of(2026, 2, 20), "diet"),
        
        new TestLog(106L, "Did 30 minutes of yoga and stretching exercises", 
            LocalDate.of(2026, 2, 20), "exercise"),
        
        new TestLog(107L, "Had a headache in the afternoon, took some rest", 
            LocalDate.of(2026, 2, 20), "health"),
        
        new TestLog(108L, "Ate grilled chicken with vegetables and brown rice for dinner", 
            LocalDate.of(2026, 2, 20), "diet"),
        
        new TestLog(109L, "Went swimming for 45 minutes at the gym", 
            LocalDate.of(2026, 2, 20), "exercise")
    );

    // 测试查询: 每条日志对应3种不同的查询方式（同义改写）
    private static final List<SemanticQuery> SEMANTIC_QUERIES = Arrays.asList(
        // 日志 101: "I ate an apple and a banana for breakfast"
        new SemanticQuery(101L, "What fruits did I eat?", "水果相关查询"),
        new SemanticQuery(101L, "What did I have for my morning meal?", "早餐相关查询"),
        new SemanticQuery(101L, "What food did I consume in the morning?", "食物摄入查询"),
        
        // 日志 102: "Went to bed at 11pm and woke up at 7am, slept for 8 hours"
        new SemanticQuery(102L, "How long did I sleep last night?", "睡眠时长查询"),
        new SemanticQuery(102L, "What time did I go to sleep?", "就寝时间查询"),
        new SemanticQuery(102L, "How was my rest last night?", "休息情况查询"),
        
        // 日志 103: "Ran 5 kilometers in the park this morning"
        new SemanticQuery(103L, "Did I do any running today?", "跑步活动查询"),
        new SemanticQuery(103L, "How far did I jog?", "跑步距离查询"),
        new SemanticQuery(103L, "What cardio exercise did I do?", "有氧运动查询"),
        
        // 日志 104: "Feeling stressed because of work deadline"
        new SemanticQuery(104L, "How am I feeling emotionally?", "情绪状态查询"),
        new SemanticQuery(104L, "Am I experiencing any anxiety?", "焦虑相关查询"),
        new SemanticQuery(104L, "What's causing me pressure?", "压力来源查询"),
        
        // 日志 105: "Drank 8 glasses of water throughout the day"
        new SemanticQuery(105L, "How much water did I drink?", "饮水量查询"),
        new SemanticQuery(105L, "What beverages did I have?", "饮品相关查询"),
        new SemanticQuery(105L, "Did I stay hydrated today?", "水分补充查询"),
        
        // 日志 106: "Did 30 minutes of yoga and stretching exercises"
        new SemanticQuery(106L, "What flexibility training did I do?", "柔韧性训练查询"),
        new SemanticQuery(106L, "Did I do any yoga practice?", "瑜伽练习查询"),
        new SemanticQuery(106L, "What stretching activities did I perform?", "拉伸活动查询"),
        
        // 日志 107: "Had a headache in the afternoon, took some rest"
        new SemanticQuery(107L, "Did I experience any pain today?", "疼痛相关查询"),
        new SemanticQuery(107L, "Was I feeling unwell?", "不适症状查询"),
        new SemanticQuery(107L, "Did I have any health issues?", "健康问题查询"),
        
        // 日志 108: "Ate grilled chicken with vegetables and brown rice for dinner"
        new SemanticQuery(108L, "What did I eat for my evening meal?", "晚餐相关查询"),
        new SemanticQuery(108L, "What protein did I consume?", "蛋白质摄入查询"),
        new SemanticQuery(108L, "What was my dinner menu?", "晚餐菜单查询"),
        
        // 日志 109: "Went swimming for 45 minutes at the gym"
        new SemanticQuery(109L, "Did I go to the pool today?", "游泳活动查询"),
        new SemanticQuery(109L, "What water sports did I do?", "水上运动查询"),
        new SemanticQuery(109L, "How long did I swim?", "游泳时长查询")
    );

    private static List<Long> insertedLogIds = new ArrayList<>();

    @BeforeAll
    static void setupTestData(@Autowired LogMapper logMapper,
                              @Autowired EmbeddingModel embeddingModel,
                              @Autowired MilvusEmbeddingStore userLogEmbeddingStore) throws Exception {
        System.out.println("\n========================================");
        System.out.println("准备语义理解测试数据");
        System.out.println("========================================\n");

        // 1. 清理旧数据
        System.out.println("清理旧的测试数据...");
        try {
            List<Long> oldTestIds = new ArrayList<>();
            for (long i = 101; i <= 109; i++) {
                oldTestIds.add(i);
            }
            logMapper.deleteByLogIds(oldTestIds);
            System.out.println("✓ 已清理MySQL中的旧测试数据");
        } catch (Exception e) {
            System.out.println("⚠ MySQL清理跳过: " + e.getMessage());
        }

        // 2. 插入MySQL数据
        System.out.println("\n插入测试日志到MySQL:");
        for (TestLog testLog : TEST_LOGS) {
            ChatLog chatLog = new ChatLog();
            chatLog.setLogId(testLog.logId);
            chatLog.setUserId(TEST_USER_ID);
            chatLog.setMemoryId(TEST_USER_ID + ":0");
            chatLog.setRawText(testLog.content);
            chatLog.setIntent("RECORD");
            chatLog.setEventDate(testLog.eventDate);
            chatLog.setCreateTime(LocalDateTime.now());

            logMapper.insertChatLog(chatLog);
            insertedLogIds.add(testLog.logId);
            
            System.out.println("  ✓ [" + testLog.logId + "] " + 
                testLog.content.substring(0, Math.min(60, testLog.content.length())) + "...");
        }

        // 3. 向量化并存入Milvus
        System.out.println("\n向量化并存入Milvus:");
        for (TestLog testLog : TEST_LOGS) {
            Embedding embedding = embeddingModel.embed(testLog.content).content();
            TextSegment segment = TextSegment.from(testLog.content);
            segment.metadata().put("log_id", String.valueOf(testLog.logId));
            segment.metadata().put("memory_id", TEST_USER_ID + ":0");
            segment.metadata().put("user_id", TEST_USER_ID);
            segment.metadata().put("event_date", testLog.eventDate.atStartOfDay()
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            segment.metadata().put("category", testLog.category);

            userLogEmbeddingStore.add(embedding, segment);
        }
        System.out.println("  ✓ 已向量化 " + TEST_LOGS.size() + " 条日志\n");

        // 等待Milvus索引完成
        Thread.sleep(2000);
        System.out.println("测试数据准备完成\n");
    }

    @Test
    @Order(1)
    @DisplayName("语义理解测试 - 主测试")
    void testSemanticUnderstanding() {
        System.out.println("\n========================================");
        System.out.println("开始执行语义理解测试");
        System.out.println("========================================\n");

        int totalQueries = SEMANTIC_QUERIES.size();
        int successfulQueries = 0;
        List<QueryResult> results = new ArrayList<>();

        // 按日志分组统计
        Map<Long, LogQueryStats> logStats = new HashMap<>();
        for (TestLog log : TEST_LOGS) {
            logStats.put(log.logId, new LogQueryStats(log.content));
        }

        // 执行每个查询
        for (int i = 0; i < SEMANTIC_QUERIES.size(); i++) {
            SemanticQuery query = SEMANTIC_QUERIES.get(i);
            
            if (i % 3 == 0) {
                System.out.println("测试日志 " + query.expectedLogId + ":");
                System.out.println("  原文: " + getLogContent(query.expectedLogId));
                System.out.println();
            }

            System.out.println("  查询 " + (i % 3 + 1) + "/3: " + query.question);
            System.out.println("  类型: " + query.queryType);

            // 执行检索
            SearchResult searchResult = performSearch(query.question);
            
            // 判断是否成功检索到目标日志
            boolean success = searchResult.retrievedLogIds.contains(query.expectedLogId);
            
            if (success) {
                successfulQueries++;
                logStats.get(query.expectedLogId).successCount++;
            }
            logStats.get(query.expectedLogId).totalCount++;

            QueryResult result = new QueryResult(
                query.expectedLogId,
                query.question,
                query.queryType,
                searchResult.retrievedLogIds,
                searchResult.topScore,
                success
            );
            results.add(result);

            System.out.println("  检索到: " + searchResult.retrievedLogIds.size() + " 条");
            System.out.println("  最高分: " + String.format("%.3f", searchResult.topScore));
            System.out.println("  结果: " + (success ? "✅ 成功" : "❌ 失败"));
            
            if (!success && !searchResult.retrievedLogIds.isEmpty()) {
                System.out.println("  实际检索到: " + searchResult.retrievedLogIds);
            }
            System.out.println();
        }

        // 计算成功率
        double successRate = totalQueries > 0 
            ? (double) successfulQueries / totalQueries 
            : 0.0;

        // 输出测试报告
        printTestReport(results, logStats, totalQueries, successfulQueries, successRate);

        // 断言: 成功率应该 ≥ 90%
        Assertions.assertTrue(successRate >= 0.90,
            String.format("语义理解成功率 %.1f%% 低于目标值 90%%", successRate * 100));
    }

    /**
     * 执行向量检索
     */
    private SearchResult performSearch(String query) {
        try {
            // 1. 向量化查询
            Embedding embedding = embeddingModel.embed(query).content();

            // 2. 构建过滤条件
            Filter filter = MetadataFilterBuilder.metadataKey("user_id")
                .isEqualTo(TEST_USER_ID);

            // 3. 执行检索
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(TOP_K)
                .minScore(MIN_SCORE)
                .filter(filter)
                .build();

            EmbeddingSearchResult<TextSegment> result = userLogEmbeddingStore.search(request);

            // 4. 提取log_id和分数
            List<Long> logIds = result.matches().stream()
                .map(match -> {
                    String logIdStr = match.embedded().metadata().getString("log_id");
                    return logIdStr != null ? Long.parseLong(logIdStr) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            double topScore = result.matches().isEmpty() ? 0.0 : result.matches().get(0).score();

            return new SearchResult(logIds, topScore);

        } catch (Exception e) {
            System.err.println("检索失败: " + e.getMessage());
            return new SearchResult(Collections.emptyList(), 0.0);
        }
    }

    /**
     * 获取日志内容
     */
    private String getLogContent(Long logId) {
        return TEST_LOGS.stream()
            .filter(log -> log.logId.equals(logId))
            .map(log -> log.content)
            .findFirst()
            .orElse("(未找到)");
    }

    /**
     * 打印测试报告
     */
    private void printTestReport(List<QueryResult> results,
                                 Map<Long, LogQueryStats> logStats,
                                 int totalQueries,
                                 int successfulQueries,
                                 double successRate) {
        System.out.println("\n========================================");
        System.out.println("语义理解测试报告");
        System.out.println("========================================\n");

        System.out.println("测试配置:");
        System.out.println("  - 测试日志数: " + TEST_LOGS.size());
        System.out.println("  - 每日志查询数: 3 种不同表述");
        System.out.println("  - 总查询数: " + totalQueries);
        System.out.println("  - 检索返回: Top-" + TOP_K);
        System.out.println("  - 最低相似度: " + MIN_SCORE);
        System.out.println();

        System.out.println("按日志统计:");
        System.out.println("┌────────┬──────────┬──────────┬──────────┐");
        System.out.println("│ 日志ID │ 查询数   │ 成功数   │ 成功率   │");
        System.out.println("├────────┼──────────┼──────────┼──────────┤");
        
        for (TestLog log : TEST_LOGS) {
            LogQueryStats stats = logStats.get(log.logId);
            double rate = stats.totalCount > 0 
                ? (double) stats.successCount / stats.totalCount 
                : 0.0;
            System.out.printf("│ %-6d │ %-8d │ %-8d │ %6.1f%% │%n",
                log.logId, stats.totalCount, stats.successCount, rate * 100);
        }
        
        System.out.println("└────────┴──────────┴──────────┴──────────┘");
        System.out.println();

        System.out.println("失败的查询:");
        List<QueryResult> failedQueries = results.stream()
            .filter(r -> !r.success)
            .collect(Collectors.toList());
        
        if (failedQueries.isEmpty()) {
            System.out.println("  (无)");
        } else {
            for (int i = 0; i < failedQueries.size(); i++) {
                QueryResult r = failedQueries.get(i);
                System.out.println("  " + (i + 1) + ". 日志 " + r.expectedLogId + " - " + r.queryType);
                System.out.println("     查询: " + r.question);
                System.out.println("     原文: " + getLogContent(r.expectedLogId).substring(0, 
                    Math.min(60, getLogContent(r.expectedLogId).length())) + "...");
                System.out.println("     最高分: " + String.format("%.3f", r.topScore));
            }
        }
        System.out.println();

        System.out.println("语义理解能力分析:");
        
        // 统计不同查询类型的成功率
        Map<String, Integer> typeSuccess = new HashMap<>();
        Map<String, Integer> typeTotal = new HashMap<>();
        
        for (QueryResult r : results) {
            typeTotal.put(r.queryType, typeTotal.getOrDefault(r.queryType, 0) + 1);
            if (r.success) {
                typeSuccess.put(r.queryType, typeSuccess.getOrDefault(r.queryType, 0) + 1);
            }
        }
        
        System.out.println("  不同表述方式的理解能力:");
        for (Map.Entry<String, Integer> entry : typeTotal.entrySet()) {
            String type = entry.getKey();
            int total = entry.getValue();
            int success = typeSuccess.getOrDefault(type, 0);
            double rate = total > 0 ? (double) success / total : 0.0;
            System.out.println("    - " + type + ": " + success + "/" + total + 
                " (" + String.format("%.1f%%", rate * 100) + ")");
        }
        System.out.println();

        System.out.println("总体统计:");
        System.out.println("  - 总查询数: " + totalQueries);
        System.out.println("  - 成功检索: " + successfulQueries);
        System.out.println("  - 失败检索: " + (totalQueries - successfulQueries));
        System.out.println("  - 成功率: " + String.format("%.1f%%", successRate * 100));
        System.out.println();

        String status = successRate >= 0.90 ? "✅ 通过" : "❌ 未通过";
        System.out.println("测试结论: " + status);
        System.out.println("  目标成功率: ≥ 90%");
        System.out.println("  实际成功率: " + String.format("%.1f%%", successRate * 100));
        System.out.println();

        if (successRate >= 0.90) {
            System.out.println("✨ 语义检索优势:");
            System.out.println("  - 用户不需要记住系统的固定词汇");
            System.out.println("  - 可以用自然语言自由表达");
            System.out.println("  - 比关键词匹配更智能、更灵活");
            System.out.println();
        }
    }

    @AfterAll
    static void cleanupTestData(@Autowired LogMapper logMapper) {
        System.out.println("\n========================================");
        System.out.println("清理测试数据");
        System.out.println("========================================\n");

        if (!insertedLogIds.isEmpty()) {
            logMapper.deleteByLogIds(insertedLogIds);
            System.out.println("✓ 已清理 MySQL 测试数据");
        }

        System.out.println("✓ Milvus 测试数据已隔离(通过user_id=" + TEST_USER_ID + ")\n");
    }

    // ==================== 辅助类 ====================

    static class TestLog {
        final Long logId;
        final String content;
        final LocalDate eventDate;
        final String category;

        TestLog(Long logId, String content, LocalDate eventDate, String category) {
            this.logId = logId;
            this.content = content;
            this.eventDate = eventDate;
            this.category = category;
        }
    }

    static class SemanticQuery {
        final Long expectedLogId;
        final String question;
        final String queryType;

        SemanticQuery(Long expectedLogId, String question, String queryType) {
            this.expectedLogId = expectedLogId;
            this.question = question;
            this.queryType = queryType;
        }
    }

    static class SearchResult {
        final List<Long> retrievedLogIds;
        final double topScore;

        SearchResult(List<Long> retrievedLogIds, double topScore) {
            this.retrievedLogIds = retrievedLogIds;
            this.topScore = topScore;
        }
    }

    static class QueryResult {
        final Long expectedLogId;
        final String question;
        final String queryType;
        final List<Long> retrievedLogIds;
        final double topScore;
        final boolean success;

        QueryResult(Long expectedLogId, String question, String queryType,
                   List<Long> retrievedLogIds, double topScore, boolean success) {
            this.expectedLogId = expectedLogId;
            this.question = question;
            this.queryType = queryType;
            this.retrievedLogIds = retrievedLogIds;
            this.topScore = topScore;
            this.success = success;
        }
    }

    static class LogQueryStats {
        final String content;
        int totalCount = 0;
        int successCount = 0;

        LogQueryStats(String content) {
            this.content = content;
        }
    }
}
