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
import org.bupt.demoapp.aiservice.SummaryGenerationService;
import org.bupt.demoapp.aiservice.SummaryQueryAnalysisService;
import org.bupt.demoapp.common.MemoryIds;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.entity.ChatLog;
import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.mapper.LogMapper;
import org.bupt.demoapp.service.LogSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 健康总结服务实现
 * 
 * 流程：
 * 1. LLM 分析查询（时间范围 + 是否有类别要求）
 * 2. 根据是否有类别选择数据获取方式（MySQL 或 Milvus）
 * 3. 检查数量并决定是否分批
 * 4. 生成总结
 */
@Service
public class LogSummaryServiceImp implements LogSummaryService {
    private static final Logger log = LoggerFactory.getLogger(LogSummaryServiceImp.class);
    
    // 数量限制
    private static final int MAX_LOGS = 30000;
    private static final int BATCH_SIZE = 10000;
    private static final int MAX_BATCHES = 3;
    // Milvus topk 上限为 16384
    private static final int MILVUS_MAX_TOP_K = 16384;
    
    // Milvus 召回参数
    private static final double MIN_SCORE = 0.3;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 500;

    @Autowired
    private SummaryQueryAnalysisService queryAnalysisService;
    
    @Autowired
    private SummaryGenerationService summaryGenerationService;
    
    @Autowired
    private LogMapper logMapper;
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    @Autowired
    private MilvusEmbeddingStore milvusEmbeddingStore;
    
    @Autowired
    private MemoryIds memoryIds;

    @Override
    public ChatResponse summarize(String memoryId, String msg) {
        log.info(">>> 总结请求 - memoryId: {}, msg: {}", memoryId, msg);
        long startTime = System.currentTimeMillis();
        
        // 获取用户ID
        String userId = extractUserId(memoryId);
        if (userId == null) {
            return errorResponse("用户信息解析失败");
        }
        
        // 查询分析（时间范围 + 类别）
        //时间范围
        String analysisResult = queryAnalysisService.analyzeQuery(
            LocalDate.now().toString(), msg);
        log.info(">>> 查询分析结果: {}", analysisResult);
        //如果用户时间范围输入不完整
        if (analysisResult.equals("INCOMPLETE")) {
            return incompleteTimeResponse();
        }

        QueryAnalysis analysis = parseAnalysis(analysisResult);
        if (analysis == null) {
            return errorResponse("时间范围解析失败");
        }
        log.info(">>> 时间范围: {} ~ {}, 有类别: {}", 
            analysis.startDate, analysis.endDate, analysis.hasCategory);
        
        // 获取日志ID列表
        List<String> logIds;
        if (analysis.hasCategory) {
            // 有类别：Milvus 召回
            log.info(">>> 使用 Milvus 召回（有类别要求）");
            logIds = searchMilvusWithTime(msg, userId, analysis);
        } else {
            // 无类别：MySQL 查询
            log.info(">>> 使用 MySQL 查询（无类别要求）");
            logIds = queryMysqlByTime(userId, analysis);
        }
        
        if (logIds == null || logIds.isEmpty()) {
            return noResultResponse(analysis);
        }
        log.info(">>> 获取 {} 条日志ID", logIds.size());
        
        // 检查数量
        if (logIds.size() > MAX_LOGS) {
            return tooManyLogsResponse(logIds.size());
        }
        
        // 生成总结
        String summary;
        //日志数量小于10000条
        if (logIds.size() <= BATCH_SIZE) {
            log.info(">>> 直接生成总结");
            summary = generateSingleBatchSummary(analysis, logIds, msg);

        }
        //日志大于10000条,小于30000条
        else {
            log.info(">>> 分批生成总结");
            summary = generateMultiBatchSummary(analysis, logIds, msg);
        }
        
        String summaryId = "SUMMARY-" + System.currentTimeMillis();
        log.info(">>> 总结完成 - 耗时: {}ms", System.currentTimeMillis() - startTime);
        return new ChatResponse(summaryId, Intent.SUMMARY.name(), summary, false, false);
    }

    /**
     * MySQL 按时间范围查询所有日志
     */
    private List<String> queryMysqlByTime(String userId, QueryAnalysis analysis) {
        try {
            List<ChatLog> logs = logMapper.findByUserIdAndTimeRange(
                userId, analysis.startDate, analysis.endDate);
            return logs.stream()
                .map(log -> String.valueOf(log.getLogId()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(">>> MySQL 查询失败", e);
            return null;
        }
    }

    /**
     * Milvus 按时间和语义召回（支持多次召回合并，最多30000条）
     */
    private List<String> searchMilvusWithTime(String query, String userId, QueryAnalysis analysis) {
        Embedding embedding;
        try {
            embedding = embeddingModel.embed(query).content();
        } catch (Exception e) {
            log.error(">>> 向量化失败", e);
            return null;
        }
        
        // 构建 filter
        Filter filter = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(userId)
            .and(MetadataFilterBuilder.metadataKey("event_date")
                .isGreaterThanOrEqualTo(dateToTimestamp(analysis.startDate)))
            .and(MetadataFilterBuilder.metadataKey("event_date")
                .isLessThanOrEqualTo(dateToTimestamp(analysis.endDate)));
        
        Set<String> allLogIds = new LinkedHashSet<>();
        
        // 第一次召回：minScore = 0.3
        log.info(">>> Milvus 第1次召回 - minScore: {}", MIN_SCORE);
        List<String> batch1 = searchMilvusBatch(embedding, filter, MIN_SCORE);
        if (batch1 == null) {
            return null;
        }
        allLogIds.addAll(batch1);
        log.info(">>> 第1次召回: {} 条, 累计: {} 条", batch1.size(), allLogIds.size());
        
        // 如果第一次就达到上限且还需要更多，进行第二次召回
        if (batch1.size() == MILVUS_MAX_TOP_K && allLogIds.size() < MAX_LOGS) {
            log.info(">>> Milvus 第2次召回 - minScore: 0.2");
            List<String> batch2 = searchMilvusBatch(embedding, filter, 0.2);
            if (batch2 != null) {
                allLogIds.addAll(batch2);
                log.info(">>> 第2次召回: {} 条, 累计: {} 条（去重后）", batch2.size(), allLogIds.size());
            }
        }
        
        // 如果第二次也达到上限且还需要更多，进行第三次召回
        if (allLogIds.size() >= MILVUS_MAX_TOP_K * 2 - 1000 && allLogIds.size() < MAX_LOGS) {
            log.info(">>> Milvus 第3次召回 - minScore: 0.15");
            List<String> batch3 = searchMilvusBatch(embedding, filter, 0.15);
            if (batch3 != null) {
                allLogIds.addAll(batch3);
                log.info(">>> 第3次召回: {} 条, 累计: {} 条（去重后）", batch3.size(), allLogIds.size());
            }
        }
        
        return new ArrayList<>(allLogIds);
    }

    /**
     * 单次 Milvus 召回
     */
    private List<String> searchMilvusBatch(Embedding embedding, Filter filter, double minScore) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(MILVUS_MAX_TOP_K)
            .minScore(minScore)
            .filter(filter)
            .build();
        
        List<EmbeddingMatch<TextSegment>> matches = executeSearchWithRetry(request);
        if (matches == null) {
            return null;
        }
        
        return matches.stream()
            .map(m -> m.embedded().metadata().getString("log_id"))
            .filter(id -> id != null)
            .collect(Collectors.toList());
    }

    /**
     * 生成单批总结
     */
    private String generateSingleBatchSummary(QueryAnalysis analysis, 
                                              List<String> logIds, String query) {
        List<ChatLog> logs = logMapper.findByLogIds(logIds);
        String logsText = buildLogsText(logs);
        String timeRange = formatTimeRange(analysis);
        String currentDate = LocalDate.now().toString();
        
        return summaryGenerationService.generateSummary(
            currentDate, timeRange, logIds.size(), logsText, query);
    }

    /**
     * 生成多批总结
     */
    private String generateMultiBatchSummary(QueryAnalysis analysis, 
                                             List<String> logIds, String query) {
        int batchCount = (logIds.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        List<String> subSummaries = new ArrayList<>();
        String currentDate = LocalDate.now().toString();
        
        for (int i = 0; i < batchCount; i++) {
            int start = i * BATCH_SIZE;
            int end = Math.min(start + BATCH_SIZE, logIds.size());
            List<String> batchIds = logIds.subList(start, end);
            
            List<ChatLog> batchLogs = logMapper.findByLogIds(batchIds);
            String logsText = buildLogsText(batchLogs);
            String timeRange = formatTimeRange(analysis);
            
            String sub = summaryGenerationService.generateSummary(
                currentDate, timeRange, logIds.size(), logsText, query);
            subSummaries.add(String.format("批次%d:\n%s", i+1, sub));
            
            log.info(">>> 完成批次 {}/{}", i+1, batchCount);
        }
        
        // 合并子总结
        return summaryGenerationService.mergeSummaries(
            currentDate,
            formatTimeRange(analysis),
            logIds.size(),
            String.join("\n\n---\n\n", subSummaries),
            query
        );
    }

    /**
     * 构建日志文本
     */
    private String buildLogsText(List<ChatLog> logs) {
        StringBuilder sb = new StringBuilder();
        for (ChatLog chatLog : logs) {
            String date = chatLog.getEventDate() != null
                ? chatLog.getEventDate().toString()
                : (chatLog.getCreateTime() != null ? 
                    chatLog.getCreateTime().toLocalDate().toString() : "未知");
            sb.append(String.format("- %s: %s\n", date, chatLog.getRawText()));
        }
        return sb.toString();
    }

    /**
     * 解析分析结果
     */
    private QueryAnalysis parseAnalysis(String result) {
        try {
            String[] parts = result.split(",");
            if (parts.length == 3) {
                LocalDate startDate = LocalDate.parse(parts[0].trim());
                LocalDate endDate = LocalDate.parse(parts[1].trim());
                boolean hasCategory = Boolean.parseBoolean(parts[2].trim());
                return new QueryAnalysis(startDate, endDate, hasCategory);
            }
        } catch (Exception e) {
            log.error(">>> 解析分析结果失败: {}", result, e);
        }
        return null;
    }

    /**
     * 格式化时间范围
     */
    private String formatTimeRange(QueryAnalysis analysis) {
        if (analysis.startDate.equals(analysis.endDate)) {
            return analysis.startDate.toString();
        }
        return analysis.startDate + " to " + analysis.endDate;
    }

    /**
     * 获取用户ID
     */
    private String extractUserId(String memoryId) {
        try {
            return String.valueOf(memoryIds.extractUserId(memoryId));
        } catch (Exception e) {
            log.error(">>> 解析userId失败 - memoryId: {}", memoryId, e);
            return null;
        }
    }

    /**
     * 日期转时间戳
     */
    private long dateToTimestamp(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Milvus 检索重试
     */
    private List<EmbeddingMatch<TextSegment>> executeSearchWithRetry(EmbeddingSearchRequest request) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                EmbeddingSearchResult<TextSegment> result = milvusEmbeddingStore.search(request);
                log.info(">>> Milvus检索成功 - 召回: {} 条", result.matches().size());
                return result.matches();
            } catch (Exception e) {
                lastError = e;
                if (e.getMessage() != null && e.getMessage().contains("Mutation")) {
                    log.warn(">>> Milvus Mutation冲突，重试 {}/{}", attempt, MAX_RETRIES);
                    if (attempt < MAX_RETRIES) {
                        sleep(RETRY_DELAY_MS * attempt);
                    }
                } else {
                    log.error(">>> Milvus检索失败", e);
                    break;
                }
            }
        }
        log.error(">>> Milvus检索最终失败", lastError);
        return null;
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 响应构造 ====================
    
    private ChatResponse errorResponse(String message) {
        String summaryId = "SUMMARY-" + System.currentTimeMillis();
        return new ChatResponse(summaryId, Intent.SUMMARY.name(), 
            "抱歉，" + message, false, false);
    }

    private ChatResponse incompleteTimeResponse() {
        String summaryId = "SUMMARY-" + System.currentTimeMillis();
        return new ChatResponse(summaryId, Intent.SUMMARY.name(), 
            "请提供明确的时间范围，例如：\"1-2月的健康报告\" 或 \"上个月的运动情况\"", 
            false, false);
    }

    private ChatResponse noResultResponse(QueryAnalysis analysis) {
        String summaryId = "SUMMARY-" + System.currentTimeMillis();
        return new ChatResponse(summaryId, Intent.SUMMARY.name(), 
            String.format("抱歉，在%s没有找到相关的记录", formatTimeRange(analysis)), 
            false, false);
    }

    private ChatResponse tooManyLogsResponse(int count) {
        String summaryId = "SUMMARY-" + System.currentTimeMillis();
        String msg = String.format(
            "您查询的时间范围内有 %d 条相关记录，数据量过大（上限%d条）。\n" +
            "建议缩短时间范围（如按月、按周查询）以获得更准确的总结。",
            count, MAX_LOGS
        );
        return new ChatResponse(summaryId, Intent.SUMMARY.name(), msg, false, false);
    }

    // ==================== 内部类 ====================
    
    private static class QueryAnalysis {
        final LocalDate startDate;
        final LocalDate endDate;
        final boolean hasCategory;

        QueryAnalysis(LocalDate startDate, LocalDate endDate, boolean hasCategory) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.hasCategory = hasCategory;
        }
    }
}
