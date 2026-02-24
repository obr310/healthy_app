package org.bupt.demoapp.serviceImp;

import org.bupt.demoapp.aiservice.SummaryGenerationService;
import org.bupt.demoapp.aiservice.SummaryQueryAnalysisService;
import org.bupt.demoapp.common.MemoryIds;
import org.bupt.demoapp.dto.ChatResponse;
import org.bupt.demoapp.entity.ChatLog;
import org.bupt.demoapp.entity.Intent;
import org.bupt.demoapp.mapper.LogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 分批总结功能单元测试
 * 
 * 验证：当日志数 > 10000 时，会调用 generateMultiBatchSummary，
 * 且 generateSummary 被调用多次、mergeSummaries 被调用一次
 */
@ExtendWith(MockitoExtension.class)
class LogSummaryServiceImpBatchTest {

    private static final String MEMORY_ID = "270375239215616000:1";
    private static final String MSG = "给我一份1-2月的健康报告";
    private static final int BATCH_SIZE = 10000;
    private static final int TOTAL_LOGS = 20001;  // 超过10000，分批数 = ceil(20001/10000) = 3

    @Mock
    private SummaryQueryAnalysisService queryAnalysisService;

    @Mock
    private SummaryGenerationService summaryGenerationService;

    @Mock
    private LogMapper logMapper;

    @Mock
    private MemoryIds memoryIds;

    @InjectMocks
    private LogSummaryServiceImp service;

    @BeforeEach
    void setUp() {
        // 用户ID 解析
        when(memoryIds.extractUserId(MEMORY_ID)).thenReturn(270375239215616000L);

        // 查询分析：无类别，使用 MySQL 路径
        when(queryAnalysisService.analyzeQuery(anyString(), eq(MSG)))
            .thenReturn("2026-01-01,2026-02-28,false");

        // MySQL 返回 15001 条日志（触发分批）
        List<ChatLog> allLogs = IntStream.range(1, TOTAL_LOGS + 1)
            .mapToObj(i -> {
                ChatLog log = new ChatLog();
                log.setLogId((long) i);
                log.setRawText("测试日志内容 " + i);
                log.setEventDate(LocalDate.of(2026, 1, 1));
                return log;
            })
            .collect(Collectors.toList());

        when(logMapper.findByUserIdAndTimeRange(
            eq("270375239215616000"),
            eq(LocalDate.of(2026, 1, 1)),
            eq(LocalDate.of(2026, 2, 28))))
            .thenReturn(allLogs);

        // findByLogIds：按批次返回对应日志
        when(logMapper.findByLogIds(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            return ids.stream()
                .map(id -> {
                    ChatLog log = new ChatLog();
                    log.setLogId(Long.parseLong(id));
                    log.setRawText("测试日志 " + id);
                    log.setEventDate(LocalDate.of(2026, 1, 1));
                    return log;
                })
                .collect(Collectors.toList());
        });

        // 子总结生成
        when(summaryGenerationService.generateSummary(
            anyString(), anyString(), anyInt(), anyString(), anyString()))
            .thenReturn("子总结内容");

        // 合并总结
        when(summaryGenerationService.mergeSummaries(
            anyString(), anyString(), anyInt(), anyString(), anyString()))
            .thenReturn("合并后的健康总结报告");
    }

    @Test
    void summarize_withMoreThan10000Logs_shouldCallMultiBatchSummary() {
        ChatResponse response = service.summarize(MEMORY_ID, MSG);

        assertNotNull(response);
        assertEquals(Intent.SUMMARY.name(), response.getIntent());
        assertEquals("合并后的健康总结报告", response.getReply());
    }

    @Test
    void summarize_withMoreThan10000Logs_shouldCallGenerateSummaryMultipleTimes() {
        service.summarize(MEMORY_ID, MSG);

        // 20001 条分 3 批：10000 + 10000 + 1
        int expectedBatches = (TOTAL_LOGS + BATCH_SIZE - 1) / BATCH_SIZE;
        verify(summaryGenerationService, times(expectedBatches))
            .generateSummary(anyString(), anyString(), eq(TOTAL_LOGS), anyString(), eq(MSG));
    }

    @Test
    void summarize_withMoreThan10000Logs_shouldCallMergeSummariesOnce() {
        service.summarize(MEMORY_ID, MSG);

        verify(summaryGenerationService, times(1))
            .mergeSummaries(anyString(), anyString(), eq(TOTAL_LOGS), anyString(), eq(MSG));
    }

    @Test
    void summarize_withMoreThan10000Logs_shouldCallFindByLogIdsPerBatch() {
        service.summarize(MEMORY_ID, MSG);

        int expectedBatches = (TOTAL_LOGS + BATCH_SIZE - 1) / BATCH_SIZE;
        verify(logMapper, times(expectedBatches)).findByLogIds(anyList());
    }

    @Test
    void summarize_withMoreThan10000Logs_mergeReceivesAllSubSummaries() {
        service.summarize(MEMORY_ID, MSG);

        ArgumentCaptor<String> subSummariesCaptor = ArgumentCaptor.forClass(String.class);
        verify(summaryGenerationService)
            .mergeSummaries(anyString(), anyString(), eq(TOTAL_LOGS), subSummariesCaptor.capture(), eq(MSG));

        String subSummaries = subSummariesCaptor.getValue();
        int expectedBatches = (TOTAL_LOGS + BATCH_SIZE - 1) / BATCH_SIZE;
        for (int i = 1; i <= expectedBatches; i++) {
            assertTrue(subSummaries.contains("批次" + i), "应包含批次" + i);
        }
    }
}
