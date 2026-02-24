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
import org.bupt.demoapp.aiservice.TimeRangeExtractionService;
import org.bupt.demoapp.common.MemoryIds;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.entity.ChatLog;
import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.mapper.LogMapper;
import org.bupt.demoapp.service.LogQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日志查询服务实现
 *
 * 流程：
 * 1. LLM 解析时间范围（从用户查询中提取）
 * 2. Milvus向量检索（带 event_date filter）
 * 3. MySQL 拉取完整日志
 * 4. ReplyGenerationService 生成回复
 */
@Service
public class LogQueryServiceImp implements LogQueryService {
    private static final Logger log = LoggerFactory.getLogger(LogQueryServiceImp.class);
    
    // 召回参数设置
    private static final int MAX_RESULTS = 100;
    private static final double MIN_SCORE = 0.3;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 500;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private MilvusEmbeddingStore userLogEmbeddingStore;

    @Autowired
    private LogMapper logMapper;

    @Autowired
    private MemoryIds memoryIds;

    @Autowired
    private ReplyGenerationService replyGenerationService;

    @Autowired
    private TimeRangeExtractionService timeRangeExtractionService;

    @Override
    public ChatResponse queryChat(String memoryId, String msg) {
        log.info(">>> 查询请求 - memoryId: {}, msg: {}", memoryId, msg);
        long startTime = System.currentTimeMillis();
        
        // 1. 获取用户ID
        String userId = extractUserId(memoryId);
        if (userId == null) {
            return errorResponse("用户信息解析失败");
        }

        // 2. LLM 解析时间范围
        DateRange timeRange = parseTimeRange(msg);
        log.info(">>> 解析时间范围: {}", timeRange != null ? timeRange : "无");

        // 3. Milvus 召回(使用用户名和日期进行过滤)
        List<String> logIds = searchMilvus(msg, userId, timeRange);
        if (logIds == null) {
            return errorResponse("检索失败，请稍后再试");
        }
        
        // 4. MySQL 拉取完整日志
        List<ChatLog> logs = fetchLogs(logIds);
        log.info(">>> 召回 {} 条日志", logs.size());

        if (logs.isEmpty()) {
            String queryId = "QUERY-" + System.currentTimeMillis();
            String noResultMsg = timeRange != null 
                    ? "抱歉，在" + formatTimeRange(timeRange) + "没有找到相关的记录" 
                    : "抱歉，没有找到相关的记录";
            return new ChatResponse(queryId, Intent.QUERY.name(), noResultMsg, false, false);
        }

        // 5. LLM 生成回复
        //把筛选后的日志传给LLM
        String logsText = buildLogsText(logs);
        String reply = generateReply(msg, logsText);

        String queryId = "QUERY-" + System.currentTimeMillis();

        log.info(">>> 查询完成 - 耗时: {}ms", System.currentTimeMillis() - startTime);
        return new ChatResponse(queryId, Intent.QUERY.name(), reply, false, false);
    }

    /**
     * 构建返回给LLM的日志
     */
    private String buildLogsText(List<ChatLog> logs) {
        if (logs.isEmpty()) {
            return "无相关记录";
        }

        StringBuilder sb = new StringBuilder();
        for (ChatLog chatLog : logs) {
            String date = chatLog.getEventDate() != null
                    ? chatLog.getEventDate().toString()
                    : (chatLog.getCreateTime() != null ? chatLog.getCreateTime().toLocalDate().toString() : "未知");
            sb.append(String.format("- %s: %s\n", date, chatLog.getRawText()));
        }
        return sb.toString();
    }

    /**
     * 格式化时间范围用于提示信息
     */
    private String formatTimeRange(DateRange range) {
        if (range.startDate.equals(range.endDate)) {
            return range.startDate.toString();
        }
        return range.startDate + " 到 " + range.endDate;
    }

    /**
     * 调用 ReplyGenerationService 生成回复
     */
    private String generateReply(String query, String filteredLogs) {
        try {
            return replyGenerationService.generateQueryReply(query, filteredLogs);
        } catch (Exception e) {
            log.error(">>> 回复生成失败", e);
            return filteredLogs.equals("无相关记录") 
                    ? "抱歉，没有找到相关的记录" 
                    : "抱歉，生成回复时出错了，请稍后再试";
        }
    }





    // ==================== 时间范围解析 ====================
    
    /**
     * 时间范围内部类
     */
    private static class DateRange {
        final LocalDate startDate;
        final LocalDate endDate;

        DateRange(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        @Override
        public String toString() {
            return startDate + " ~ " + endDate;
        }
    }

    /**
     * 使用 LLM 解析用户查询中的时间范围
     * 
     * @param query 用户查询
     * @return 时间范围，如果没有时间相关表达则返回 null
     */
    private DateRange parseTimeRange(String query) {
        try {
            String currentDate = LocalDate.now().toString();
            String result = timeRangeExtractionService.extractTimeRange(currentDate, query);
            log.info(">>> LLM 时间范围提取结果: {}", result);

            String cleaned = result.trim();
            
            // 如果返回"无"或空，表示没有时间范围
            if (cleaned.equals("无") || cleaned.isEmpty()) {
                return null;
            }
            
            // 解析格式：startDate,endDate
            String[] parts = cleaned.split(",");
            if (parts.length == 2) {
                LocalDate startDate = LocalDate.parse(parts[0].trim());
                LocalDate endDate = LocalDate.parse(parts[1].trim());
                return new DateRange(startDate, endDate);
            }
            
            log.warn(">>> 时间范围格式不正确: {}", result);
            return null;
            
        } catch (DateTimeParseException e) {
            log.warn(">>> 日期解析失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error(">>> 时间范围提取失败", e);
            return null;
        }
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
     * 从 Milvus 中搜索日志（带时间范围 filter）
     */
    private List<String> searchMilvus(String query, String userId, DateRange timeRange) {
        Embedding embedding;
        try {
            embedding = embeddingModel.embed(query).content();
        } catch (Exception e) {
            log.error(">>> 向量化失败 - query: {}", query, e);
            return null;
        }

        // 构建 filter：user_id 必须 + event_date 可选
        Filter filter = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(userId);
        
        if (timeRange != null) {
            // 转换为毫秒时间戳进行过滤（性能更好）
            long startTimestamp = dateToStartOfDayTimestamp(timeRange.startDate);
            long endTimestamp = dateToEndOfDayTimestamp(timeRange.endDate);
            
            filter = filter.and(
                MetadataFilterBuilder.metadataKey("event_date")
                    .isGreaterThanOrEqualTo(startTimestamp)
            ).and(
                MetadataFilterBuilder.metadataKey("event_date")
                    .isLessThanOrEqualTo(endTimestamp)
            );
            log.info(">>> Milvus filter: user_id={}, event_date=[{} ({}), {} ({})]", 
                    userId, timeRange.startDate, startTimestamp, timeRange.endDate, endTimestamp);
        } else {
            log.info(">>> Milvus filter: user_id={} (无时间限制)", userId);
        }

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(MAX_RESULTS)
                .minScore(MIN_SCORE)
                .filter(filter)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = executeSearchWithRetry(request, userId);
        if (matches == null) {
            return null;
        }

        return matches.stream()
                .map(match -> match.embedded().metadata().getString("log_id"))
                .filter(id -> id != null)
                .collect(Collectors.toList());
    }

    /**
     * 将 LocalDate 转换为当天 00:00:00 的毫秒时间戳
     */
    private long dateToStartOfDayTimestamp(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 将 LocalDate 转换为当天 23:59:59.999 的毫秒时间戳
     */
    private long dateToEndOfDayTimestamp(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
    }

    private List<EmbeddingMatch<TextSegment>> executeSearchWithRetry(EmbeddingSearchRequest request, String userId) {
        Exception lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                EmbeddingSearchResult<TextSegment> result = userLogEmbeddingStore.search(request);
                log.info(">>> Milvus检索成功 - userId: {}, 召回: {} 条", userId, result.matches().size());
                return result.matches();
            } catch (Exception e) {
                lastError = e;
                if (e.getMessage() != null && e.getMessage().contains("Mutation")) {
                    log.warn(">>> Milvus Mutation冲突，重试 {}/{}", attempt, MAX_RETRIES);
                    if (attempt < MAX_RETRIES) {
                        sleep(RETRY_DELAY_MS * attempt);
                    }
                } else {
                    log.error(">>> Milvus检索失败 - userId: {}", userId, e);
                    break;
                }
            }
        }

        log.error(">>> Milvus检索最终失败 - userId: {}", userId, lastError);
        return null;
    }
    //从数据库中通过logId检索日志原文
    private List<ChatLog> fetchLogs(List<String> logIds) {
        if (logIds.isEmpty()) {
            return List.of();
        }
        try {
            return logMapper.findByLogIds(logIds);
        } catch (Exception e) {
            log.warn(">>> MySQL查询失败", e);
            return List.of();
        }
    }

    private ChatResponse errorResponse(String message) {
        String queryId = "QUERY-" + System.currentTimeMillis();
        return new ChatResponse(queryId, Intent.QUERY.name(), "抱歉，" + message, false, false);
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
