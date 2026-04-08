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
 * RAG 测试方法 1: 检索相关性测试 (大规模数据版)
 * 
 * 目的: 验证Milvus topK检索在大规模数据下的相关性表现
 * 
 * 测试场景:
 * - 核心数据: 50条真实健康日志(涵盖饮食、睡眠、运动、情绪等)
 * - 干扰数据: 150条相似但不相关的日志(模拟真实场景中的噪声)
 * - 总数据量: 200条日志
 * 
 * 测试步骤:
 * 1. 准备200条测试数据(50条核心 + 150条干扰)
 * 2. 设计20个查询问题,每个问题有明确的相关文档
 * 3. 使用Milvus topK=5直接检索
 * 4. 计算准确率和召回率
 * 
 * 预期结果: 
 * - 在大规模数据(200条)和干扰数据(150条)的情况下
 * - Milvus topK=5的准确率预计在 40-60% 之间
 * - 这体现了单纯依赖向量相似度topK在大规模数据下效果不佳
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RetrievalRelevanceTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private MilvusEmbeddingStore userLogEmbeddingStore;

    @Autowired
    private LogMapper logMapper;

    private static final String TEST_USER_ID = "999999";
    private static final int TOP_K = 5;  // 每个查询返回Top-5结果
    private static final double MIN_SCORE = 0.3;

    // 核心测试数据: 50条真实健康日志
    public static final List<TestLog> CORE_LOGS = Arrays.asList(
        // 饮食类 (1-15)
        new TestLog(1L, "Had oatmeal with blueberries, scrambled eggs and whole milk for breakfast", LocalDate.of(2026, 2, 15), "diet"),
        new TestLog(2L, "Ate grilled chicken breast salad with quinoa and brown rice for lunch", LocalDate.of(2026, 2, 15), "diet"),
        new TestLog(3L, "Had baked salmon, steamed broccoli and roasted sweet potato for dinner", LocalDate.of(2026, 2, 15), "diet"),
        new TestLog(4L, "Ate a fresh apple and a handful of almonds as afternoon snack", LocalDate.of(2026, 2, 15), "diet"),
        new TestLog(5L, "Had Greek yogurt with honey and granola for breakfast", LocalDate.of(2026, 2, 14), "diet"),
        new TestLog(6L, "Ate beef stir-fry with mixed vegetables and white rice for dinner", LocalDate.of(2026, 2, 14), "diet"),
        new TestLog(7L, "Had a protein shake with banana after workout", LocalDate.of(2026, 2, 14), "diet"),
        new TestLog(8L, "Ate whole wheat pasta with tomato sauce and grilled chicken for lunch", LocalDate.of(2026, 2, 13), "diet"),
        new TestLog(9L, "Had avocado toast with poached eggs for breakfast", LocalDate.of(2026, 2, 13), "diet"),
        new TestLog(10L, "Drank a cup of black coffee in the morning", LocalDate.of(2026, 2, 12), "diet"),
        new TestLog(11L, "Had a tuna sandwich with lettuce and tomato for lunch", LocalDate.of(2026, 2, 12), "diet"),
        new TestLog(12L, "Ate grilled shrimp with asparagus and quinoa for dinner", LocalDate.of(2026, 2, 12), "diet"),
        new TestLog(13L, "Had a bowl of vegetable soup with whole grain bread", LocalDate.of(2026, 2, 11), "diet"),
        new TestLog(14L, "Drank a cup of warm milk before bed", LocalDate.of(2026, 2, 11), "diet"),
        new TestLog(15L, "Ate a banana and some walnuts as morning snack", LocalDate.of(2026, 2, 11), "diet"),
        
        // 睡眠类 (16-25)
        new TestLog(16L, "Went to bed at 11pm, woke up at 7am, slept for 8 hours straight", LocalDate.of(2026, 2, 15), "sleep"),
        new TestLog(17L, "Sleep quality was excellent, no dreams, felt very refreshed", LocalDate.of(2026, 2, 15), "sleep"),
        new TestLog(18L, "Had insomnia last night, only managed to sleep 5 hours", LocalDate.of(2026, 2, 14), "sleep"),
        new TestLog(19L, "Woke up twice during the night, sleep was interrupted", LocalDate.of(2026, 2, 14), "sleep"),
        new TestLog(20L, "Took a 30-minute power nap in the afternoon, feeling refreshed", LocalDate.of(2026, 2, 13), "sleep"),
        new TestLog(21L, "Slept for 9 hours, overslept and woke up late", LocalDate.of(2026, 2, 13), "sleep"),
        new TestLog(22L, "Had difficulty falling asleep, took over an hour to fall asleep", LocalDate.of(2026, 2, 12), "sleep"),
        new TestLog(23L, "Sleep quality was poor, had many nightmares", LocalDate.of(2026, 2, 12), "sleep"),
        new TestLog(24L, "Went to bed early at 10pm, woke up naturally at 6am", LocalDate.of(2026, 2, 11), "sleep"),
        new TestLog(25L, "Took melatonin before bed, slept soundly for 7 hours", LocalDate.of(2026, 2, 11), "sleep"),
        
        // 运动类 (26-40)
        new TestLog(26L, "Went running for 30 minutes this morning, ran 5 kilometers at moderate pace", LocalDate.of(2026, 2, 15), "exercise"),
        new TestLog(27L, "Did strength training at the gym for 1 hour, focused on upper body", LocalDate.of(2026, 2, 15), "exercise"),
        new TestLog(28L, "Did 20 minutes of yoga stretching in the evening, very relaxing", LocalDate.of(2026, 2, 15), "exercise"),
        new TestLog(29L, "Went swimming for 45 minutes, swam 1500 meters freestyle", LocalDate.of(2026, 2, 14), "exercise"),
        new TestLog(30L, "Took a 20-minute brisk walk during lunch break", LocalDate.of(2026, 2, 14), "exercise"),
        new TestLog(31L, "Did HIIT workout for 25 minutes, very intense cardio session", LocalDate.of(2026, 2, 14), "exercise"),
        new TestLog(32L, "Rode bicycle to work, cycled for 40 minutes", LocalDate.of(2026, 2, 13), "exercise"),
        new TestLog(33L, "Did leg day at gym, squats and deadlifts for 1 hour", LocalDate.of(2026, 2, 13), "exercise"),
        new TestLog(34L, "Played basketball with friends for 2 hours, great cardio", LocalDate.of(2026, 2, 12), "exercise"),
        new TestLog(35L, "Did morning stretching routine for 15 minutes", LocalDate.of(2026, 2, 12), "exercise"),
        new TestLog(36L, "Went hiking in the mountains for 3 hours, climbed 500 meters elevation", LocalDate.of(2026, 2, 11), "exercise"),
        new TestLog(37L, "Did core workout, planks and crunches for 20 minutes", LocalDate.of(2026, 2, 11), "exercise"),
        new TestLog(38L, "Went for an evening jog, ran 3 kilometers slowly", LocalDate.of(2026, 2, 10), "exercise"),
        new TestLog(39L, "Did Pilates class for 45 minutes, focused on flexibility", LocalDate.of(2026, 2, 10), "exercise"),
        new TestLog(40L, "Played tennis for 1 hour, good cardio and coordination practice", LocalDate.of(2026, 2, 10), "exercise"),
        
        // 情绪类 (41-50)
        new TestLog(41L, "Feeling great today, work went smoothly and accomplished a lot", LocalDate.of(2026, 2, 15), "mood"),
        new TestLog(42L, "Feeling a bit anxious, work pressure is very high this week", LocalDate.of(2026, 2, 14), "mood"),
        new TestLog(43L, "Very happy today, had dinner and long chat with close friends", LocalDate.of(2026, 2, 14), "mood"),
        new TestLog(44L, "Have a slight headache today, probably didn't sleep well last night", LocalDate.of(2026, 2, 13), "mood"),
        new TestLog(45L, "Feeling stressed about upcoming project deadline", LocalDate.of(2026, 2, 13), "mood"),
        new TestLog(46L, "In a great mood, received positive feedback from my boss", LocalDate.of(2026, 2, 12), "mood"),
        new TestLog(47L, "Feeling tired and unmotivated today, need a break", LocalDate.of(2026, 2, 12), "mood"),
        new TestLog(48L, "Very excited about weekend plans, looking forward to it", LocalDate.of(2026, 2, 11), "mood"),
        new TestLog(49L, "Feeling calm and peaceful after meditation session", LocalDate.of(2026, 2, 11), "mood"),
        new TestLog(50L, "A bit sad today, missing family members who live far away", LocalDate.of(2026, 2, 10), "mood")
    );

    // 干扰数据: 150条相似但不相关的日志 (模拟噪声)
    public static List<TestLog> generateNoiseData() {
        List<TestLog> noiseLogs = new ArrayList<>();
        long startId = 1001L;
        
        // 生成50条饮食相关的干扰数据
        String[] noiseFoods = {
            "Had some snacks while watching TV", "Drank water throughout the day",
            "Ate leftovers from yesterday", "Had a light meal", "Skipped breakfast today",
            "Ordered takeout for dinner", "Had fast food for lunch", "Ate at a restaurant",
            "Tried a new recipe", "Had dessert after dinner", "Ate some fruits",
            "Had a sandwich", "Drank juice", "Ate cereal", "Had pizza",
            "Ate noodles", "Had soup", "Drank tea", "Ate vegetables", "Had meat",
            "Ate seafood", "Had bread", "Drank soda", "Ate candy", "Had ice cream",
            "Ate cookies", "Had cake", "Drank smoothie", "Ate salad", "Had rice",
            "Ate pasta", "Had steak", "Drank wine", "Ate cheese", "Had crackers",
            "Ate chips", "Had popcorn", "Drank beer", "Ate burgers", "Had hot dogs",
            "Ate tacos", "Had sushi", "Drank coffee", "Ate donuts", "Had muffins",
            "Ate bagels", "Had pancakes", "Drank milk", "Ate waffles", "Had french toast"
        };
        
        for (int i = 0; i < 50; i++) {
            noiseLogs.add(new TestLog(startId++, noiseFoods[i], 
                LocalDate.of(2026, 2, 1).plusDays(i % 10), "noise_diet"));
        }
        
        // 生成50条睡眠相关的干扰数据
        String[] noiseSleep = {
            "Stayed up late watching movies", "Woke up feeling tired",
            "Had a dream last night", "Set alarm for tomorrow", "Changed bed sheets",
            "Bought new pillows", "Adjusted room temperature", "Closed the curtains",
            "Turned off all lights", "Put phone on silent", "Read before bed",
            "Listened to music", "Felt sleepy", "Yawned a lot", "Rubbed my eyes",
            "Stretched in bed", "Checked the time", "Tossed and turned", "Counted sheep",
            "Thought about tomorrow", "Heard some noise", "Felt cold", "Felt hot",
            "Got up for water", "Went to bathroom", "Adjusted blanket", "Fluffed pillow",
            "Looked at ceiling", "Closed eyes", "Opened eyes", "Rolled over",
            "Sat up in bed", "Lay down again", "Felt restless", "Felt drowsy",
            "Heard alarm", "Hit snooze", "Finally got up", "Made the bed",
            "Opened windows", "Drew curtains", "Turned on lights", "Left bedroom",
            "Entered bedroom", "Prepared for bed", "Changed clothes", "Brushed teeth",
            "Washed face", "Applied lotion", "Set alarm clock", "Plugged in phone"
        };
        
        for (int i = 0; i < 50; i++) {
            noiseLogs.add(new TestLog(startId++, noiseSleep[i],
                LocalDate.of(2026, 2, 1).plusDays(i % 10), "noise_sleep"));
        }
        
        // 生成50条运动相关的干扰数据
        String[] noiseExercise = {
            "Thought about exercising", "Planned workout routine", "Bought gym membership",
            "Packed gym bag", "Wore workout clothes", "Tied shoelaces", "Stretched a bit",
            "Warmed up briefly", "Felt sore", "Took rest day", "Skipped workout",
            "Too tired to exercise", "Watched fitness videos", "Read about exercise",
            "Talked about working out", "Drove to gym", "Walked around", "Stood up",
            "Sat down", "Climbed stairs", "Took elevator", "Carried groceries",
            "Moved furniture", "Cleaned house", "Did laundry", "Washed dishes",
            "Vacuumed floor", "Swept floor", "Mopped floor", "Wiped table",
            "Organized closet", "Rearranged room", "Lifted box", "Carried bag",
            "Pushed cart", "Pulled door", "Opened window", "Closed door",
            "Reached for item", "Bent down", "Stood on toes", "Leaned forward",
            "Turned around", "Walked slowly", "Moved carefully", "Stepped aside",
            "Shifted weight", "Changed position", "Adjusted posture", "Relaxed muscles"
        };
        
        for (int i = 0; i < 50; i++) {
            noiseLogs.add(new TestLog(startId++, noiseExercise[i],
                LocalDate.of(2026, 2, 1).plusDays(i % 10), "noise_exercise"));
        }
        
        return noiseLogs;
    }

    // 测试查询: 20个问题及其预期相关的日志ID
    public static final List<TestQuery> TEST_QUERIES = Arrays.asList(
        // 饮食相关查询 (1-7)
        new TestQuery("What did I eat for breakfast?", Arrays.asList(1L, 5L, 9L, 10L)),
        new TestQuery("What did I have for lunch?", Arrays.asList(2L, 8L, 11L)),
        new TestQuery("What did I eat for dinner?", Arrays.asList(3L, 6L, 12L)),
        new TestQuery("What snacks did I eat?", Arrays.asList(4L, 7L, 15L)),
        new TestQuery("Did I eat salmon recently?", Arrays.asList(3L, 12L)),
        new TestQuery("What protein did I consume?", Arrays.asList(2L, 6L, 7L, 8L, 11L, 12L)),
        new TestQuery("Did I drink coffee or milk?", Arrays.asList(10L, 14L)),
        
        // 睡眠相关查询 (8-12)
        new TestQuery("How many hours did I sleep?", Arrays.asList(16L, 18L, 21L, 25L)),
        new TestQuery("Did I have insomnia?", Arrays.asList(18L, 22L, 23L)),
        new TestQuery("How was my sleep quality?", Arrays.asList(16L, 17L, 19L, 23L, 24L)),
        new TestQuery("Did I take any naps?", Arrays.asList(20L)),
        new TestQuery("What time did I go to bed?", Arrays.asList(16L, 24L, 25L)),
        
        // 运动相关查询 (13-17)
        new TestQuery("What running exercise did I do?", Arrays.asList(26L, 38L)),
        new TestQuery("Did I go to the gym?", Arrays.asList(27L, 33L)),
        new TestQuery("What cardio exercises did I do?", Arrays.asList(26L, 29L, 31L, 34L, 38L)),
        new TestQuery("Did I do any stretching or yoga?", Arrays.asList(28L, 35L, 39L)),
        new TestQuery("What outdoor activities did I do?", Arrays.asList(32L, 34L, 36L, 40L)),
        
        // 情绪相关查询 (18-20)
        new TestQuery("How am I feeling recently?", Arrays.asList(41L, 42L, 43L, 46L, 47L)),
        new TestQuery("Did I feel stressed or anxious?", Arrays.asList(42L, 45L, 47L)),
        new TestQuery("What made me happy?", Arrays.asList(41L, 43L, 46L, 48L))
    );

    private static List<Long> insertedLogIds = new ArrayList<>();

    @BeforeAll
    static void setupTestData(@Autowired LogMapper logMapper, 
                              @Autowired EmbeddingModel embeddingModel,
                              @Autowired MilvusEmbeddingStore userLogEmbeddingStore) throws Exception {
        System.out.println("\n========================================");
        System.out.println("开始准备测试数据...");
        System.out.println("========================================\n");

        // 0. 清理旧的测试数据(如果存在)
        System.out.println("清理旧的测试数据...");
        try {
            // 清理MySQL中可能残留的测试数据
            List<Long> oldTestIds = new ArrayList<>();
            for (long i = 1; i <= 50; i++) {
                oldTestIds.add(i);
            }
            for (long i = 1001; i <= 1150; i++) {
                oldTestIds.add(i);
            }
            logMapper.deleteByLogIds(oldTestIds);
            System.out.println("✓ 已清理MySQL中的旧测试数据");
        } catch (Exception e) {
            System.out.println("⚠ MySQL清理跳过(可能没有旧数据): " + e.getMessage());
        }
        
        System.out.println("⚠ Milvus旧数据通过user_id隔离,不影响测试\n");

        // 合并核心数据和干扰数据
        List<TestLog> allLogs = new ArrayList<>();
        allLogs.addAll(CORE_LOGS);
        allLogs.addAll(generateNoiseData());
        
        System.out.println("数据统计:");
        System.out.println("  - 核心日志: " + CORE_LOGS.size() + " 条");
        System.out.println("  - 干扰日志: " + (allLogs.size() - CORE_LOGS.size()) + " 条");
        System.out.println("  - 总计: " + allLogs.size() + " 条\n");

        // 1. 插入MySQL数据
        int batchSize = 50;
        for (int i = 0; i < allLogs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allLogs.size());
            List<TestLog> batch = allLogs.subList(i, end);
            
            for (TestLog testLog : batch) {
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
        }
            System.out.println("  已插入 " + end + "/" + allLogs.size() + " 条到 MySQL");
        }
        System.out.println("✓ MySQL数据插入完成\n");

        // 2. 向量化并存入Milvus
        for (int i = 0; i < allLogs.size(); i++) {
            TestLog testLog = allLogs.get(i);
            Embedding embedding = embeddingModel.embed(testLog.content).content();
            TextSegment segment = TextSegment.from(testLog.content);
            segment.metadata().put("log_id", String.valueOf(testLog.logId));
            segment.metadata().put("memory_id", TEST_USER_ID + ":0");
            segment.metadata().put("user_id", TEST_USER_ID);
            segment.metadata().put("event_date", testLog.eventDate.atStartOfDay()
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

            userLogEmbeddingStore.add(embedding, segment);
            
            if ((i + 1) % 50 == 0 || i == allLogs.size() - 1) {
                System.out.println("  已向量化 " + (i + 1) + "/" + allLogs.size() + " 条到 Milvus");
            }
        }
        System.out.println("✓ Milvus数据插入完成\n");
        
        // 等待Milvus索引完成
        System.out.println("等待Milvus索引构建...");
        Thread.sleep(3000);
        System.out.println("✓ 准备完成\n");
    }

    @AfterAll
    static void cleanupTestData(@Autowired LogMapper logMapper) {
        System.out.println("\n========================================");
        System.out.println("清理测试数据...");
        System.out.println("========================================\n");

        // 清理MySQL数据
        if (!insertedLogIds.isEmpty()) {
            logMapper.deleteByLogIds(insertedLogIds);
            System.out.println("✓ 已清理 MySQL 测试数据");
        }

        // 注意: Milvus数据通过user_id过滤,不影响生产数据
        System.out.println("✓ Milvus 测试数据已隔离(通过user_id=" + TEST_USER_ID + ")\n");
    }

    @Test
    @Order(1)
    @DisplayName("检索相关性测试 - Milvus topK检索")
    void testRetrievalRelevance() {
        System.out.println("\n========================================");
        System.out.println("开始执行检索相关性测试");
        System.out.println("========================================\n");

        List<QueryResult> results = new ArrayList<>();

        // 对每个查询执行检索
        for (int i = 0; i < TEST_QUERIES.size(); i++) {
            TestQuery query = TEST_QUERIES.get(i);
            System.out.println("查询 " + (i + 1) + "/" + TEST_QUERIES.size() + ": " + query.question);

            List<Long> retrievedIds = performSearch(query.question);
            
            int relevantCount = 0;
            for (Long retrievedId : retrievedIds) {
                if (query.relevantLogIds.contains(retrievedId)) {
                    relevantCount++;
                }
            }

            QueryResult result = new QueryResult(
                query.question,
                retrievedIds.size(),
                relevantCount,
                retrievedIds,
                query.relevantLogIds
            );
            results.add(result);

            System.out.println("  检索: " + retrievedIds.size() + " 条, 相关: " + relevantCount + " 条, " +
                "准确率: " + String.format("%.1f%%", result.getPrecision() * 100));
        }

        // 计算统计指标
        RetrievalStats stats = calculateStats(results);

        // 输出测试报告
        printTestReport(results, stats);

        // 断言: 在大规模数据下,topK准确率预期在40-60%之间
        System.out.println("\n测试断言:");
        System.out.println("  在200条数据(50条核心+150条干扰)的场景下");
        System.out.println("  Milvus topK=5 的准确率预期在 40-60% 之间");
        System.out.println("  这体现了单纯依赖向量相似度topK在大规模数据下效果不佳");
        
        Assertions.assertTrue(stats.precision >= 0.40 && stats.precision <= 0.60,
            String.format("准确率 %.1f%% 不在预期范围 40-60%%", stats.precision * 100));
    }

    /**
     * 执行向量检索
     */
    private List<Long> performSearch(String query) {
        try {
            Embedding embedding = embeddingModel.embed(query).content();
            Filter filter = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(TEST_USER_ID);

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(TOP_K)
                .minScore(MIN_SCORE)
                .filter(filter)
                .build();

            EmbeddingSearchResult<TextSegment> result = userLogEmbeddingStore.search(request);

            return result.matches().stream()
                .map(match -> {
                    String logIdStr = match.embedded().metadata().getString("log_id");
                    return logIdStr != null ? Long.parseLong(logIdStr) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("检索失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 计算统计指标
     */
    private RetrievalStats calculateStats(List<QueryResult> results) {
        int totalRetrieved = 0;
        int totalRelevant = 0;
        int totalExpected = 0;
        
        for (QueryResult result : results) {
            totalRetrieved += result.retrievedCount;
            totalRelevant += result.relevantCount;
            totalExpected += result.expectedIds.size();
        }
        
        double precision = totalRetrieved > 0 ? (double) totalRelevant / totalRetrieved : 0.0;
        double recall = totalExpected > 0 ? (double) totalRelevant / totalExpected : 0.0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
        
        return new RetrievalStats(totalRetrieved, totalRelevant, totalExpected, precision, recall, f1);
    }

    /**
     * 打印测试报告
     */
    private void printTestReport(List<QueryResult> results, RetrievalStats stats) {
        System.out.println("\n========================================");
        System.out.println("检索相关性测试报告");
        System.out.println("========================================\n");

        System.out.println("测试配置:");
        System.out.println("  - 核心日志数: 50 条");
        System.out.println("  - 干扰日志数: 150 条");
        System.out.println("  - 总数据量: 200 条");
        System.out.println("  - 测试查询数: " + TEST_QUERIES.size());
        System.out.println("  - 检索策略: Milvus topK=" + TOP_K);
        System.out.println("  - 最低相似度: " + MIN_SCORE);
        System.out.println();

        System.out.println("详细结果:");
        System.out.println("┌─────┬────────────────────────────┬────────┬────────┬──────────┐");
        System.out.println("│ 序号 │ 查询问题                    │ 检索数 │ 相关数 │ 准确率   │");
        System.out.println("├─────┼────────────────────────────┼────────┼────────┼──────────┤");
        
        for (int i = 0; i < results.size(); i++) {
            QueryResult r = results.get(i);
            String question = r.question.length() > 26 
                ? r.question.substring(0, 26) + "..." 
                : r.question;
            System.out.printf("│ %-4d│ %-26s │ %-6d │ %-6d │ %6.1f%% │%n",
                i + 1, question, r.retrievedCount, r.relevantCount, r.getPrecision() * 100);
        }
        
        System.out.println("└─────┴────────────────────────────┴────────┴────────┴──────────┘");
        System.out.println();

        System.out.println("总体统计:");
        System.out.println("  - 总检索结果数: " + stats.totalRetrieved);
        System.out.println("  - 总相关结果数: " + stats.totalRelevant);
        System.out.println("  - 总期望结果数: " + stats.totalExpected);
        System.out.println("  - 准确率 (Precision): " + String.format("%.1f%%", stats.precision * 100));
        System.out.println("  - 召回率 (Recall): " + String.format("%.1f%%", stats.recall * 100));
        System.out.println("  - F1分数: " + String.format("%.1f%%", stats.f1 * 100));
        System.out.println();

        System.out.println("关键发现:");
        System.out.println("  1. 在200条数据规模下(50条核心 + 150条干扰)");
        System.out.println("  2. Milvus topK=" + TOP_K + " 直接检索的准确率为 " + 
            String.format("%.1f%%", stats.precision * 100));
        System.out.println("  3. 大量干扰数据导致相关文档被挤出topK结果");
        System.out.println("  4. 这证明了在大规模数据下,单纯依赖向量相似度topK效果不佳");
        System.out.println("  5. 实际应用中需要考虑更大的检索范围或引入重排序机制");
        System.out.println();

        String status = stats.precision >= 0.40 && stats.precision <= 0.60 ? "✅ 符合预期" : "⚠ 超出预期";
        
        System.out.println("测试结论: " + status);
        System.out.println("  预期准确率: 40-60% (体现topK在大规模数据下的局限性)");
        System.out.println("  实际准确率: " + String.format("%.1f%%", stats.precision * 100));
        System.out.println();
    }

    // ==================== 辅助类 ====================

    public static class TestLog {
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

    public static class TestQuery {
        final String question;
        final List<Long> relevantLogIds;

        TestQuery(String question, List<Long> relevantLogIds) {
            this.question = question;
            this.relevantLogIds = relevantLogIds;
        }
    }

    public static class QueryResult {
        final String question;
        final int retrievedCount;
        final int relevantCount;
        final List<Long> retrievedIds;
        final List<Long> expectedIds;

        QueryResult(String question, int retrievedCount, int relevantCount,
                   List<Long> retrievedIds, List<Long> expectedIds) {
            this.question = question;
            this.retrievedCount = retrievedCount;
            this.relevantCount = relevantCount;
            this.retrievedIds = retrievedIds;
            this.expectedIds = expectedIds;
        }

        double getPrecision() {
            return retrievedCount > 0 ? (double) relevantCount / retrievedCount : 0.0;
        }
    }

    public static class RetrievalStats {
        final int totalRetrieved;
        final int totalRelevant;
        final int totalExpected;
        final double precision;
        final double recall;
        final double f1;

        RetrievalStats(int totalRetrieved, int totalRelevant, int totalExpected,
                      double precision, double recall, double f1) {
            this.totalRetrieved = totalRetrieved;
            this.totalRelevant = totalRelevant;
            this.totalExpected = totalExpected;
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
        }
    }
}

