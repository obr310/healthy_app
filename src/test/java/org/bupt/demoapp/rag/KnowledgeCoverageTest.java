package org.bupt.demoapp.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 测试方法 2: 知识库覆盖测试
 * 
 * 目的: 验证MedlinePlus知识库能够覆盖常见健康问题
 * 
 * 测试步骤:
 * 1. 准备30个常见健康问题(营养、睡眠、运动、心理)
 * 2. 对每个问题从知识库检索相关内容
 * 3. 判断检索结果是否足够支撑回答
 * 4. 计算覆盖率 = 能回答的问题数 / 总问题数
 * 
 * 预期结果: 覆盖率 ≥ 85%
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KnowledgeCoverageTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("knowledgeBaseEmbeddingStore")
    private MilvusEmbeddingStore knowledgeBaseEmbeddingStore;

    private static final int TOP_K = 3; // 每个查询返回Top-3结果
    private static final double MIN_SCORE = 0.5; // 最低相似度阈值
    private static final int MIN_RELEVANT_DOCS = 1; // 至少需要1个相关文档才算能回答

    // 测试问题: 30个常见健康问题,分为4个类别
    private static final List<TestQuestion> TEST_QUESTIONS = Arrays.asList(
        // 营养类 (10个)
        new TestQuestion("What is a healthy diet?", "nutrition", true),
        new TestQuestion("How much protein should I eat daily?", "nutrition", true),
        new TestQuestion("What are the benefits of vitamins?", "nutrition", true),
        new TestQuestion("Is sugar bad for health?", "nutrition", true),
        new TestQuestion("What foods are high in fiber?", "nutrition", true),
        new TestQuestion("How much water should I drink per day?", "nutrition", true),
        new TestQuestion("What is the Mediterranean diet?", "nutrition", true),
        new TestQuestion("Are carbohydrates necessary?", "nutrition", true),
        new TestQuestion("What are healthy fats?", "nutrition", true),
        new TestQuestion("How to reduce sodium intake?", "nutrition", true),
        
        // 睡眠类 (8个)
        new TestQuestion("How many hours of sleep do I need?", "sleep", true),
        new TestQuestion("What causes insomnia?", "sleep", true),
        new TestQuestion("How to improve sleep quality?", "sleep", true),
        new TestQuestion("What is sleep apnea?", "sleep", true),
        new TestQuestion("Is napping good for health?", "sleep", true),
        new TestQuestion("What are sleep disorders?", "sleep", true),
        new TestQuestion("How does caffeine affect sleep?", "sleep", true),
        new TestQuestion("What is REM sleep?", "sleep", true),
        
        // 运动类 (7个)
        new TestQuestion("How much exercise do I need?", "exercise", true),
        new TestQuestion("What are the benefits of exercise?", "exercise", true),
        new TestQuestion("Is walking good exercise?", "exercise", true),
        new TestQuestion("How to start exercising?", "exercise", true),
        new TestQuestion("What is aerobic exercise?", "exercise", true),
        new TestQuestion("How to prevent exercise injuries?", "exercise", true),
        new TestQuestion("Is strength training important?", "exercise", true),
        
        // 心理健康类 (5个)
        new TestQuestion("What is depression?", "mental_health", true),
        new TestQuestion("How to manage stress?", "mental_health", true),
        new TestQuestion("What is anxiety disorder?", "mental_health", true),
        new TestQuestion("How to improve mental health?", "mental_health", true),
        new TestQuestion("What is meditation?", "mental_health", true)
    );

    @Test
    @Order(1)
    @DisplayName("知识库覆盖测试 - 主测试")
    void testKnowledgeCoverage() {
        System.out.println("\n========================================");
        System.out.println("开始执行知识库覆盖测试");
        System.out.println("========================================\n");

        int totalQuestions = TEST_QUESTIONS.size();
        int answeredQuestions = 0;
        Map<String, CategoryStats> categoryStats = new HashMap<>();
        List<QuestionResult> results = new ArrayList<>();

        // 初始化类别统计
        categoryStats.put("nutrition", new CategoryStats("营养"));
        categoryStats.put("sleep", new CategoryStats("睡眠"));
        categoryStats.put("exercise", new CategoryStats("运动"));
        categoryStats.put("mental_health", new CategoryStats("心理健康"));

        // 对每个问题执行检索
        for (int i = 0; i < TEST_QUESTIONS.size(); i++) {
            TestQuestion question = TEST_QUESTIONS.get(i);
            System.out.println("问题 " + (i + 1) + "/" + totalQuestions + ": " + question.question);
            System.out.println("  类别: " + getCategoryName(question.category));

            // 执行知识库检索
            SearchResult searchResult = searchKnowledgeBase(question.question);
            
            // 判断是否能回答
            boolean canAnswer = searchResult.relevantCount >= MIN_RELEVANT_DOCS;
            
            if (canAnswer) {
                answeredQuestions++;
            }

            // 更新类别统计
            CategoryStats stats = categoryStats.get(question.category);
            stats.total++;
            if (canAnswer) {
                stats.answered++;
            }

            QuestionResult result = new QuestionResult(
                question.question,
                question.category,
                searchResult.retrievedCount,
                searchResult.relevantCount,
                searchResult.maxScore,
                canAnswer
            );
            results.add(result);

            System.out.println("  检索到: " + searchResult.retrievedCount + " 条");
            System.out.println("  相关: " + searchResult.relevantCount + " 条");
            System.out.println("  最高分: " + String.format("%.3f", searchResult.maxScore));
            System.out.println("  能回答: " + (canAnswer ? "✓" : "✗"));
            System.out.println();
        }

        // 计算总体覆盖率
        double coverageRate = totalQuestions > 0 
            ? (double) answeredQuestions / totalQuestions 
            : 0.0;

        // 输出测试报告
        printTestReport(results, categoryStats, totalQuestions, answeredQuestions, coverageRate);

        // 断言: 覆盖率应该 ≥ 85%
        Assertions.assertTrue(coverageRate >= 0.85, 
            String.format("知识库覆盖率 %.1f%% 低于目标值 85%%", coverageRate * 100));
    }

    /**
     * 从知识库检索相关内容
     */
    private SearchResult searchKnowledgeBase(String query) {
        try {
            // 1. 向量化查询
            Embedding embedding = embeddingModel.embed(query).content();

            // 2. 执行检索(不需要过滤条件,检索所有知识库内容)
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(TOP_K)
                .minScore(MIN_SCORE)
                .build();

            EmbeddingSearchResult<TextSegment> result = knowledgeBaseEmbeddingStore.search(request);

            // 3. 统计相关文档数量
            int retrievedCount = result.matches().size();
            int relevantCount = (int) result.matches().stream()
                .filter(match -> match.score() >= MIN_SCORE)
                .count();
            
            double maxScore = result.matches().isEmpty() ? 0.0 
                : result.matches().get(0).score();

            return new SearchResult(retrievedCount, relevantCount, maxScore);

        } catch (Exception e) {
            System.err.println("检索失败: " + e.getMessage());
            return new SearchResult(0, 0, 0.0);
        }
    }

    /**
     * 打印测试报告
     */
    private void printTestReport(List<QuestionResult> results, 
                                 Map<String, CategoryStats> categoryStats,
                                 int totalQuestions, 
                                 int answeredQuestions, 
                                 double coverageRate) {
        System.out.println("\n========================================");
        System.out.println("知识库覆盖测试报告");
        System.out.println("========================================\n");

        System.out.println("测试配置:");
        System.out.println("  - 测试问题数: " + TEST_QUESTIONS.size());
        System.out.println("  - 每问题返回: Top-" + TOP_K);
        System.out.println("  - 最低相似度: " + MIN_SCORE);
        System.out.println("  - 最少相关文档: " + MIN_RELEVANT_DOCS);
        System.out.println();

        System.out.println("分类别统计:");
        System.out.println("┌──────────────┬────────┬────────┬──────────┐");
        System.out.println("│ 类别         │ 总数   │ 能回答 │ 覆盖率   │");
        System.out.println("├──────────────┼────────┼────────┼──────────┤");
        
        for (Map.Entry<String, CategoryStats> entry : categoryStats.entrySet()) {
            CategoryStats stats = entry.getValue();
            double rate = stats.total > 0 ? (double) stats.answered / stats.total : 0.0;
            System.out.printf("│ %-12s │ %-6d │ %-6d │ %6.1f%% │%n",
                stats.name, stats.total, stats.answered, rate * 100);
        }
        
        System.out.println("└──────────────┴────────┴────────┴──────────┘");
        System.out.println();

        System.out.println("无法回答的问题:");
        List<QuestionResult> unanswered = results.stream()
            .filter(r -> !r.canAnswer)
            .collect(Collectors.toList());
        
        if (unanswered.isEmpty()) {
            System.out.println("  (无)");
        } else {
            for (int i = 0; i < unanswered.size(); i++) {
                QuestionResult r = unanswered.get(i);
                System.out.println("  " + (i + 1) + ". " + r.question);
                System.out.println("     类别: " + getCategoryName(r.category) + 
                                 ", 相关文档: " + r.relevantCount + 
                                 ", 最高分: " + String.format("%.3f", r.maxScore));
            }
        }
        System.out.println();

        System.out.println("总体统计:");
        System.out.println("  - 总问题数: " + totalQuestions);
        System.out.println("  - 能回答数: " + answeredQuestions);
        System.out.println("  - 无法回答: " + (totalQuestions - answeredQuestions));
        System.out.println("  - 覆盖率: " + String.format("%.1f%%", coverageRate * 100));
        System.out.println();

        String status = coverageRate >= 0.85 ? "✅ 通过" : "❌ 未通过";
        System.out.println("测试结论: " + status);
        System.out.println("  目标覆盖率: ≥ 85%");
        System.out.println("  实际覆盖率: " + String.format("%.1f%%", coverageRate * 100));
        System.out.println();
    }

    private String getCategoryName(String category) {
        switch (category) {
            case "nutrition": return "营养";
            case "sleep": return "睡眠";
            case "exercise": return "运动";
            case "mental_health": return "心理健康";
            default: return category;
        }
    }

    // ==================== 辅助类 ====================

    static class TestQuestion {
        final String question;
        final String category;
        final boolean shouldBeAnswerable;

        TestQuestion(String question, String category, boolean shouldBeAnswerable) {
            this.question = question;
            this.category = category;
            this.shouldBeAnswerable = shouldBeAnswerable;
        }
    }

    static class SearchResult {
        final int retrievedCount;
        final int relevantCount;
        final double maxScore;

        SearchResult(int retrievedCount, int relevantCount, double maxScore) {
            this.retrievedCount = retrievedCount;
            this.relevantCount = relevantCount;
            this.maxScore = maxScore;
        }
    }

    static class QuestionResult {
        final String question;
        final String category;
        final int retrievedCount;
        final int relevantCount;
        final double maxScore;
        final boolean canAnswer;

        QuestionResult(String question, String category, int retrievedCount, 
                      int relevantCount, double maxScore, boolean canAnswer) {
            this.question = question;
            this.category = category;
            this.retrievedCount = retrievedCount;
            this.relevantCount = relevantCount;
            this.maxScore = maxScore;
            this.canAnswer = canAnswer;
        }
    }

    static class CategoryStats {
        final String name;
        int total = 0;
        int answered = 0;

        CategoryStats(String name) {
            this.name = name;
        }
    }
}



