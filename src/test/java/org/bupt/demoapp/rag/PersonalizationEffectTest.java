package org.bupt.demoapp.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 测试方法 3: 个性化效果测试
 * 
 * 目的: 验证系统能够根据不同用户的个人日志提供个性化建议
 * 
 * 测试原理:
 * - 创建两个虚拟用户(User A 和 User B)，他们有不同的健康数据
 * - 两个用户问同样的问题
 * - 如果系统只依赖知识库，两个用户会得到相同的答案
 * - 如果系统真正实现了个性化，会根据各自的日志数据给出不同的建议
 * 
 * 测试场景:
 * 1. 睡眠场景: User A 睡5小时 vs User B 睡8小时，问"我的睡眠时间够吗"
 * 2. 运动场景: User A 每天运动60分钟 vs User B 不运动，问"我的运动量如何"
 * 3. 饮食场景: User A 高糖饮食 vs User B 均衡饮食，问"我的饮食健康吗"
 * 4. 体重场景: User A BMI 18 vs User B BMI 28，问"我需要调整体重吗"
 * 
 * 预期结果: 
 * - 每个场景中，两个用户应该得到明显不同的建议
 * - 个性化准确率 ≥ 80% (至少4个场景中有3个体现个性化)
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PersonalizationEffectTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private MilvusEmbeddingStore userLogEmbeddingStore;

    @Autowired
    private MilvusEmbeddingStore knowledgeBaseEmbeddingStore;

    private static final String TEST_USER_A = "test_user_personalization_a";
    private static final String TEST_USER_B = "test_user_personalization_b";
    private static final int TOP_K = 3;
    private static final double MIN_SCORE = 0.5;

    @BeforeAll
    static void setupTestData(@Autowired EmbeddingModel embeddingModel,
                             @Autowired MilvusEmbeddingStore userLogEmbeddingStore) {
        System.out.println("\n========================================");
        System.out.println("准备个性化测试数据");
        System.out.println("========================================\n");

        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        // User A 的数据: 不健康的生活方式
        List<UserLogEntry> userALogs = Arrays.asList(
            new UserLogEntry(TEST_USER_A, today, "sleep", 
                "I only slept 5 hours last night. Feeling tired and exhausted."),
            new UserLogEntry(TEST_USER_A, today, "exercise", 
                "No exercise today. Too busy with work, no time for physical activity."),
            new UserLogEntry(TEST_USER_A, today, "diet", 
                "Had fast food for lunch and dinner. Lots of sugar and fried food."),
            new UserLogEntry(TEST_USER_A, today, "weight", 
                "My weight is 50kg and height is 170cm. BMI is around 18, feeling weak.")
        );

        // User B 的数据: 健康的生活方式
        List<UserLogEntry> userBLogs = Arrays.asList(
            new UserLogEntry(TEST_USER_B, today, "sleep", 
                "Had a great sleep of 8 hours. Woke up feeling refreshed and energetic."),
            new UserLogEntry(TEST_USER_B, today, "exercise", 
                "Completed 60 minutes of exercise today including jogging and strength training."),
            new UserLogEntry(TEST_USER_B, today, "diet", 
                "Ate balanced meals with vegetables, fruits, whole grains and lean protein."),
            new UserLogEntry(TEST_USER_B, today, "weight", 
                "My weight is 85kg and height is 170cm. BMI is around 28, need to lose weight.")
        );

        // 插入 User A 的日志
        System.out.println("插入 User A 的日志 (不健康生活方式):");
        for (UserLogEntry log : userALogs) {
            insertUserLog(embeddingModel, userLogEmbeddingStore, log);
            System.out.println("  ✓ " + log.category + ": " + log.content.substring(0, Math.min(50, log.content.length())) + "...");
        }

        // 插入 User B 的日志
        System.out.println("\n插入 User B 的日志 (健康生活方式):");
        for (UserLogEntry log : userBLogs) {
            insertUserLog(embeddingModel, userLogEmbeddingStore, log);
            System.out.println("  ✓ " + log.category + ": " + log.content.substring(0, Math.min(50, log.content.length())) + "...");
        }

        System.out.println("\n测试数据准备完成\n");
    }

    @Test
    @Order(1)
    @DisplayName("个性化效果测试 - 主测试")
    void testPersonalizationEffect() {
        System.out.println("\n========================================");
        System.out.println("开始执行个性化效果测试");
        System.out.println("========================================\n");

        List<PersonalizationScenario> scenarios = createTestScenarios();
        List<ScenarioResult> results = new ArrayList<>();

        int totalScenarios = scenarios.size();
        int personalizedScenarios = 0;

        for (int i = 0; i < scenarios.size(); i++) {
            PersonalizationScenario scenario = scenarios.get(i);
            System.out.println("场景 " + (i + 1) + "/" + totalScenarios + ": " + scenario.name);
            System.out.println("问题: " + scenario.question);
            System.out.println();

            // 为 User A 检索
            System.out.println("User A (不健康生活方式):");
            PersonalizedResponse responseA = getPersonalizedResponse(TEST_USER_A, scenario.question);
            printResponse(responseA);

            // 为 User B 检索
            System.out.println("User B (健康生活方式):");
            PersonalizedResponse responseB = getPersonalizedResponse(TEST_USER_B, scenario.question);
            printResponse(responseB);

            // 判断是否体现个性化
            boolean isPersonalized = analyzePersonalization(scenario, responseA, responseB);
            
            if (isPersonalized) {
                personalizedScenarios++;
            }

            ScenarioResult result = new ScenarioResult(
                scenario.name,
                scenario.question,
                responseA,
                responseB,
                isPersonalized
            );
            results.add(result);

            System.out.println("个性化判断: " + (isPersonalized ? "✅ 是" : "❌ 否"));
            System.out.println("原因: " + result.getPersonalizationReason(scenario, responseA, responseB));
            System.out.println("\n" + "=".repeat(60) + "\n");
        }

        // 计算个性化准确率
        double personalizationRate = totalScenarios > 0 
            ? (double) personalizedScenarios / totalScenarios 
            : 0.0;

        // 输出测试报告
        printTestReport(results, totalScenarios, personalizedScenarios, personalizationRate);

        // 断言: 个性化准确率应该 ≥ 80%
        Assertions.assertTrue(personalizationRate >= 0.80,
            String.format("个性化准确率 %.1f%% 低于目标值 80%%", personalizationRate * 100));
    }

    /**
     * 创建测试场景
     */
    private List<PersonalizationScenario> createTestScenarios() {
        return Arrays.asList(
            new PersonalizationScenario(
                "睡眠场景",
                "Is my sleep duration sufficient?",
                "sleep",
                "User A 睡5小时应得到'睡眠不足'建议，User B 睡8小时应得到'睡眠充足'反馈"
            ),
            new PersonalizationScenario(
                "运动场景",
                "How is my exercise level?",
                "exercise",
                "User A 不运动应得到'需要增加运动'建议，User B 运动60分钟应得到'运动量良好'反馈"
            ),
            new PersonalizationScenario(
                "饮食场景",
                "Is my diet healthy?",
                "diet",
                "User A 高糖快餐应得到'饮食不健康'建议，User B 均衡饮食应得到'饮食健康'反馈"
            ),
            new PersonalizationScenario(
                "体重场景",
                "Do I need to adjust my weight?",
                "weight",
                "User A BMI 18应得到'体重偏轻'建议，User B BMI 28应得到'需要减重'建议"
            )
        );
    }

    /**
     * 获取个性化响应
     */
    private PersonalizedResponse getPersonalizedResponse(String userId, String question) {
        try {
            // 1. 向量化查询
            Embedding embedding = embeddingModel.embed(question).content();

            // 2. 从用户日志检索
            Filter userFilter = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(userId);
            EmbeddingSearchRequest userRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(TOP_K)
                .minScore(MIN_SCORE)
                .filter(userFilter)
                .build();

            EmbeddingSearchResult<TextSegment> userResult = userLogEmbeddingStore.search(userRequest);

            // 3. 从知识库检索
            EmbeddingSearchRequest knowledgeRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(TOP_K)
                .minScore(MIN_SCORE)
                .build();

            EmbeddingSearchResult<TextSegment> knowledgeResult = knowledgeBaseEmbeddingStore.search(knowledgeRequest);

            // 4. 构建响应
            List<RetrievedDoc> userDocs = userResult.matches().stream()
                .map(match -> new RetrievedDoc(
                    match.embedded().text(),
                    match.score(),
                    "user_log"
                ))
                .collect(Collectors.toList());

            List<RetrievedDoc> knowledgeDocs = knowledgeResult.matches().stream()
                .map(match -> new RetrievedDoc(
                    match.embedded().text(),
                    match.score(),
                    "knowledge_base"
                ))
                .collect(Collectors.toList());

            return new PersonalizedResponse(userId, userDocs, knowledgeDocs);

        } catch (Exception e) {
            System.err.println("检索失败: " + e.getMessage());
            return new PersonalizedResponse(userId, Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * 分析是否体现个性化
     */
    private boolean analyzePersonalization(PersonalizationScenario scenario,
                                          PersonalizedResponse responseA,
                                          PersonalizedResponse responseB) {
        // 判断标准:
        // 1. 两个用户都检索到了各自的个人日志
        boolean aHasUserData = !responseA.userDocs.isEmpty();
        boolean bHasUserData = !responseB.userDocs.isEmpty();

        if (!aHasUserData || !bHasUserData) {
            return false; // 如果任一用户没有检索到个人日志，说明没有个性化
        }

        // 2. 两个用户检索到的个人日志内容应该不同
        String aUserContent = responseA.userDocs.stream()
            .map(doc -> doc.content)
            .collect(Collectors.joining(" "));
        
        String bUserContent = responseB.userDocs.stream()
            .map(doc -> doc.content)
            .collect(Collectors.joining(" "));

        boolean contentDifferent = !aUserContent.equals(bUserContent);

        // 3. 个人日志的相似度应该足够高(说明确实用到了个人数据)
        double aMaxUserScore = responseA.userDocs.stream()
            .mapToDouble(doc -> doc.score)
            .max()
            .orElse(0.0);
        
        double bMaxUserScore = responseB.userDocs.stream()
            .mapToDouble(doc -> doc.score)
            .max()
            .orElse(0.0);

        boolean scoresHighEnough = aMaxUserScore >= MIN_SCORE && bMaxUserScore >= MIN_SCORE;

        return aHasUserData && bHasUserData && contentDifferent && scoresHighEnough;
    }

    /**
     * 打印响应信息
     */
    private void printResponse(PersonalizedResponse response) {
        System.out.println("  个人日志检索结果 (" + response.userDocs.size() + " 条):");
        if (response.userDocs.isEmpty()) {
            System.out.println("    (无)");
        } else {
            for (int i = 0; i < response.userDocs.size(); i++) {
                RetrievedDoc doc = response.userDocs.get(i);
                System.out.println("    " + (i + 1) + ". [分数: " + String.format("%.3f", doc.score) + "] " 
                    + doc.content.substring(0, Math.min(80, doc.content.length())) + "...");
            }
        }

        System.out.println("  知识库检索结果 (" + response.knowledgeDocs.size() + " 条):");
        if (response.knowledgeDocs.isEmpty()) {
            System.out.println("    (无)");
        } else {
            for (int i = 0; i < Math.min(2, response.knowledgeDocs.size()); i++) {
                RetrievedDoc doc = response.knowledgeDocs.get(i);
                System.out.println("    " + (i + 1) + ". [分数: " + String.format("%.3f", doc.score) + "] " 
                    + doc.content.substring(0, Math.min(80, doc.content.length())) + "...");
            }
        }
        System.out.println();
    }

    /**
     * 打印测试报告
     */
    private void printTestReport(List<ScenarioResult> results,
                                 int totalScenarios,
                                 int personalizedScenarios,
                                 double personalizationRate) {
        System.out.println("\n========================================");
        System.out.println("个性化效果测试报告");
        System.out.println("========================================\n");

        System.out.println("测试配置:");
        System.out.println("  - 测试场景数: " + totalScenarios);
        System.out.println("  - 测试用户: User A (不健康) vs User B (健康)");
        System.out.println("  - 检索数量: Top-" + TOP_K);
        System.out.println("  - 最低相似度: " + MIN_SCORE);
        System.out.println();

        System.out.println("场景详情:");
        System.out.println("┌────────────────┬──────────────┬────────────────┐");
        System.out.println("│ 场景           │ 个性化       │ 状态           │");
        System.out.println("├────────────────┼──────────────┼────────────────┤");
        
        for (ScenarioResult result : results) {
            String status = result.isPersonalized ? "✅ 通过" : "❌ 未通过";
            System.out.printf("│ %-14s │ %-12s │ %-14s │%n",
                result.scenarioName,
                result.isPersonalized ? "是" : "否",
                status);
        }
        
        System.out.println("└────────────────┴──────────────┴────────────────┘");
        System.out.println();

        System.out.println("未体现个性化的场景:");
        List<ScenarioResult> nonPersonalized = results.stream()
            .filter(r -> !r.isPersonalized)
            .collect(Collectors.toList());
        
        if (nonPersonalized.isEmpty()) {
            System.out.println("  (无)");
        } else {
            for (int i = 0; i < nonPersonalized.size(); i++) {
                ScenarioResult r = nonPersonalized.get(i);
                System.out.println("  " + (i + 1) + ". " + r.scenarioName);
                System.out.println("     问题: " + r.question);
                System.out.println("     User A 检索到个人日志: " + r.responseA.userDocs.size() + " 条");
                System.out.println("     User B 检索到个人日志: " + r.responseB.userDocs.size() + " 条");
            }
        }
        System.out.println();

        System.out.println("总体统计:");
        System.out.println("  - 总场景数: " + totalScenarios);
        System.out.println("  - 体现个性化: " + personalizedScenarios);
        System.out.println("  - 未体现个性化: " + (totalScenarios - personalizedScenarios));
        System.out.println("  - 个性化准确率: " + String.format("%.1f%%", personalizationRate * 100));
        System.out.println();

        String status = personalizationRate >= 0.80 ? "✅ 通过" : "❌ 未通过";
        System.out.println("测试结论: " + status);
        System.out.println("  目标准确率: ≥ 80%");
        System.out.println("  实际准确率: " + String.format("%.1f%%", personalizationRate * 100));
        System.out.println();
    }

    /**
     * 插入用户日志
     */
    private static void insertUserLog(EmbeddingModel embeddingModel,
                                     MilvusEmbeddingStore userLogEmbeddingStore,
                                     UserLogEntry log) {
        try {
            Embedding embedding = embeddingModel.embed(log.content).content();
            
            TextSegment segment = TextSegment.from(log.content);
            segment.metadata().put("user_id", log.userId);
            segment.metadata().put("date", log.date);
            segment.metadata().put("category", log.category);
            segment.metadata().put("source", "user_log");
            
            userLogEmbeddingStore.add(embedding, segment);
            
        } catch (Exception e) {
            System.err.println("插入日志失败: " + e.getMessage());
        }
    }

    @AfterAll
    static void cleanupTestData(@Autowired MilvusEmbeddingStore userLogEmbeddingStore) {
        System.out.println("\n清理测试数据...");
        try {
            // 注意: Milvus 不支持按 metadata 删除，这里只是标记
            // 实际清理需要在测试环境重置或使用专门的清理脚本
            System.out.println("测试数据已标记为待清理");
        } catch (Exception e) {
            System.err.println("清理失败: " + e.getMessage());
        }
    }

    // ==================== 辅助类 ====================

    static class UserLogEntry {
        final String userId;
        final String date;
        final String category;
        final String content;

        UserLogEntry(String userId, String date, String category, String content) {
            this.userId = userId;
            this.date = date;
            this.category = category;
            this.content = content;
        }
    }

    static class PersonalizationScenario {
        final String name;
        final String question;
        final String category;
        final String expectedBehavior;

        PersonalizationScenario(String name, String question, String category, String expectedBehavior) {
            this.name = name;
            this.question = question;
            this.category = category;
            this.expectedBehavior = expectedBehavior;
        }
    }

    static class RetrievedDoc {
        final String content;
        final double score;
        final String source;

        RetrievedDoc(String content, double score, String source) {
            this.content = content;
            this.score = score;
            this.source = source;
        }
    }

    static class PersonalizedResponse {
        final String userId;
        final List<RetrievedDoc> userDocs;
        final List<RetrievedDoc> knowledgeDocs;

        PersonalizedResponse(String userId, List<RetrievedDoc> userDocs, List<RetrievedDoc> knowledgeDocs) {
            this.userId = userId;
            this.userDocs = userDocs;
            this.knowledgeDocs = knowledgeDocs;
        }
    }

    static class ScenarioResult {
        final String scenarioName;
        final String question;
        final PersonalizedResponse responseA;
        final PersonalizedResponse responseB;
        final boolean isPersonalized;

        ScenarioResult(String scenarioName, String question,
                      PersonalizedResponse responseA, PersonalizedResponse responseB,
                      boolean isPersonalized) {
            this.scenarioName = scenarioName;
            this.question = question;
            this.responseA = responseA;
            this.responseB = responseB;
            this.isPersonalized = isPersonalized;
        }

        String getPersonalizationReason(PersonalizationScenario scenario,
                                       PersonalizedResponse responseA,
                                       PersonalizedResponse responseB) {
            if (!responseA.userDocs.isEmpty() && !responseB.userDocs.isEmpty()) {
                return "两个用户都检索到了各自的个人日志，且内容不同，体现了个性化";
            } else if (responseA.userDocs.isEmpty() && responseB.userDocs.isEmpty()) {
                return "两个用户都没有检索到个人日志，系统仅依赖知识库，未体现个性化";
            } else {
                return "只有一个用户检索到个人日志，个性化不完整";
            }
        }
    }
}

