package org.bupt.demoapp.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

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

    @Value("${elasticsearch.scheme:http}")
    private String esScheme;

    @Value("${elasticsearch.host:localhost}")
    private String esHost;

    @Value("${elasticsearch.port:9200}")
    private Integer esPort;

    @Value("${elasticsearch.username:}")
    private String esUsername;

    @Value("${elasticsearch.password:}")
    private String esPassword;

    @Value("${elasticsearch.ca-cert-path:}")
    private String esCaCertPath;

    @Value("${langchain4j.milvus.user-log-collection-name}")
    private String userLogCollectionName;

    @Value("${langchain4j.milvus.knowledge-collection-name}")
    private String knowledgeCollectionName;

    @Value("${langchain4j.milvus.dimension}")
    private Integer dimension;

    /**
     * Elasticsearch Client Bean（用于 BM25 多路召回）
     * 支持 HTTP / HTTPS（CA 证书或跳过验证）+ 用户名密码认证
     */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        log.info(">>> 初始化 ElasticsearchClient - {}://{}:{}", esScheme, esHost, esPort);
        try {
            BasicCredentialsProvider credsProv = null;
            if (esUsername != null && !esUsername.isEmpty()) {
                credsProv = new BasicCredentialsProvider();
                credsProv.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(esUsername, esPassword));
            }
            final BasicCredentialsProvider finalCredsProv = credsProv;

            RestClientBuilder builder = RestClient.builder(new HttpHost(esHost, esPort, esScheme));

            if ("https".equalsIgnoreCase(esScheme)) {
                SSLContext sslContext;
                if (esCaCertPath != null && !esCaCertPath.isEmpty()) {
                    // 使用 CA 证书
                    try (InputStream certIs = Files.newInputStream(Paths.get(esCaCertPath))) {
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        Certificate caCert = cf.generateCertificate(certIs);
                        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                        ks.load(null, null);
                        ks.setCertificateEntry("ca", caCert);
                        sslContext = SSLContexts.custom().loadTrustMaterial(ks, null).build();
                        log.info(">>> ES 使用 CA 证书: {}", esCaCertPath);
                    }
                } else {
                    // 跳过证书验证（开发环境兜底）
                    sslContext = SSLContexts.custom()
                            .loadTrustMaterial(null, (chain, authType) -> true)
                            .build();
                    log.warn(">>> ES CA 证书路径未配置，使用信任所有证书模式（仅限开发环境）");
                }
                final SSLContext finalSslContext = sslContext;
                builder.setHttpClientConfigCallback(hc -> {
                    hc.setSSLContext(finalSslContext);
                    if (finalCredsProv != null) hc.setDefaultCredentialsProvider(finalCredsProv);
                    return hc;
                });
            } else {
                if (finalCredsProv != null) {
                    builder.setHttpClientConfigCallback(
                            hc -> hc.setDefaultCredentialsProvider(finalCredsProv));
                }
            }

            RestClient restClient = builder.build();
            ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            return new ElasticsearchClient(transport);
        } catch (Exception e) {
            log.error(">>> ElasticsearchClient 初始化失败", e);
            throw new RuntimeException("ElasticsearchClient 初始化失败", e);
        }
    }

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

