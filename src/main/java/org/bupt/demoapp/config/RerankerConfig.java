package org.bupt.demoapp.config;

import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reranker 配置类
 * 
 * 使用 Cohere Rerank API 对检索结果进行重排序
 * 
 * 工作流程:
 * 1. Milvus 向量检索 (召回阶段) - Top-20
 * 2. Cohere Rerank (精排阶段) - Top-5
 * 
 * 优势:
 * - 提升检索准确性
 * - 更好的语义理解（因果、时间、否定等）
 * - 专门为 RAG 优化
 */
@Configuration
public class RerankerConfig {
    private static final Logger log = LoggerFactory.getLogger(RerankerConfig.class);

    @Value("${langchain4j.cohere.api-key:}")
    private String cohereApiKey;

    @Value("${langchain4j.cohere.rerank.model:rerank-english-v3.0}")
    private String rerankModel;

    @Value("${langchain4j.cohere.rerank.enabled:false}")
    private boolean rerankEnabled;

    /**
     * Cohere Reranker Bean
     * 
     * 如果没有配置 API Key，返回 null（使用降级方案）
     */
    @Bean
    public ScoringModel cohereReranker() {
        if (!rerankEnabled || cohereApiKey == null || cohereApiKey.isEmpty()) {
            log.warn(">>> Cohere Reranker 未启用或未配置 API Key，将使用向量检索结果");
            return null;
        }

        log.info(">>> 初始化 Cohere Reranker - model: {}", rerankModel);
        
        return CohereScoringModel.builder()
                .apiKey(cohereApiKey)
                .modelName(rerankModel)
                .build();
    }
}

