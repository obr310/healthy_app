package org.bupt.demoapp.config;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Milvus 配置类
 * 
 * 创建两个独立的 EmbeddingStore：
 * 1. userLogEmbeddingStore - 存储用户健康日志（动态、个人）
 * 2. knowledgeBaseEmbeddingStore - 存储医学知识库（静态、全局）
 */
@Configuration
public class MilvusConfig {
    private static final Logger log = LoggerFactory.getLogger(MilvusConfig.class);

    @Value("${langchain4j.milvus.host}")
    private String host;

    @Value("${langchain4j.milvus.port}")
    private Integer port;

    @Value("${langchain4j.milvus.username}")
    private String username;

    @Value("${langchain4j.milvus.password}")
    private String password;

    @Value("${langchain4j.milvus.user-log-collection-name}")
    private String userLogCollectionName;

    @Value("${langchain4j.milvus.knowledge-collection-name}")
    private String knowledgeCollectionName;

    @Value("${langchain4j.milvus.dimension}")
    private Integer dimension;

    /**
     * 用户日志 EmbeddingStore
     * 用于存储用户的健康日志向量
     */
    @Bean
    @Primary
    public MilvusEmbeddingStore userLogEmbeddingStore() {
        log.info(">>> 初始化用户日志 EmbeddingStore - collection: {}", userLogCollectionName);
        
        return MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .collectionName(userLogCollectionName)
                .dimension(dimension)
                .indexType(IndexType.AUTOINDEX)
                .metricType(MetricType.COSINE)
                .build();
    }

    /**
     * 知识库 EmbeddingStore
     * 用于存储医学知识库向量（MedlinePlus 等）
     */
    @Bean
    public MilvusEmbeddingStore knowledgeBaseEmbeddingStore() {
        log.info(">>> 初始化知识库 EmbeddingStore - collection: {}", knowledgeCollectionName);
        
        return MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .collectionName(knowledgeCollectionName)
                .dimension(dimension)
                .indexType(IndexType.AUTOINDEX)
                .metricType(MetricType.COSINE)
                .build();
    }
}

