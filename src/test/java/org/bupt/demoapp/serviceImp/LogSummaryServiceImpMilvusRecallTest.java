package org.bupt.demoapp.serviceImp;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Milvus 多次召回分批测试
 * 
 * 验证：当 hasCategory=true 且第一次召回达到 16384 条时，
 * 会触发第二次、第三次召回，并合并去重
 */
@ExtendWith(MockitoExtension.class)
class LogSummaryServiceImpMilvusRecallTest {

    private static final String MEMORY_ID = "270375239215616000:1";
    private static final String MSG = "1-2月的运动情况总结";
    private static final int MILVUS_MAX_TOP_K = 16384;

    @Mock
    private SummaryQueryAnalysisService queryAnalysisService;

    @Mock
    private SummaryGenerationService summaryGenerationService;

    @Mock
    private LogMapper logMapper;

    @Mock
    private MemoryIds memoryIds;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private MilvusEmbeddingStore milvusEmbeddingStore;

    @InjectMocks
    private LogSummaryServiceImp service;

    @BeforeEach
    void setUp() {
        when(memoryIds.extractUserId(MEMORY_ID)).thenReturn(270375239215616000L);

        // 有类别：使用 Milvus 路径
        when(queryAnalysisService.analyzeQuery(anyString(), eq(MSG)))
            .thenReturn("2026-01-01,2026-02-28,true");

        //  Fake embedding
        float[] values = new float[1024];
        java.util.Arrays.fill(values, 0.1f);
        Embedding fakeEmbedding = Embedding.from(values);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(fakeEmbedding));

        // Milvus 召回：第1次 16384 条（满）触发第2次，第2次 16384 条
        // 累计 32768 > MAX_LOGS(30000)，不会触发第3次
        List<EmbeddingSearchResult<TextSegment>> searchResults = new ArrayList<>();
        searchResults.add(createSearchResult(1, MILVUS_MAX_TOP_K));
        searchResults.add(createSearchResult(MILVUS_MAX_TOP_K + 1, MILVUS_MAX_TOP_K));

        final int[] callCount = {0};
        when(milvusEmbeddingStore.search(any(EmbeddingSearchRequest.class)))
            .thenAnswer(invocation -> searchResults.get(callCount[0]++));

        // findByLogIds
        when(logMapper.findByLogIds(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            return ids.stream()
                .map(id -> {
                    ChatLog log = new ChatLog();
                    log.setLogId(Long.parseLong(id));
                    log.setRawText("运动日志 " + id);
                    log.setEventDate(LocalDate.of(2026, 1, 15));
                    return log;
                })
                .collect(Collectors.toList());
        });

        when(summaryGenerationService.generateSummary(
            anyString(), anyString(), anyInt(), anyString(), anyString()))
            .thenReturn("运动总结");

        when(summaryGenerationService.mergeSummaries(
            anyString(), anyString(), anyInt(), anyString(), anyString()))
            .thenReturn("合并后的运动总结报告");
    }

    @SuppressWarnings("unchecked")
    private EmbeddingSearchResult<TextSegment> createSearchResult(int startId, int count) {
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String logId = String.valueOf(startId + i);
            Metadata meta = new Metadata();
            meta.put("log_id", logId);
            meta.put("user_id", "270375239215616000");
            meta.put("event_date", 1735689600000L);
            TextSegment seg = new TextSegment("运动 " + logId, meta);
            EmbeddingMatch<TextSegment> match = mock(EmbeddingMatch.class);
            when(match.embedded()).thenReturn(seg);
            matches.add(match);
        }
        return new EmbeddingSearchResult<>(matches);
    }

    @Test
    void summarize_withCategory_shouldUseMilvusAndTriggerMultiRecall() {
        ChatResponse response = service.summarize(MEMORY_ID, MSG);

        assertNotNull(response);
        assertEquals(Intent.SUMMARY.name(), response.getIntent());
        assertTrue(response.getReply().contains("合并后的运动总结报告"));

        // 调用 2 次 Milvus search（第1次满16384触发第2次，累计32768超30000后停止）
        verify(milvusEmbeddingStore, times(2)).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    void summarize_withCategory_shouldCallEmbeddingModel() {
        service.summarize(MEMORY_ID, MSG);

        verify(embeddingModel, times(1)).embed(eq(MSG));
    }

    @Test
    void summarize_withCategory_searchCalledTwiceWhenFirstBatchFull() {
        service.summarize(MEMORY_ID, MSG);

        // 第1次召回 16384 条触发第2次，累计 32768 > MAX_LOGS(30000) 后停止
        verify(milvusEmbeddingStore, times(2)).search(any(EmbeddingSearchRequest.class));
    }
}
